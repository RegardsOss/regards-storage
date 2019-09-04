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
package fr.cnes.regards.modules.storagelight.service.file.request;

import java.net.URL;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
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
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimaps;
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
import fr.cnes.regards.modules.storagelight.dao.IFileCacheRequestRepository;
import fr.cnes.regards.modules.storagelight.domain.database.CacheFile;
import fr.cnes.regards.modules.storagelight.domain.database.FileReference;
import fr.cnes.regards.modules.storagelight.domain.database.PrioritizedStorage;
import fr.cnes.regards.modules.storagelight.domain.database.request.FileCacheRequest;
import fr.cnes.regards.modules.storagelight.domain.database.request.FileRequestStatus;
import fr.cnes.regards.modules.storagelight.domain.event.FileReferenceEvent;
import fr.cnes.regards.modules.storagelight.domain.event.FileRequestType;
import fr.cnes.regards.modules.storagelight.domain.plugin.FileRestorationWorkingSubset;
import fr.cnes.regards.modules.storagelight.domain.plugin.INearlineStorageLocation;
import fr.cnes.regards.modules.storagelight.domain.plugin.IStorageLocation;
import fr.cnes.regards.modules.storagelight.service.JobsPriority;
import fr.cnes.regards.modules.storagelight.service.cache.CacheService;
import fr.cnes.regards.modules.storagelight.service.file.FileReferenceEventPublisher;
import fr.cnes.regards.modules.storagelight.service.file.FileReferenceService;
import fr.cnes.regards.modules.storagelight.service.file.job.FileCacheRequestJob;
import fr.cnes.regards.modules.storagelight.service.location.PrioritizedStorageService;
import fr.cnes.regards.modules.storagelight.service.location.StoragePluginConfigurationHandler;

/**
 * Service to handle {@link FileCacheRequest}s.
 * Those requests are created when a file reference need to be restored physically thanks to an existing {@link INearlineStorageLocation} plugin.
 *
 * @author Sébastien Binda
 */
