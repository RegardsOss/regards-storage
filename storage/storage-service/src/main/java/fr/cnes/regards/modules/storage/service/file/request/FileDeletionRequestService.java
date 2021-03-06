/*
 * Copyright 2017-2020 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
package fr.cnes.regards.modules.storage.service.file.request;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.compress.utils.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
import fr.cnes.regards.framework.modules.jobs.domain.JobStatus;
import fr.cnes.regards.framework.modules.jobs.service.IJobInfoService;
import fr.cnes.regards.framework.modules.locks.service.ILockService;
import fr.cnes.regards.framework.modules.plugins.domain.PluginConfiguration;
import fr.cnes.regards.framework.modules.plugins.service.IPluginService;
import fr.cnes.regards.framework.utils.plugins.exception.NotAvailablePluginConfigurationException;
import fr.cnes.regards.modules.storage.dao.IFileDeletetionRequestRepository;
import fr.cnes.regards.modules.storage.dao.IFileReferenceRepository;
import fr.cnes.regards.modules.storage.domain.database.FileReference;
import fr.cnes.regards.modules.storage.domain.database.request.FileDeletionRequest;
import fr.cnes.regards.modules.storage.domain.database.request.FileRequestStatus;
import fr.cnes.regards.modules.storage.domain.dto.request.FileDeletionRequestDTO;
import fr.cnes.regards.modules.storage.domain.event.FileRequestType;
import fr.cnes.regards.modules.storage.domain.flow.DeletionFlowItem;
import fr.cnes.regards.modules.storage.domain.plugin.FileDeletionWorkingSubset;
import fr.cnes.regards.modules.storage.domain.plugin.IStorageLocation;
import fr.cnes.regards.modules.storage.domain.plugin.PreparationResponse;
import fr.cnes.regards.modules.storage.service.JobsPriority;
import fr.cnes.regards.modules.storage.service.file.FileReferenceEventPublisher;
import fr.cnes.regards.modules.storage.service.file.FileReferenceService;
import fr.cnes.regards.modules.storage.service.file.job.FileDeletionRequestJob;
import fr.cnes.regards.modules.storage.service.file.job.FileDeletionRequestsCreatorJob;
import fr.cnes.regards.modules.storage.service.file.job.FileStorageRequestJob;
import fr.cnes.regards.modules.storage.service.location.StoragePluginConfigurationHandler;

/**
 * Service to handle request to physically delete files thanks to {@link FileDeletionRequest}s.
 *
 * @author Sébastien Binda
 */
