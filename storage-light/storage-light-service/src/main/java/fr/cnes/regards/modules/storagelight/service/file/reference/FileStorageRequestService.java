/*
 * Copyright 2017-2019 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
 *
 * This file is part of REGARDS.
 *
 * REGARDS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * REGARDS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with REGARDS. If not, see <http://www.gnu.org/licenses/>.
 */
package fr.cnes.regards.modules.storagelight.service.file.reference;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.compress.utils.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.google.common.collect.Sets;

import fr.cnes.regards.framework.authentication.IAuthenticationResolver;
import fr.cnes.regards.framework.jpa.multitenant.transactional.MultitenantTransactional;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.jobs.domain.JobInfo;
import fr.cnes.regards.framework.modules.jobs.domain.JobParameter;
import fr.cnes.regards.framework.modules.jobs.service.IJobInfoService;
import fr.cnes.regards.framework.modules.plugins.domain.PluginConfiguration;
import fr.cnes.regards.framework.modules.plugins.service.IPluginService;
import fr.cnes.regards.framework.utils.plugins.exception.NotAvailablePluginConfigurationException;
import fr.cnes.regards.modules.storagelight.dao.IFileStorageRequestRepository;
import fr.cnes.regards.modules.storagelight.domain.FileRequestStatus;
import fr.cnes.regards.modules.storagelight.domain.database.FileLocation;
import fr.cnes.regards.modules.storagelight.domain.database.FileReference;
import fr.cnes.regards.modules.storagelight.domain.database.FileReferenceMetaInfo;
import fr.cnes.regards.modules.storagelight.domain.database.request.FileStorageRequest;
import fr.cnes.regards.modules.storagelight.domain.plugin.FileStorageWorkingSubset;
import fr.cnes.regards.modules.storagelight.domain.plugin.IStorageLocation;
import fr.cnes.regards.modules.storagelight.service.file.reference.flow.FileRefEventPublisher;
import fr.cnes.regards.modules.storagelight.service.file.reference.job.FileStorageRequestJob;
import fr.cnes.regards.modules.storagelight.service.storage.flow.StoragePluginConfigurationHandler;

/**
 * Service to handle {@link FileStorageRequest}s.
 * Those requests are created when a file reference need to be stored physically thanks to an existing {@link IStorageLocation} plugin.
 *
 * @author Sébastien Binda
 */
