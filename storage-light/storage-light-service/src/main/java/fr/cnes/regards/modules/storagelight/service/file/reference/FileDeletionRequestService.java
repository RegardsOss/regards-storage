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

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;

import org.apache.commons.compress.utils.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

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
import fr.cnes.regards.modules.storagelight.dao.IFileDeletetionRequestRepository;
import fr.cnes.regards.modules.storagelight.domain.database.FileReference;
import fr.cnes.regards.modules.storagelight.domain.database.request.FileDeletionRequest;
import fr.cnes.regards.modules.storagelight.domain.database.request.FileRequestStatus;
import fr.cnes.regards.modules.storagelight.domain.plugin.FileDeletionWorkingSubset;
import fr.cnes.regards.modules.storagelight.domain.plugin.IStorageLocation;
import fr.cnes.regards.modules.storagelight.service.JobsPriority;
import fr.cnes.regards.modules.storagelight.service.file.reference.job.FileDeletionRequestJob;
import fr.cnes.regards.modules.storagelight.service.file.reference.job.FileStorageRequestJob;
import fr.cnes.regards.modules.storagelight.service.storage.flow.StoragePluginConfigurationHandler;

/**
 * Service to handle request to physically delete files thanks to {@link FileDeletionRequest}s.
 *
 * @author Sébastien Binda
 */