@Service
@MultitenantTransactional
public class FileCacheRequestService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileCacheRequestService.class);

    private static final int NB_REFERENCE_BY_PAGE = 500;

    @Autowired
    private IFileCacheRequestRepository repository;

    @Autowired
    private IPluginService pluginService;

    @Autowired
    private IJobInfoService jobInfoService;

    @Autowired
    private IAuthenticationResolver authResolver;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private StoragePluginConfigurationHandler storageHandler;

    @Autowired
    private EntityManager em;

    @Autowired
    protected FileCacheRequestService self;

    @Autowired
    private FileReferenceEventPublisher publisher;

    @Autowired
    private RequestsGroupService reqGrpService;

    @Autowired
    private FileReferenceService fileRefService;

    @Autowired
    private PrioritizedStorageService pStorageService;

    @Transactional(readOnly = true)
    public Optional<FileCacheRequest> search(String checksum) {
        return repository.findByChecksum(checksum);
    }

    /**
     * Creates a new {@link FileCacheRequest} if does not exists already.
     * @param fileRefToRestore
     * @param expirationDate
     * @param groupId Business identifier of the deletion request
     * @return {@link FileCacheRequest} created.
     */
    public Optional<FileCacheRequest> create(FileReference fileRefToRestore, OffsetDateTime expirationDate,
            String groupId) {
        String checksum = fileRefToRestore.getMetaInfo().getChecksum();
        Optional<FileCacheRequest> oFcr = repository.findByChecksum(checksum);
        FileCacheRequest request = null;
        if (!oFcr.isPresent()) {
            request = new FileCacheRequest(fileRefToRestore, cacheService.getCacheDirectoryPath(checksum),
                    expirationDate, groupId);
            request = repository.save(request);
            LOGGER.debug("File {} (checksum {}) is requested for cache.", fileRefToRestore.getMetaInfo().getFileName(),
                         fileRefToRestore.getMetaInfo().getChecksum());
        } else {
            request = oFcr.get();
            if (request.getStatus() == FileRequestStatus.ERROR) {
                request.setStatus(FileRequestStatus.TODO);
                request = repository.save(request);
            }
            LOGGER.debug("File {} (checksum {}) is already requested for cache.",
                         fileRefToRestore.getMetaInfo().getFileName(), fileRefToRestore.getMetaInfo().getChecksum());
        }
        return Optional.ofNullable(request);
    }

    /**
     * Ensure availability of given files by their checksum for download.
     * @param checksums
     * @param expirationDate availability expiration date.
     * @param groupId
     */
    public void makeAvailable(Collection<String> checksums, OffsetDateTime expirationDate, String groupId) {

        Set<FileReference> onlines = Sets.newHashSet();
        Set<FileReference> offlines = Sets.newHashSet();
        Set<FileReference> nearlines = Sets.newHashSet();
        Set<FileReference> refs = fileRefService.search(checksums);
        Set<String> remainingChecksums = Sets.newHashSet(checksums);

        // Dispatch by storage
        ImmutableListMultimap<String, FileReference> filesByStorage = Multimaps
                .index(refs, f -> f.getLocation().getStorage());
        Set<String> remainingStorages = Sets.newHashSet(filesByStorage.keySet());

        Optional<PrioritizedStorage> storage = pStorageService.searchActiveHigherPriority(remainingStorages);
        // Handle storage by priority
        while (storage.isPresent() && !remainingStorages.isEmpty() && !remainingChecksums.isEmpty()) {
            // For each storage dispatch files in online, near line and not available
            PluginConfiguration conf = storage.get().getStorageConfiguration();
            String storageName = conf.getLabel();
            ImmutableList<FileReference> storageFiles = filesByStorage.get(storageName);
            // Calculate remaining files to make available for the current storage
            Set<FileReference> files = storageFiles.stream()
                    .filter(f -> remainingChecksums.contains(f.getMetaInfo().getChecksum()))
                    .collect(Collectors.toSet());
            switch (storage.get().getStorageType()) {
                case NEARLINE:
                    nearlines.addAll(files);
                    break;
                case ONLINE:
                    // If storage is an online one, so file is already available
                    onlines.addAll(files);
                    break;
                default:
                    // Unknown storage type
                    offlines.addAll(files);
                    break;
            }
            // Remove handled checksum by the current storage
            remainingChecksums.removeAll(storageFiles.stream().map(f -> f.getMetaInfo().getChecksum())
                    .collect(Collectors.toSet()));
            // Remove handled storage
            remainingStorages.remove(storageName);
            // Retrieve the new highest storage priority with the remaining ones
            storage = pStorageService.searchActiveHigherPriority(remainingStorages);
        }
        if (!remainingChecksums.isEmpty()) {
            // add unknown to offline files
            offlines.addAll(refs.stream().filter(ref -> remainingChecksums.contains(ref.getMetaInfo().getChecksum()))
                    .collect(Collectors.toSet()));
        }
        notifyAvailables(onlines, groupId);
        notifyNotAvailables(offlines, groupId);
        // Hack to handle offline errors as request errors to be notified in request ERROR notification.
        nearlines.addAll(offlines);
        int nbRequests = makeAvailable(nearlines, expirationDate, groupId);
        // If no cache requests are needed, we have to notify the end of the availability request
        if (nearlines.isEmpty() || (nbRequests == 0)) {
            reqGrpService.done(groupId, FileRequestType.AVAILABILITY);
        }
    }

    /**
     * Update all {@link FileCacheRequest} in error status to change status to todo.
     * @param groupId request business identifier to retry
     */
    public void retryRequest(String groupId) {
        for (FileCacheRequest request : repository.findByGroupIdAndStatus(groupId, FileRequestStatus.ERROR)) {
            request.setStatus(FileRequestStatus.TODO);
            request.setErrorCause(null);
            repository.save(request);
        }
    }

    /**
     * Schedule all {@link FileCacheRequest}s with given status to be handled in {@link JobInfo}s
     * @param status
     * @return scheduled {@link JobInfo}s
     */
    public Collection<JobInfo> scheduleJobs(FileRequestStatus status) {
        LOGGER.info("... scheduling cache jobs");
        long start = System.currentTimeMillis();
        Collection<JobInfo> jobList = Lists.newArrayList();
        Set<String> allStorages = repository.findStoragesByStatus(status);
        for (String storage : allStorages) {
            Page<FileCacheRequest> filesPage;
            Pageable page = PageRequest.of(0, NB_REFERENCE_BY_PAGE, Direction.ASC, "id");
            do {
                filesPage = repository.findAllByStorageAndStatus(storage, status, page);
                List<FileCacheRequest> requests = filesPage.getContent();
                if (storageHandler.getConfiguredStorages().contains(storage)) {
                    requests = calculateRestorables(requests);
                    jobList = scheduleJobsByStorage(storage, requests);
                } else {
                    this.handleStorageNotAvailable(requests);
                }
                page = filesPage.nextPageable();
            } while (filesPage.hasNext());
        }
        LOGGER.info("...{} cache jobs scheduled in {} ms", jobList.size(), System.currentTimeMillis() - start);
        return jobList;
    }

    /**
     * Handle a {@link FileCacheRequest} end with success.<ul>
     *  <li> Creates the new {@link CacheFile}</li>
     *  <li> Deletes the {@link FileCacheRequest} handled.
     *  </ul>
     * @param fileReq
     * @param cacheLocation
     * @param realFileSize
     */
    public void handleSuccess(FileCacheRequest fileReq, URL cacheLocation, Collection<String> owners, Long realFileSize,
            String successMessage) {
        Optional<FileCacheRequest> oRequest = repository.findById(fileReq.getId());
        if (oRequest.isPresent()) {
            // Create the cache file associated
            cacheService.addFile(oRequest.get().getChecksum(), realFileSize, cacheLocation,
                                 oRequest.get().getExpirationDate(), fileReq.getGroupId());
            repository.deleteById(oRequest.get().getId());
        }
        publisher.available(fileReq.getChecksum(), "cache", cacheLocation.toString(), owners, successMessage,
                            fileReq.getGroupId());
        // Inform group that a request is done
        reqGrpService.requestSuccess(fileReq.getGroupId(), FileRequestType.AVAILABILITY, fileReq.getChecksum(), null,
                                     null);
    }

    /**
     * Handle a {@link FileCacheRequest} end with error.
     * @param fileReq
     * @param cause
     */
    public void handleError(FileCacheRequest fileReq, String cause) {
        Optional<FileCacheRequest> oRequest = repository.findById(fileReq.getId());
        if (oRequest.isPresent()) {
            FileCacheRequest request = oRequest.get();
            request.setStatus(FileRequestStatus.ERROR);
            request.setErrorCause(cause);
            repository.save(request);
        }
        publisher.notAvailable(fileReq.getChecksum(), cause, fileReq.getGroupId());
        reqGrpService.requestError(fileReq.getGroupId(), FileRequestType.AVAILABILITY, fileReq.getChecksum(),
                                   fileReq.getStorage(), cause);
    }

    /**
     * Return all the request that can be restored in cache to not reach the cache size limit.
     * @param requests
     * @return available {@link FileCacheRequest} requests for restoration in cache
     */
    private List<FileCacheRequest> calculateRestorables(Collection<FileCacheRequest> requests) {
        List<FileCacheRequest> restorables = Lists.newArrayList();
        // Calculate cache size available by adding cache file sizes sum and already queued requests
        Long availableCacheSize = cacheService.getFreeSpaceInBytes();
        Long pendingSize = repository.getPendingFileSize();
        Long availableSize = availableCacheSize - pendingSize;
        Iterator<FileCacheRequest> it = requests.iterator();
        Long totalSize = 0L;
        while (it.hasNext()) {
            FileCacheRequest request = it.next();
            if ((totalSize + request.getFileSize()) <= availableSize) {
                restorables.add(request);
                totalSize += request.getFileSize();
            }
        }
        return restorables;
    }

    /**
     * Schedule all {@link FileCacheRequest}s to be handled in {@link JobInfo}s for the given storage location.
     * @param storage
     * @param requests
     * @return {@link JobInfo}s scheduled
     */
    private Collection<JobInfo> scheduleJobsByStorage(String storage, List<FileCacheRequest> requests) {
        Collection<JobInfo> jobInfoList = Sets.newHashSet();
        if ((requests != null) && !requests.isEmpty()) {
            try {
                PluginConfiguration conf = pluginService.getPluginConfigurationByLabel(storage);
                IStorageLocation storagePlugin = pluginService.getPlugin(conf.getBusinessId());
                Collection<FileRestorationWorkingSubset> workingSubSets = storagePlugin.prepareForRestoration(requests);
                for (FileRestorationWorkingSubset ws : workingSubSets) {
                    jobInfoList.add(self.scheduleJob(ws, conf.getBusinessId()));
                }
            } catch (ModuleException | NotAvailablePluginConfigurationException e) {
                this.handleStorageNotAvailable(requests);
            }
        }
        return jobInfoList;
    }

    /**
     * Schedule a {@link JobInfo} for the given {@link  FileRestorationWorkingSubset}.<br/>
     * NOTE : A new transaction is created for each call at this method. It is mandatory to avoid having too long transactions.
     * @param workingSubset
     * @param pluginConfId
     * @return {@link JobInfo} scheduled.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public JobInfo scheduleJob(FileRestorationWorkingSubset workingSubset, String plgBusinessId) {
        Set<JobParameter> parameters = Sets.newHashSet();
        parameters.add(new JobParameter(FileCacheRequestJob.DATA_STORAGE_CONF_BUSINESS_ID, plgBusinessId));
        parameters.add(new JobParameter(FileCacheRequestJob.WORKING_SUB_SET, workingSubset));
        workingSubset.getFileRestorationRequests()
                .forEach(r -> repository.updateStatus(FileRequestStatus.PENDING, r.getId()));
        JobInfo jobInfo = jobInfoService.createAsQueued(new JobInfo(false, JobsPriority.FILE_CACHE_JOB.getPriority(),
                parameters, authResolver.getUser(), FileCacheRequestJob.class.getName()));
        em.flush();
        em.clear();
        return jobInfo;
    }

    /**
     * Creates {@link FileCacheRequest} for each nearline {@link FileReference} to be available for download.
     * After copy in cache, files will be available until the given expiration date.
     * @param fileReferences
     * @param expirationDate
     * @param groupId
     * @return number of cache request created.
     */
    public int makeAvailable(Set<FileReference> fileReferences, OffsetDateTime expirationDate, String groupId) {
        // Check files already available in cache
        Set<FileReference> availables = cacheService.getFilesAvailableInCache(fileReferences, groupId);
        Set<FileReference> toRestore = fileReferences.stream().filter(f -> !availables.contains(f))
                .collect(Collectors.toSet());
        // Notify available
        notifyAlreadyAvailablesInCache(availables, groupId);
        // Create a restoration request for all to restore
        for (FileReference f : toRestore) {
            create(f, expirationDate, groupId);
        }
        return toRestore.size();
    }

    /**
     * Update a list of {@link FileCacheRequest}s when the storage origin cannot be handled.
     * A storage origin cannot be handled if <ul>
     * <li> No plugin configuration of {@link IStorageLocation} exists for the storage</li>
     * <li> the plugin configuration is disabled </li>
     * </ul>
     * @param fileRefRequests
     */
    private void handleStorageNotAvailable(Collection<FileCacheRequest> fileRefRequests) {
        fileRefRequests.forEach(this::handleStorageNotAvailable);
    }

    /**
     * Update a {@link FileCacheRequest} when the storage origin cannot be handled.
     * A storage origin cannot be handled if <ul>
     * <li> No plugin configuration of {@link IStorageLocation} exists for the storage</li>
     * <li> the plugin configuration is disabled </li>
     * </ul>
     * @param fileRefRequest
     */
    private void handleStorageNotAvailable(FileCacheRequest request) {
        // The storage destination is unknown, we can already set the request in error status
        String message = String
                .format("File <%s> cannot be handle for restoration as origin storage <%s> is unknown or disabled.",
                        request.getFileReference().getMetaInfo().getFileName(), request.getStorage());
        request.setStatus(FileRequestStatus.ERROR);
        request.setErrorCause(message);
        repository.save(request);
        LOGGER.error("[CACHE ERROR] File {} is not available. Cause : {}",
                     request.getFileReference().getMetaInfo().getChecksum(), request.getErrorCause());
        publisher.notAvailable(request.getChecksum(), request.getErrorCause(), request.getGroupId());
        reqGrpService.requestError(request.getGroupId(), FileRequestType.AVAILABILITY, request.getChecksum(),
                                   request.getStorage(), message);
    }

    /**
     * Send {@link FileReferenceEvent} for available given files.
     * @param availables newly available files
     * @param availabilityGroupId business request identifier of the availability request associated.
     */
    private void notifyAvailables(Set<FileReference> availables, String availabilityGroupId) {
        for (FileReference fileRef : availables) {
            String checksum = fileRef.getMetaInfo().getChecksum();
            String storage = fileRef.getLocation().getStorage();
            String message = String.format("file %s (checksum %s) is available for download.",
                                           fileRef.getMetaInfo().getFileName(), checksum);
            publisher.available(checksum, storage, fileRef.getLocation().getUrl(), fileRef.getOwners(), message,
                                availabilityGroupId);
            reqGrpService.requestSuccess(availabilityGroupId, FileRequestType.AVAILABILITY, checksum, storage, fileRef,
                                         false);
        }
    }

    /**
     * Send {@link FileReferenceEvent} for not available given files.
     * @param availables newly available files
     * @param availabilityGroupId business request identifier of the availability request associated.
     */
    private void notifyNotAvailables(Set<FileReference> notAvailables, String availabilityGroupId) {
        for (FileReference fileRef : notAvailables) {
            String checksum = fileRef.getMetaInfo().getChecksum();
            String storage = fileRef.getLocation().getStorage();
            String message = String.format("file %s (checksum %s) is not available for download.",
                                           fileRef.getMetaInfo().getFileName(), checksum);
            publisher.notAvailable(fileRef.getMetaInfo().getChecksum(), message, availabilityGroupId);
            reqGrpService.requestError(availabilityGroupId, FileRequestType.AVAILABILITY, checksum, storage, message,
                                       false);
        }
    }

    /**
     * Notify all files as AVAILABLE.
     * @param availables
     * @param groupId
     */
    private void notifyAlreadyAvailablesInCache(Set<FileReference> availables, String groupId) {
        for (FileReference fileRef : availables) {
            String checksum = fileRef.getMetaInfo().getChecksum();
            String storage = fileRef.getLocation().getStorage();
            String message = String.format("File %s (checksum %s) is available for download.",
                                           fileRef.getMetaInfo().getFileName(), checksum);
            publisher.available(checksum, "cache", cacheService.getFilePath(checksum), fileRef.getOwners(), message,
                                groupId);
            reqGrpService.requestSuccess(groupId, FileRequestType.AVAILABILITY, checksum, storage, fileRef);
        }
    }
}