@Service
@MultitenantTransactional
public class FileStorageRequestService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileStorageRequestService.class);

    private static final int NB_REFERENCE_BY_PAGE = 1000;

    @Autowired
    private IPluginService pluginService;

    @Autowired
    private IFileStorageRequestRepository fileStorageRequestRepo;

    @Autowired
    private IJobInfoService jobInfoService;

    @Autowired
    private IAuthenticationResolver authResolver;

    @Autowired
    private FileRefEventPublisher fileRefEventPublisher;

    @Autowired
    private StoragePluginConfigurationHandler storageHandler;

    /**
     * Search for {@link FileStorageRequest}s matching the given destination storage and checksum
     * @param destinationStorage
     * @param checksum
     * @return {@link FileStorageRequest}
     */
    public Optional<FileStorageRequest> search(String destinationStorage, String checksum) {
        return fileStorageRequestRepo.findByMetaInfoChecksumAndDestinationStorage(checksum, destinationStorage);
    }

    /**
     * Search for all {@link FileStorageRequest}s
     * @param pageable
     * @return {@link FileStorageRequest}s by page
     */
    public Page<FileStorageRequest> search(Pageable pageable) {
        return fileStorageRequestRepo.findAll(pageable);
    }

    /**
     * Search for {@link FileStorageRequest}s associated to the given destination storage location.
     * @param pageable
     * @return {@link FileStorageRequest}s by page
     */
    public Page<FileStorageRequest> search(String destinationStorage, Pageable pageable) {
        return fileStorageRequestRepo.findByDestinationStorage(destinationStorage, pageable);
    }

    /**
     * Delete a {@link FileStorageRequest}
     * @param fileStorageRequestuest to delete
     */
    public void deleteFileReferenceRequest(FileStorageRequest fileStorageRequestuest) {
        fileStorageRequestRepo.deleteById(fileStorageRequestuest.getId());
    }

    /**
     * Update a {@link FileStorageRequest}
     * @param fileStorageRequestuest to delete
     */
    public void update(FileStorageRequest fileStorageRequestuest) {
        fileStorageRequestRepo.save(fileStorageRequestuest);
    }

    /**
     * Schedule {@link FileStorageRequestJob}s for all {@link FileStorageRequest}s matching the given parameters
     * @param status of the request to handle
     * @param storages of the request to handle
     * @param owners of the request to handle
     * @return {@link JobInfo}s scheduled
     */
    public Collection<JobInfo> scheduleStoreJobs(FileRequestStatus status, Collection<String> storages,
            Collection<String> owners) {
        Collection<JobInfo> jobList = Lists.newArrayList();
        Set<String> allStorages = fileStorageRequestRepo.findDestinationStoragesByStatus(status);
        Set<String> storagesToSchedule = (storages != null) && !storages.isEmpty()
                ? allStorages.stream().filter(storages::contains).collect(Collectors.toSet())
                : allStorages;
        for (String storage : storagesToSchedule) {
            Page<FileStorageRequest> filesPage;
            Pageable page = PageRequest.of(0, NB_REFERENCE_BY_PAGE);
            do {
                if ((owners != null) && !owners.isEmpty()) {
                    filesPage = fileStorageRequestRepo.findAllByDestinationStorageAndOwnersIn(storage, owners, page);
                } else {
                    filesPage = fileStorageRequestRepo.findAllByDestinationStorage(storage, page);
                }
                List<FileStorageRequest> fileStorageRequests = filesPage.getContent();

                if (storageHandler.getConfiguredStorages().contains(storage)) {
                    jobList = this.scheduleStoreJobsByStorage(storage, fileStorageRequests);
                } else {
                    this.handleStorageNotAvailable(fileStorageRequests);
                }
                page = filesPage.nextPageable();
            } while (filesPage.hasNext());
        }
        return jobList;
    }

    /**
     * Schedule {@link FileStorageRequestJob}s for all given {@link FileStorageRequest}s and a given storage location.
     * @param storage
     * @param fileStorageRequests
     * @return {@link JobInfo}s scheduled
     */
    private Collection<JobInfo> scheduleStoreJobsByStorage(String storage,
            Collection<FileStorageRequest> fileStorageRequests) {
        Collection<JobInfo> jobInfoList = Sets.newHashSet();
        try {
            PluginConfiguration conf = pluginService.getPluginConfigurationByLabel(storage);
            IStorageLocation storagePlugin = pluginService.getPlugin(conf.getId());
            Collection<FileStorageWorkingSubset> workingSubSets = storagePlugin
                    .prepareForStorage(fileStorageRequests);
            workingSubSets.forEach(ws -> {
                Set<JobParameter> parameters = Sets.newHashSet();
                parameters.add(new JobParameter(FileStorageRequestJob.DATA_STORAGE_CONF_ID, conf.getId()));
                parameters.add(new JobParameter(FileStorageRequestJob.WORKING_SUB_SET, ws));
                ws.getFileReferenceRequests().forEach(fileStorageRequest -> fileStorageRequestRepo
                        .updateStatus(FileRequestStatus.PENDING, fileStorageRequest.getId()));
                jobInfoList.add(jobInfoService.createAsQueued(new JobInfo(false, 0, parameters, authResolver.getUser(),
                        FileStorageRequestJob.class.getName())));
            });
        } catch (ModuleException | NotAvailablePluginConfigurationException e) {
            this.handleStorageNotAvailable(fileStorageRequests);
        }
        return jobInfoList;
    }

    /**
     * Create a new {@link FileStorageRequest}
     * @param owners owners of the file to reference
     * @param fileMetaInfo meta information of the file to reference
     * @param origin file origin location
     * @param destination file destination location (where the file will be stored).
     */
    public void createNewFileReferenceRequest(Collection<String> owners, FileReferenceMetaInfo fileMetaInfo,
            FileLocation origin, FileLocation destination) {
        this.addFileReferenceRequest(owners, fileMetaInfo, origin, destination, FileRequestStatus.TODO);
    }

    public void addFileReferenceRequest(Collection<String> owners, FileReferenceMetaInfo fileMetaInfo,
            FileLocation origin, FileLocation destination, FileRequestStatus status) {
        // Check if file reference request already exists
        Optional<FileStorageRequest> oFileRefRequest = search(destination.getStorage(), fileMetaInfo.getChecksum());
        if (oFileRefRequest.isPresent()) {
            handleFileReferenceRequestAlreadyExists(oFileRefRequest.get(), fileMetaInfo, owners);
        } else {
            newFileReferenceRequest(owners, fileMetaInfo, origin, destination, status);
        }
    }

    /**
     * Create a new {@link FileStorageRequest}
     * @param owners owners of the file to reference
     * @param fileMetaInfo meta information of the file to reference
     * @param origin file origin location
     * @param destination file destination location (where the file will be stored).
     * @param status status of the file request to create.
     */
    private void newFileReferenceRequest(Collection<String> owners, FileReferenceMetaInfo fileMetaInfo,
            FileLocation origin, FileLocation destination, FileRequestStatus status) {
        FileStorageRequest fileStorageRequest = new FileStorageRequest(owners, fileMetaInfo, origin, destination);
        fileStorageRequest.setStatus(status);
        if (!storageHandler.getConfiguredStorages().contains(destination.getStorage())) {
            // The storage destination is unknown, we can already set the request in error status
            String message = String
                    .format("File <%s> cannot be handle for storage as destination storage <%s> is unknown. Known storages are %s",
                            fileMetaInfo.getFileName(), destination.getStorage(),
                            Arrays.toString(storageHandler.getConfiguredStorages().toArray()));
            fileStorageRequest.setStatus(FileRequestStatus.ERROR);
            fileStorageRequest.setErrorCause(message);
            LOGGER.error(message);
            fileRefEventPublisher.publishFileRefStoreError(fileMetaInfo.getChecksum(), owners, destination, message);
        } else {
            LOGGER.debug("New file reference request created for file <{}> to store to {} with status {}",
                         fileStorageRequest.getMetaInfo().getFileName(), fileStorageRequest.getDestination().toString(),
                         fileStorageRequest.getStatus());
        }
        fileStorageRequestRepo.save(fileStorageRequest);
    }

    /**
     * Method to update a {@link FileStorageRequest} when a new request is sent for the same associated {@link FileReference}.<br/>
     * If the existing file request is in error state, update the state to todo to allow store request retry.<br/>
     * The existing request is also updated to add new owners of the future stored and referenced {@link FileReference}.
     * @param fileStorageRequest
     * @param newMetaInfo
     * @param owners
     */
    public void handleFileReferenceRequestAlreadyExists(FileStorageRequest fileStorageRequest,
            FileReferenceMetaInfo newMetaInfo, Collection<String> owners) {
        for (String owner : owners) {
            if (!fileStorageRequest.getOwners().contains(owner)) {
                fileStorageRequest.getOwners().add(owner);
                if (newMetaInfo.equals(fileStorageRequest.getMetaInfo())) {
                    LOGGER.warn("Existing referenced file meta information differs "
                            + "from new reference meta information. Previous ones are maintained");
                }
            }
        }
        switch (fileStorageRequest.getStatus()) {
            case ERROR:
                // Allows storage retry.
                fileStorageRequest.setStatus(FileRequestStatus.TODO);
                break;
            case PENDING:
                // A storage is already in progress for this request.
            case DELAYED:
            case TODO:
            default:
                // Request has not been handled yet, we can update it.
                break;
        }
        fileStorageRequestRepo.save(fileStorageRequest);
    }

    /**
     * Update a list of {@link FileStorageRequest}s when the storage destination cannot be handled.
     * A storage destination cannot be handled if <ul>
     * <li> No plugin configuration of {@link IStorageLocation} exists for the storage</li>
     * <li> the plugin configuration is disabled </li>
     * </ul>
     * @param fileStorageRequests
     */
    private void handleStorageNotAvailable(Collection<FileStorageRequest> fileStorageRequests) {
        fileStorageRequests.forEach(this::handleStorageNotAvailable);
    }

    /**
     * Update a {@link FileStorageRequest} when the storage destination cannot be handled.
     * A storage destination cannot be handled if <ul>
     * <li> No plugin configuration of {@link IStorageLocation} exists for the storage</li>
     * <li> the plugin configuration is disabled </li>
     * </ul>
     * @param fileStorageRequestuest
     */
    private void handleStorageNotAvailable(FileStorageRequest fileStorageRequest) {
        // The storage destination is unknown, we can already set the request in error status
        fileStorageRequest.setStatus(FileRequestStatus.ERROR);
        fileStorageRequest.setErrorCause(String
                .format("File <%s> cannot be handle for storage as destination storage <%s> is unknown or disabled.",
                        fileStorageRequest.getMetaInfo().getFileName(),
                        fileStorageRequest.getDestination().getStorage()));
        update(fileStorageRequest);
    }

}