@Service
@MultitenantTransactional
public class FileDeletionRequestService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileDeletionRequestService.class);

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
    protected FileDeletionRequestService self;

    @Autowired
    private FileReferenceEventPublisher publisher;

    @Autowired
    private FileReferenceService fileRefService;

    @Autowired
    private RequestsGroupService reqGroupService;

    @Autowired
    private RequestStatusService reqStatusService;

    @Autowired
    private FileCopyRequestService fileCopyReqService;

    @Autowired
    private FileCacheRequestService fileCacheReqService;

    @Autowired
    private ILockService lockService;

    @Value("${regards.storage.deletion.requests.days.before.expiration:5}")
    private Integer nbDaysBeforeExpiration;

    @Value("${regards.storage.deletion.requests.per.job:100}")
    private Integer nbRequestsPerJob;

    /**
     * Create a new {@link FileDeletionRequest}.
     * @param fileReferenceToDelete {@link FileReference} to delete
     * @param forceDelete allows to delete fileReference even if the deletion is in error.
     * @param groupId Business identifier of the deletion request
     */
    public FileDeletionRequest create(FileReference fileReferenceToDelete, boolean forceDelete, String groupId,
            Collection<FileDeletionRequest> existingRequests, FileRequestStatus status) {
        Optional<FileDeletionRequest> existingOne = existingRequests.stream()
                .filter(r -> r.getFileReference().getId().equals(fileReferenceToDelete.getId())).findFirst();
        FileDeletionRequest request;
        if (!existingOne.isPresent()) {
            // Create new deletion request
            FileDeletionRequest newDelRequest = new FileDeletionRequest(fileReferenceToDelete, forceDelete, groupId,
                    status);
            newDelRequest.setStatus(reqStatusService.getNewStatus(newDelRequest, Optional.of(status)));
            request = fileDeletionRequestRepo.save(newDelRequest);
            existingRequests.add(request);
        } else {
            // Retry deletion if error
            request = retry(existingOne.get(), forceDelete);
        }
        return request;
    }

    /**
     * Update all {@link FileDeletionRequest} in error status to change status to {@link FileRequestStatus#TO_DO}.
     */
    private FileDeletionRequest retry(FileDeletionRequest request, boolean forceDelete) {
        if (request.getStatus() == FileRequestStatus.ERROR) {
            request.setStatus(FileRequestStatus.TO_DO);
            request.setErrorCause(null);
            request.setForceDelete(forceDelete);
            return updateFileDeletionRequest(request);
        } else {
            return request;
        }
    }

    /**
     * Schedule {@link FileDeletionRequestJob}s for all {@link FileDeletionRequest}s matching the given parameters.
     * @param status status of the {@link FileDeletionRequest}s to handle
     * @param storages of the {@link FileDeletionRequest}s to handle
     * @return {@link JobInfo}s scheduled
     */
    public Collection<JobInfo> scheduleJobs(FileRequestStatus status, Collection<String> storages) {
        Collection<JobInfo> jobList = Lists.newArrayList();
        if (!lockDeletionProcess(false, 30)) {
            LOGGER.info("[DELETION REQUESTS] Deletion process is delayed. A deletion process is already running.");
            return jobList;
        }
        try {
            LOGGER.trace("[DELETION REQUESTS] Scheduling deletion jobs ...");
            long start = System.currentTimeMillis();
            Set<String> allStorages = fileDeletionRequestRepo.findStoragesByStatus(status);
            Set<String> deletionToSchedule = (storages != null) && !storages.isEmpty()
                    ? allStorages.stream().filter(storages::contains).collect(Collectors.toSet())
                    : allStorages;
            int loop = 0;
            for (String storage : deletionToSchedule) {
                Page<FileDeletionRequest> deletionRequestPage;
                Long maxId = 0L;
                // Always search the first page of requests until there is no requests anymore.
                // To do so, we order on id to ensure to not handle same requests multiple times.
                Pageable page = PageRequest.of(0, nbRequestsPerJob, Direction.ASC, "id");
                do {
                    deletionRequestPage = fileDeletionRequestRepo
                            .findByStorageAndStatusAndIdGreaterThan(storage, status, maxId, page);
                    if (deletionRequestPage.hasContent()) {
                        maxId = deletionRequestPage.stream().max(Comparator.comparing(FileDeletionRequest::getId)).get()
                                .getId();
                        jobList.addAll(self.scheduleDeletionJobsByStorage(storage, deletionRequestPage));
                    }
                    loop++;
                } while (deletionRequestPage.hasContent() && (loop < 10));
            }
            LOGGER.debug("[DELETION REQUESTS] {} jobs scheduled in {} ms", jobList.size(),
                         System.currentTimeMillis() - start);
            return jobList;
        } finally {
            releaseLock();
        }
    }

    /**
     * Schedule jobs for deletion requests by using a new transaction
     * @param jobList
     * @param storage
     * @param deletionRequestPage
     * @return scheduled {@link JobInfo}
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Collection<JobInfo> scheduleDeletionJobsByStorage(String storage,
            Page<FileDeletionRequest> deletionRequestPage) {
        LOGGER.debug("[DELETION REQUESTS] scheduling {} deletion jobs for storage {} ... ", deletionRequestPage.get(),
                     storage);
        if (storageHandler.isConfigured(storage)) {
            return scheduleDeletionJobsByStorage(storage, deletionRequestPage.getContent());
        } else {
            handleStorageNotAvailable(deletionRequestPage.getContent(), Optional.empty());
        }
        return Collections.emptyList();
    }

    /**
     * Inform if for the given storage a deletion process is running
     * @param storage
     * @return boolean
     */
    public boolean isDeletionRunning(String storage) {
        boolean isRunning = false;
        // Does a deletion job exists ?
        isRunning = jobInfoService.retrieveJobsCount(FileDeletionRequestsCreatorJob.class.getName(), JobStatus.PENDING,
                                                     JobStatus.QUEUED, JobStatus.RUNNING, JobStatus.TO_BE_RUN) > 0;
        if (!isRunning) {
            isRunning = fileDeletionRequestRepo
                    .existsByStorageAndStatusIn(storage,
                                                Sets.newHashSet(FileRequestStatus.TO_DO, FileRequestStatus.PENDING));
        }
        return isRunning;
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

            PreparationResponse<FileDeletionWorkingSubset, FileDeletionRequest> response = storagePlugin
                    .prepareForDeletion(fileDeletionRequests);
            for (FileDeletionWorkingSubset ws : response.getWorkingSubsets()) {
                jobInfoList.add(scheduleJob(ws, conf.getBusinessId()));
            }
            // Handle error requests
            for (Entry<FileDeletionRequest, String> error : response.getPreparationErrors().entrySet()) {
                handleStorageNotAvailable(error.getKey(), Optional.ofNullable(error.getValue()));
            }
        } catch (ModuleException | NotAvailablePluginConfigurationException e) {
            LOGGER.error(e.getMessage(), e);
            handleStorageNotAvailable(fileDeletionRequests, Optional.empty());
        }
        return jobInfoList;
    }

    /**
     * Schedule a {@link JobInfo} for the given {@link  FileDeletionWorkingSubset}.<br/>
     * NOTE : A new transaction is created for each call at this method. It is mandatory to avoid having too long transactions.
     * @return {@link JobInfo} scheduled.
     */
    private JobInfo scheduleJob(FileDeletionWorkingSubset workingSubset, String pluginConfBusinessId) {
        Set<JobParameter> parameters = Sets.newHashSet();
        parameters.add(new JobParameter(FileStorageRequestJob.DATA_STORAGE_CONF_BUSINESS_ID, pluginConfBusinessId));
        parameters.add(new JobParameter(FileStorageRequestJob.WORKING_SUB_SET, workingSubset));
        JobInfo jobInfo = jobInfoService.createAsQueued(new JobInfo(false, JobsPriority.FILE_DELETION_JOB.getPriority(),
                parameters, authResolver.getUser(), FileDeletionRequestJob.class.getName()));
        workingSubset.getFileDeletionRequests().forEach(fileRefReq -> fileDeletionRequestRepo
                .updateStatusAndJobId(FileRequestStatus.PENDING, jobInfo.getId().toString(), fileRefReq.getId()));
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
    private void handleStorageNotAvailable(Collection<FileDeletionRequest> fileDeletionRequests,
            Optional<String> errorCause) {
        fileDeletionRequests.forEach((r) -> this.handleStorageNotAvailable(r, errorCause));
    }

    /**
     * Update a {@link FileDeletionRequest} when the storage destination cannot be handled.
     * A storage destination cannot be handled if <ul>
     * <li> No plugin configuration of {@link IStorageLocation} exists for the storage</li>
     * <li> the plugin configuration is disabled </li>
     * </ul>
     * @param fileDeletionRequest
     */
    private void handleStorageNotAvailable(FileDeletionRequest fileDeletionRequest, Optional<String> errorCause) {
        String lErrorCause = errorCause.orElse(String
                .format("File <%s> cannot be handle for deletion as destination storage <%s> is unknown or disabled.",
                        fileDeletionRequest.getFileReference().getMetaInfo().getFileName(),
                        fileDeletionRequest.getStorage()));
        // The storage destination is unknown, we can already set the request in error status
        fileDeletionRequest.setStatus(FileRequestStatus.ERROR);
        fileDeletionRequest.setErrorCause(lErrorCause);
        updateFileDeletionRequest(fileDeletionRequest);
    }

    /**
     * Delete a {@link FileDeletionRequest}
     * @param fileDeletionRequest
     */
    public void delete(FileDeletionRequest fileDeletionRequest) {
        Assert.notNull(fileDeletionRequest, "File deletion request to delete cannot be null");
        Assert.notNull(fileDeletionRequest.getId(), "File deletion request to delete identifier cannot be null");
        if (fileDeletionRequestRepo.existsById(fileDeletionRequest.getId())) {
            fileDeletionRequestRepo.deleteById(fileDeletionRequest.getId());
        } else {
            LOGGER.warn("Unable to delete file deletion request {} cause it does not exists.",
                        fileDeletionRequest.getId());
        }
    }

    /**
     * Initialize new deletion requests from Flow items.
     * @param list
     */
    public void handle(List<DeletionFlowItem> list) {
        Set<String> checksums = list.stream().map(DeletionFlowItem::getFiles).flatMap(Set::stream)
                .map(FileDeletionRequestDTO::getChecksum).collect(Collectors.toSet());
        Set<FileReference> existingOnes = fileRefService.search(checksums);
        Set<FileDeletionRequest> fileDeletionRequests = fileDeletionRequestRepo
                .findByFileReferenceMetaInfoChecksumIn(checksums);
        for (DeletionFlowItem item : list) {
            if (fileCopyReqService.isFileCopyRunning(item.getFiles().stream().map(i -> i.getChecksum())
                    .collect(Collectors.toSet()))) {
                reqGroupService.denied(item.getGroupId(), FileRequestType.DELETION,
                                       "Cannot delete files has a copy process is running");
                LOGGER.warn("Refused {} file deletion", item.getFiles().size());
            } else {
                reqGroupService.granted(item.getGroupId(), FileRequestType.DELETION, item.getFiles().size(),
                                        getRequestExpirationDate());
                handle(item.getFiles(), item.getGroupId(), existingOnes, fileDeletionRequests);
            }
        }
    }

    /**
     * Initialize new deletion requests for a given group identifier
     * @param requests
     * @param groupId
     */
    public void handle(Collection<FileDeletionRequestDTO> requests, String groupId) {
        Set<String> checksums = requests.stream().map(FileDeletionRequestDTO::getChecksum).collect(Collectors.toSet());
        Set<FileReference> existingOnes = fileRefService.search(checksums);
        Set<FileDeletionRequest> fileDeletionRequests = fileDeletionRequestRepo
                .findByFileReferenceMetaInfoChecksumIn(checksums);
        handle(requests, groupId, existingOnes, fileDeletionRequests);
    }

    /**
     * Initialize new deletion requests for a given group identifier. Parameter existingOnes is passed to improve performance in bulk creation to
     * avoid requesting {@link IFileReferenceRepository} on each request.
     * @param requests
     * @param groupId
     * @param existingOnes
     */
    public void handle(Collection<FileDeletionRequestDTO> requests, String groupId,
            Collection<FileReference> existingOnes, Collection<FileDeletionRequest> existingRequests) {
        for (FileDeletionRequestDTO request : requests) {
            Optional<FileReference> oFileRef = existingOnes.stream()
                    .filter(f -> f.getLocation().getStorage().equals(request.getStorage())
                            && f.getMetaInfo().getChecksum().equals(request.getChecksum()))
                    .findFirst();
            if (oFileRef.isPresent()) {
                FileReference fileRef = oFileRef.get();
                removeOwner(fileRef, request.getOwner(), request.isForceDelete(), existingRequests, groupId);
            }
            // In all case, inform caller that deletion request is success.
            reqGroupService.requestSuccess(groupId, FileRequestType.DELETION, request.getChecksum(),
                                           request.getStorage(), null, Sets.newHashSet(request.getOwner()), null);
        }
    }

    /**
     * Remove the given owner of the to the given {@link FileReference}.
     * If the owner is the last one this method tries to delete file physically if the storage location is a configured {@link IStorageLocation}.
     * @param forceDelete allows to delete fileReference even if the deletion is in error.
     * @param groupId Business identifier of the deletion request
     */
    private void removeOwner(FileReference fileReference, String owner, boolean forceDelete,
            Collection<FileDeletionRequest> existingRequests, String groupId) {
        fileRefService.removeOwner(fileReference, owner, groupId);
        // If file reference does not belongs to anyone anymore, delete file reference
        if (fileReference.getOwners().isEmpty()) {
            if (storageHandler.isConfigured(fileReference.getLocation().getStorage())) {
                // If the file is stored on an accessible storage, create a new deletion request
                create(fileReference, forceDelete, groupId, existingRequests, FileRequestStatus.TO_DO);
            } else {
                // Delete associated cache request if any
                fileCacheReqService.delete(fileReference);
                // Else, directly delete the file reference
                fileRefService.delete(fileReference, groupId);
            }
        }
    }

    /**
     * Update a {@link FileDeletionRequest}
     * @param fileDeletionRequest
     */
    public FileDeletionRequest updateFileDeletionRequest(FileDeletionRequest fileDeletionRequest) {
        Assert.notNull(fileDeletionRequest, "File deletion request to update cannot be null");
        Assert.notNull(fileDeletionRequest.getId(), "File deletion request to update identifier cannot be null");
        return fileDeletionRequestRepo.save(fileDeletionRequest);
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

    @Transactional(readOnly = true)
    public Set<FileDeletionRequest> searchByChecksums(Set<String> checksums) {
        return fileDeletionRequestRepo.findByFileReferenceMetaInfoChecksumIn(checksums);
    }

    @Transactional(readOnly = true)
    public Set<FileDeletionRequest> search(Set<FileReference> fileReferences) {
        return fileDeletionRequestRepo
                .findByFileReferenceIdIn(fileReferences.stream().map(FileReference::getId).collect(Collectors.toSet()));
    }

    @Transactional(readOnly = true)
    public Page<FileDeletionRequest> search(String storage, FileRequestStatus status, Pageable page) {
        return fileDeletionRequestRepo.findByStorageAndStatus(storage, status, page);
    }

    @Transactional(readOnly = true)
    public Page<FileDeletionRequest> search(String storage, Pageable page) {
        return fileDeletionRequestRepo.findByStorage(storage, page);
    }

    @Transactional(readOnly = true)
    public Optional<FileDeletionRequest> search(String checksum, String storage) {
        return fileDeletionRequestRepo.findByStorageAndFileReferenceMetaInfoChecksum(storage, checksum);
    }

    @Transactional(readOnly = true)
    public Long count(String storage, FileRequestStatus status) {
        return fileDeletionRequestRepo.countByStorageAndStatus(storage, status);
    }

    public void handleError(FileDeletionRequest fileDeletionRequest, String errorCause) {
        FileReference fileRef = fileDeletionRequest.getFileReference();
        if (!fileDeletionRequest.isForceDelete()) {
            // No force delete option. So request is in error status.
            // Update request in error status
            fileDeletionRequest.setStatus(FileRequestStatus.ERROR);
            fileDeletionRequest.setErrorCause(errorCause);
            updateFileDeletionRequest(fileDeletionRequest);
            // Publish deletion error
            publisher.deletionError(fileRef, errorCause, fileDeletionRequest.getGroupId());
            // Publish request error
            reqGroupService.requestError(fileDeletionRequest.getGroupId(), FileRequestType.DELETION,
                                         fileRef.getMetaInfo().getChecksum(), fileRef.getLocation().getStorage(), null,
                                         fileRef.getOwners(), errorCause);
        } else {
            // Force delete option.
            handleSuccess(fileDeletionRequest);
            // NOTE : The file reference event is published by the fileReferenceService
            LOGGER.warn(String
                    .format("File %s from %s (checksum: %s) has been removed by force from referenced files. (File may still exists on storage).",
                            fileRef.getMetaInfo().getFileName(), fileRef.getLocation().toString(),
                            fileRef.getMetaInfo().getChecksum()));
        }
    }

    /**
     * Handle a {@link FileDeletionRequest} success.
     * @param fileDeletionRequest
     */
    public void handleSuccess(FileDeletionRequest fileDeletionRequest) {
        FileReference deletedFileRef = fileDeletionRequest.getFileReference();
        // 1. Delete the request in database
        delete(fileDeletionRequest);
        // 2. Delete cache request if any
        fileCacheReqService.delete(deletedFileRef);
        // 3. Delete the file reference in database
        fileRefService.delete(deletedFileRef, fileDeletionRequest.getGroupId());
    }

    /**
     * Schedule a job to create deletion requests for all files matching the given criterion.
     * @param storageLocationId
     * @param forceDelete
     * @throws ModuleException
     */
    public JobInfo scheduleJob(String storageLocationId, Boolean forceDelete) throws ModuleException {
        // Check if a job of deletion already exists
        if (jobInfoService.retrieveJobsCount(FileDeletionRequestsCreatorJob.class.getName(), JobStatus.RUNNING) > 0) {
            throw new ModuleException("Impossible to run a files deletion process as a previous one is still running");
        } else {
            Set<JobParameter> parameters = Sets.newHashSet();
            parameters.add(new JobParameter(FileDeletionRequestsCreatorJob.STORAGE_LOCATION_ID, storageLocationId));
            parameters.add(new JobParameter(FileDeletionRequestsCreatorJob.FORCE_DELETE, forceDelete));
            JobInfo jobInfo = jobInfoService
                    .createAsQueued(new JobInfo(false, JobsPriority.FILE_DELETION_JOB.getPriority(), parameters,
                            authResolver.getUser(), FileDeletionRequestsCreatorJob.class.getName()));
            LOGGER.debug("[DELETION REQUESTS] Job scheduled to delete all files from storage location {} (force={}).",
                         storageLocationId, forceDelete);
            return jobInfo;
        }
    }

    /**
     * Delete all requests for the given storage identifier
     * @param storageLocationId
     */
    public void deleteByStorage(String storageLocationId, Optional<FileRequestStatus> status) {
        if (status.isPresent()) {
            fileDeletionRequestRepo.deleteByStorageAndStatus(storageLocationId, status.get());
        } else {
            fileDeletionRequestRepo.deleteByStorage(storageLocationId);
        }
    }

    /**
     * Lock deletion process for all instance of storage microservice
     * @param blockingMode
     * @param expiresIn seconds
     */
    public boolean lockDeletionProcess(boolean blockingMode, int expiresIn) {
        boolean lock = false;
        if (blockingMode) {
            lock = lockService.waitForlock(DeletionFlowItem.DELETION_LOCK, new DeletionFlowItem(), expiresIn, 30000);
        } else {
            lock = lockService.obtainLockOrSkip(DeletionFlowItem.DELETION_LOCK, new DeletionFlowItem(), expiresIn);
        }
        if (lock) {
            LOGGER.trace("[DELETION PROCESS] Locked !");
        }
        return lock;
    }

    /**
     * Release deletion process for all instance of storage microservice
     */
    public void releaseLock() {
        lockService.releaseLock(DeletionFlowItem.DELETION_LOCK, new DeletionFlowItem());
        LOGGER.trace("[DELETION PROCESS] Lock released !");
    }

    /**
     * Retrieve expiration date for deletion request
     * @return
     */
    public OffsetDateTime getRequestExpirationDate() {
        if ((nbDaysBeforeExpiration != null) && (nbDaysBeforeExpiration > 0)) {
            return OffsetDateTime.now().plusDays(nbDaysBeforeExpiration);
        } else {
            return null;
        }
    }

}