@Service
@MultitenantTransactional
public class FileDeletionRequestService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileDeletionRequestService.class);

    private static final int NB_REFERENCE_BY_PAGE = 1000;

    @Autowired
    private IFileDeletetionRequestRepository fileDeletionRequestRepo;

    @Autowired
    private IPluginService pluginService;

    @Autowired
    private IJobInfoService jobInfoService;

    @Autowired
    private IAuthenticationResolver authResolver;

    @Autowired
    private StoragePluginConfigurationHandler storageHandler;

    @Autowired
    private EntityManager em;

    @Autowired
    protected FileDeletionRequestService self;

    /**
     * Create a new {@link FileDeletionRequest}.
     * @param fileReferenceToDelete {@link FileReference} to delete
     * @param forceDelete allows to delete fileReference even if the deletion is in error.
     * @param groupId Business identifier of the deletion request
     */
    public void create(FileReference fileReferenceToDelete, boolean forceDelete, String groupId) {
        if (!fileDeletionRequestRepo.findByFileReferenceId(fileReferenceToDelete.getId()).isPresent()) {
            fileDeletionRequestRepo.save(new FileDeletionRequest(fileReferenceToDelete, forceDelete, groupId));
        }
    }

    /**
     * Update all {@link FileDeletionRequest} in error status to change status to todo.
     */
    public void retry() {
        Pageable page = PageRequest.of(0, NB_REFERENCE_BY_PAGE, Sort.by(Direction.ASC, "id"));
        Page<FileDeletionRequest> results;
        do {
            results = fileDeletionRequestRepo.findByStatus(FileRequestStatus.ERROR, page);
            for (FileDeletionRequest request : results) {
                request.setStatus(FileRequestStatus.TODO);
                request.setErrorCause(null);
                fileDeletionRequestRepo.save(request);
            }
            // Always retrieve the first page has we modify each element of the results.
            // All element are handled when result is empty.
        } while (results.hasNext());
    }

    /**
     * Schedule {@link FileDeletionRequestJob}s for all {@link FileDeletionRequest}s matching the given parameters.
     * @param status status of the {@link FileDeletionRequest}s to handle
     * @param storages of the {@link FileDeletionRequest}s to handle
     * @return {@link JobInfo}s scheduled
     */
    public Collection<JobInfo> scheduleJobs(FileRequestStatus status, Collection<String> storages) {
        LOGGER.info("... scheduling deletion jobs");
        long start = System.currentTimeMillis();
        Collection<JobInfo> jobList = Lists.newArrayList();
        Set<String> allStorages = fileDeletionRequestRepo.findStoragesByStatus(status);
        Set<String> deletionToSchedule = (storages != null) && !storages.isEmpty()
                ? allStorages.stream().filter(storages::contains).collect(Collectors.toSet())
                : allStorages;
        for (String storage : deletionToSchedule) {
            Page<FileDeletionRequest> deletionRequestPage;
            Pageable page = PageRequest.of(0, NB_REFERENCE_BY_PAGE, Direction.ASC, "id");
            do {
                deletionRequestPage = fileDeletionRequestRepo.findByStorage(storage, page);
                if (storageHandler.getConfiguredStorages().contains(storage)) {
                    jobList = scheduleDeletionJobsByStorage(storage, deletionRequestPage.getContent());
                } else {
                    handleStorageNotAvailable(deletionRequestPage.getContent());
                }
                page = deletionRequestPage.nextPageable();
            } while (deletionRequestPage.hasNext());
        }
        LOGGER.info("...{} deletion jobs scheduled in {} ms", jobList.size(), System.currentTimeMillis() - start);
        return jobList;
    }

    /**
     * Schedule {@link FileDeletionRequestJob}s for given {@link FileDeletionRequest}s and given storage location.
     * @param storage of the {@link FileDeletionRequest}s to handle
     * @param fileDeletionRequests {@link FileDeletionRequest}s to schedule
     * @return {@link JobInfo}s scheduled
     */
    private Collection<JobInfo> scheduleDeletionJobsByStorage(String storage,
            Collection<FileDeletionRequest> fileDeletionRequests) {
        Collection<JobInfo> jobInfoList = Sets.newHashSet();
        try {

            PluginConfiguration conf = pluginService.getPluginConfigurationByLabel(storage);
            IStorageLocation storagePlugin = pluginService.getPlugin(conf.getBusinessId());

            Collection<FileDeletionWorkingSubset> workingSubSets = storagePlugin
                    .prepareForDeletion(fileDeletionRequests);
            for (FileDeletionWorkingSubset ws : workingSubSets) {
                jobInfoList.add(self.scheduleJob(ws, conf.getBusinessId()));
            }
        } catch (ModuleException | NotAvailablePluginConfigurationException e) {
            this.handleStorageNotAvailable(fileDeletionRequests);
        }
        return jobInfoList;
    }

    /**
     * Schedule a {@link JobInfo} for the given {@link  FileDeletionWorkingSubset}.<br/>
     * NOTE : A new transaction is created for each call at this method. It is mandatory to avoid having too long transactions.
     * @param workingSubset
     * @param pluginConfId
     * @return {@link JobInfo} scheduled.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public JobInfo scheduleJob(FileDeletionWorkingSubset workingSubset, String pluginConfBusinessId) {
        Set<JobParameter> parameters = Sets.newHashSet();
        parameters.add(new JobParameter(FileStorageRequestJob.DATA_STORAGE_CONF_BUSINESS_ID, pluginConfBusinessId));
        parameters.add(new JobParameter(FileStorageRequestJob.WORKING_SUB_SET, workingSubset));
        workingSubset.getFileDeletionRequests().forEach(fileRefReq -> fileDeletionRequestRepo
                .updateStatus(FileRequestStatus.PENDING, fileRefReq.getId()));
        JobInfo jobInfo = jobInfoService.createAsQueued(new JobInfo(false, JobsPriority.FILE_DELETION_JOB.getPriority(),
                parameters, authResolver.getUser(), FileDeletionRequestJob.class.getName()));
        em.flush();
        em.clear();
        return jobInfo;
    }

    /**
     * Update a list of {@link FileDeletionRequest}s when the storage destination cannot be handled.
     * A storage destination cannot be handled if <ul>
     * <li> No plugin configuration of {@link IStorageLocation} exists for the storage</li>
     * <li> the plugin configuration is disabled </li>
     * </ul>
     * @param fileDeletionRequests
     */
    private void handleStorageNotAvailable(Collection<FileDeletionRequest> fileDeletionRequests) {
        fileDeletionRequests.forEach(this::handleStorageNotAvailable);
    }

    /**
     * Update a {@link FileDeletionRequest} when the storage destination cannot be handled.
     * A storage destination cannot be handled if <ul>
     * <li> No plugin configuration of {@link IStorageLocation} exists for the storage</li>
     * <li> the plugin configuration is disabled </li>
     * </ul>
     * @param fileDeletionRequest
     */
    private void handleStorageNotAvailable(FileDeletionRequest fileDeletionRequest) {
        // The storage destination is unknown, we can already set the request in error status
        fileDeletionRequest.setStatus(FileRequestStatus.ERROR);
        fileDeletionRequest.setErrorCause(String
                .format("File <%s> cannot be handle for deletion as destination storage <%s> is unknown or disabled.",
                        fileDeletionRequest.getFileReference().getMetaInfo().getFileName(),
                        fileDeletionRequest.getStorage()));
    }

    /**
     * Delete a {@link FileDeletionRequest}
     * @param fileDeletionRequest
     */
    public void delete(FileDeletionRequest fileDeletionRequest) {
        Assert.notNull(fileDeletionRequest, "File deletion request to delete cannot be null");
        Assert.notNull(fileDeletionRequest.getId(), "File deletion request to delete identifier cannot be null");
        fileDeletionRequestRepo.deleteById(fileDeletionRequest.getId());
    }

    /**
     * Update a {@link FileDeletionRequest}
     * @param fileDeletionRequest
     */
    public void updateFileDeletionRequest(FileDeletionRequest fileDeletionRequest) {
        Assert.notNull(fileDeletionRequest, "File deletion request to update cannot be null");
        Assert.notNull(fileDeletionRequest.getId(), "File deletion request to update identifier cannot be null");
        fileDeletionRequestRepo.save(fileDeletionRequest);
    }

    /**
     * Search for a specific {@link FileDeletionRequest}
     * @param fileReference to search for
     * @return {@link FileDeletionRequest} if exists
     */
    @Transactional(readOnly = true)
    public Optional<FileDeletionRequest> search(FileReference fileReference) {
        return fileDeletionRequestRepo.findByFileReferenceId(fileReference.getId());

    }

}
