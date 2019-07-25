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

import org.apache.commons.compress.utils.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import com.google.common.collect.Lists;

import fr.cnes.regards.framework.jpa.multitenant.transactional.MultitenantTransactional;
import fr.cnes.regards.framework.module.rest.exception.EntityNotFoundException;
import fr.cnes.regards.modules.storagelight.dao.IFileReferenceRepository;
import fr.cnes.regards.modules.storagelight.domain.FileRequestStatus;
import fr.cnes.regards.modules.storagelight.domain.database.FileDeletionRequest;
import fr.cnes.regards.modules.storagelight.domain.database.FileLocation;
import fr.cnes.regards.modules.storagelight.domain.database.FileReference;
import fr.cnes.regards.modules.storagelight.domain.database.FileReferenceMetaInfo;
import fr.cnes.regards.modules.storagelight.domain.database.StorageMonitoringAggregation;
import fr.cnes.regards.modules.storagelight.domain.flow.AddFileRefFlowItem;
import fr.cnes.regards.modules.storagelight.domain.flow.DeleteFileRefFlowItem;
import fr.cnes.regards.modules.storagelight.domain.plugin.IDataStorage;
import fr.cnes.regards.modules.storagelight.service.file.reference.flow.FileRefEventPublisher;
import fr.cnes.regards.modules.storagelight.service.storage.flow.StoragePluginConfigurationHandler;

/**
 * Service to handle File references.<br/>
 *
 * <b>File reference definition : </b><ul>
 *  <li> Mandatory checksum in {@link FileReferenceMetaInfo} </li>
 *  <li> Mandatory storage location in {@link FileLocation}</li>
 *  <li> Optional list of owners</li>
 * </ul>
 *
 * <b> File reference physical location : </b><br/>
 * A file can be referenced through this system by : <ul>
 * <li> Storing file on a storage location thanks to {@link IDataStorage} plugins </li>
 * <li> Only reference file assuming the file location is handled externally </li>
 * </ul>
 * A file reference storage/deletion is handled by the service if the storage location is known as a {@link IDataStorage} plugin configuration.<br/>
 *
 * <b>File reference owners : </b><br/>
 * When a file reference does not have any owner, then it is scheduled for deletion.<br/>
 *
 * <b> Entry points : </b><br/>
 * File references can be created using AMQP messages {@link AddFileRefFlowItem}.<br/>
 * File references can be deleted using AMQP messages {@link DeleteFileRefFlowItem}.<br/>
 * File references can be copied in cache system using AMQP messages TODO<br/>
 * File references can be download using TODO<br/>
 *
 * @author Sébastien Binda
 */
@Service
@MultitenantTransactional
public class FileReferenceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileReferenceService.class);

    @Autowired
    private IFileReferenceRepository fileRefRepo;

    @Autowired
    private FileReferenceRequestService fileRefRequestService;

    @Autowired
    private FileDeletionRequestService fileDeletionRequestService;

    @Autowired
    private StoragePluginConfigurationHandler storageHandler;

    @Autowired
    private FileRefEventPublisher fileRefPublisher;

    public Page<FileReference> search(String storage, Pageable pageable) {
        return fileRefRepo.findByLocationStorage(storage, pageable);
    }

    public Optional<FileReference> search(String storage, String checksum) {
        return fileRefRepo.findByMetaInfoChecksumAndLocationStorage(checksum, storage);
    }

    public Page<FileReference> search(Pageable pageable) {
        return fileRefRepo.findAll(pageable);
    }

    public Page<FileReference> search(Specification<FileReference> spec, Pageable page) {
        return fileRefRepo.findAll(spec, page);
    }

    public Optional<FileReference> addFileReference(AddFileRefFlowItem item) {
        FileReferenceMetaInfo metaInfo = new FileReferenceMetaInfo(item.getChecksum(), item.getAlgorithm(),
                item.getFileName(), item.getFileSize(), MediaType.valueOf(item.getMimeType()));
        return this.addFileReference(Lists.newArrayList(item.getOwner()), metaInfo, item.getOrigine(),
                                     item.getDestination());
    }

    /**
     * <b>Method to reference a given file</b> <br/><br />
     * If the file is <b>already referenced</b> in the destination storage,
     * this method only add the requesting owner to the file reference owner list.
     * <br/>
     * If the <b>origin destination equals the destination origin</b>, so reference the file as already stored.
     *
     * @param fileRefRequest file to reference
     * @return FileReference if already exists or does not need a new storage job
     */
    public Optional<FileReference> addFileReference(Collection<String> owners, FileReferenceMetaInfo fileMetaInfo,
            FileLocation origin, FileLocation destination) {

        Assert.notNull(owners, "File must have a owner to be referenced");
        Assert.isTrue(!owners.isEmpty(), "File must have a owner to be referenced");
        Assert.notNull(fileMetaInfo, "File must have an origin location to be referenced");
        Assert.notNull(fileMetaInfo.getChecksum(), "File checksum is mandatory");
        Assert.notNull(fileMetaInfo.getAlgorithm(), "File checksum algorithm is mandatory");
        Assert.notNull(fileMetaInfo.getFileName(), "File name is mandatory");
        Assert.notNull(fileMetaInfo.getMimeType(), "File mime type is mandatory");
        Assert.notNull(fileMetaInfo.getFileSize(), "File size is mandatory");
        Assert.notNull(origin, "File must have an origin location to be referenced");
        Assert.notNull(destination, "File must have an origin location to be referenced");

        // Does file is already referenced for the destination location ?
        Optional<FileReference> oFileRef = fileRefRepo
                .findByMetaInfoChecksumAndLocationStorage(fileMetaInfo.getChecksum(), destination.getStorage());
        if (oFileRef.isPresent()) {
            this.handleFileReferenceAlreadyExists(oFileRef.get(), fileMetaInfo, origin, destination, owners);
        } else {
            // If destination equals origin location so file is already stored and can be referenced directly
            if (destination.equals(origin)) {
                oFileRef = Optional.of(this.createNewFileReference(owners, fileMetaInfo, destination));
            } else {
                fileRefRequestService.createNewFileReferenceRequest(owners, fileMetaInfo, origin, destination);
            }
        }
        return oFileRef;
    }

    public void deleteFileReference(FileReference fileRef) {
        Assert.notNull(fileRef, "File reference to delete cannot be null");
        Assert.notNull(fileRef.getId(), "File reference identifier to delete cannot be null");
        fileRefRepo.deleteById(fileRef.getId());
        String message = String.format("File reference %s (checksum: %s) as been completly deleted for all owners.",
                                       fileRef.getMetaInfo().getFileName(), fileRef.getMetaInfo().getChecksum());
        fileRefPublisher.publishFileRefDeleted(fileRef, message);
    }

    /**
     *
     * @param checksum
     * @param storage
     * @param owner
     * @param forceDelete allows to delete fileReference even if the deletion is in error.
     * @throws EntityNotFoundException
     */
    public void removeFileReferenceForOwner(String checksum, String storage, String owner, boolean forceDelete)
            throws EntityNotFoundException {

        Assert.notNull(checksum, "Checksum is mandatory to delete a file reference");
        Assert.notNull(storage, "Storage is mandatory to delete a file reference");
        Assert.notNull(owner, "Owner is mandatory to delete a file reference");

        Optional<FileReference> oFileRef = fileRefRepo.findByMetaInfoChecksumAndLocationStorage(checksum, storage);
        if (oFileRef.isPresent()) {
            this.removeOwner(oFileRef.get(), owner, forceDelete);
        } else {
            throw new EntityNotFoundException(String
                    .format("File reference with ckesum: <%s> and storage: <%s> doest not exists", checksum, storage));
        }
    }

    /**
     *
     * @param fileReference
     * @param owner
     * @param forceDelete allows to delete fileReference even if the deletion is in error.
     */
    private void removeOwner(FileReference fileReference, String owner, boolean forceDelete) {
        String message;
        if (!fileReference.getOwners().contains(owner)) {
            message = String.format("File <%s (checksum: %s)> at %s does not to belongs to %s",
                                    fileReference.getMetaInfo().getFileName(),
                                    fileReference.getMetaInfo().getChecksum(), fileReference.getLocation().toString(),
                                    owner);

        } else {
            fileReference.getOwners().remove(owner);
            message = String.format("File reference <%s (checksum: %s)> at %s does not belongs to %s anymore",
                                    fileReference.getMetaInfo().getFileName(),
                                    fileReference.getMetaInfo().getChecksum(), fileReference.getLocation().toString(),
                                    owner);
            fileRefRepo.save(fileReference);
        }

        LOGGER.debug(message);
        // Inform owners that the file reference is considered has delete for him.
        fileRefPublisher.publishFileRefDeletedForOwner(fileReference, owner, message);

        // If file reference does not belongs to anyone anymore, delete file reference
        if (fileReference.getOwners().isEmpty()) {
            if (storageHandler.getConfiguredStorages().contains(fileReference.getLocation().getStorage())) {
                // If the file is stored on an accessible storage, create a new deletion request
                fileDeletionRequestService.createNewFileDeletionRequest(fileReference, forceDelete);
            } else {
                // Else, directly delete the file reference
                this.deleteFileReference(fileReference);
            }
        }
    }

    private FileReference createNewFileReference(Collection<String> owners, FileReferenceMetaInfo fileMetaInfo,
            FileLocation location) {
        FileReference fileRef = new FileReference(owners, fileMetaInfo, location);
        fileRef = fileRefRepo.save(fileRef);
        String message = String.format("New file <%s> referenced at <%s> (checksum: %s)",
                                       fileRef.getMetaInfo().getFileName(), fileRef.getLocation().toString(),
                                       fileRef.getMetaInfo().getChecksum());
        LOGGER.debug(message);
        fileRefPublisher.publishFileRefStored(fileRef, message);
        return fileRef;
    }

    public Collection<StorageMonitoringAggregation> calculateTotalFileSizeAggregation(Long lastReferencedFileId) {
        if (lastReferencedFileId != null) {
            return fileRefRepo.getTotalFileSizeAggregation(lastReferencedFileId);
        } else {
            return fileRefRepo.getTotalFileSizeAggregation();
        }
    }

    private void handleFileReferenceAlreadyExists(FileReference fileReference, FileReferenceMetaInfo newMetaInfo,
            FileLocation origin, FileLocation destination, Collection<String> owners) {
        Set<String> newOwners = Sets.newHashSet();

        Optional<FileDeletionRequest> deletionRequest = fileDeletionRequestService.search(fileReference);
        if (deletionRequest.isPresent() && (deletionRequest.get().getStatus() == FileRequestStatus.PENDING)) {
            // Deletion is running write now, so delay the new file reference creation with a FileReferenceRequest
            fileRefRequestService.createNewFileReferenceRequest(owners, newMetaInfo, origin, destination,
                                                                FileRequestStatus.DELAYED);
        } else {
            if (deletionRequest.isPresent()) {
                // Delete not running deletion request to add the new owner
                fileDeletionRequestService.deleteFileDeletionRequest(deletionRequest.get());
            }
            for (String owner : owners) {
                if (!fileReference.getOwners().contains(owner)) {
                    newOwners.add(owner);
                    fileReference.getOwners().add(owner);
                    String message = String
                            .format("New owner <%s> added to existing referenced file <%s> at <%s> (checksum: %s) ",
                                    owner, fileReference.getMetaInfo().getFileName(),
                                    fileReference.getLocation().toString(), fileReference.getMetaInfo().getChecksum());
                    LOGGER.debug(message);
                    if (!fileReference.getMetaInfo().equals(newMetaInfo)) {
                        LOGGER.warn("Existing referenced file meta information differs "
                                + "from new reference meta information. Previous ones are maintained");
                    }
                }
            }
            String message = null;
            if (!newOwners.isEmpty()) {
                fileRefRepo.save(fileReference);
                message = String
                        .format("New owners <%s> added to existing referenced file <%s> at <%s> (checksum: %s) ",
                                Arrays.toString(newOwners.toArray()), fileReference.getMetaInfo().getFileName(),
                                fileReference.getLocation().toString(), fileReference.getMetaInfo().getChecksum());

            } else {
                message = String.format("File <%s> already referenced at <%s> (checksum: %s) for owners <%>",
                                        fileReference.getMetaInfo().getFileName(),
                                        fileReference.getLocation().toString(),
                                        fileReference.getMetaInfo().getChecksum(),
                                        Arrays.toString(fileReference.getOwners().toArray()));
            }
            fileRefPublisher.publishFileRefStored(fileReference, message);
        }
    }

    public void addFileReferences(List<AddFileRefFlowItem> items) {
        for (AddFileRefFlowItem item : items) {
            FileReferenceMetaInfo metaInfo = new FileReferenceMetaInfo(item.getChecksum(), item.getAlgorithm(),
                    item.getFileName(), item.getFileSize(), MediaType.valueOf(item.getMimeType()));
            this.addFileReference(Lists.newArrayList(item.getOwner()), metaInfo, item.getOrigine(),
                                  item.getDestination());
        }
    }

}
