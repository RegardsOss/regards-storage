/*
 * LICENSE_PLACEHOLDER
 */
package fr.cnes.regards.modules.storage.service;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import fr.cnes.regards.framework.module.rest.exception.EntityNotFoundException;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.oais.DataObject;
import fr.cnes.regards.framework.oais.urn.UniformResourceName;
import fr.cnes.regards.modules.storage.domain.AIP;
import fr.cnes.regards.modules.storage.domain.AIPState;
import fr.cnes.regards.modules.storage.domain.database.AvailabilityRequest;
import fr.cnes.regards.modules.storage.domain.database.AvailabilityResponse;
import fr.cnes.regards.modules.storage.domain.database.DataFile;
import fr.cnes.regards.modules.storage.plugin.IDataStorage;

/**
 * @author Sylvain Vissiere-Guerinet
 *
 */
public interface IAIPService {

    /**
     * retrieve pages of AIP filtered according to the parameters
     *
     * @param pTo
     *            maximum date of last event that affected any AIP wanted
     * @param pFrom
     *            minimum date of submission into REGARDS wanted
     * @param pState
     *            State of AIP wanted
     * @param pPageable
     *            pageable object
     * @return filtered page of AIP
     */
    Page<AIP> retrieveAIPs(AIPState pState, OffsetDateTime pFrom, OffsetDateTime pTo, Pageable pPageable);

    /**
     * Run asynchronous jobs to handle new {@link AIP}s creation.<br/>
     * This process handle :
     * <ul>
     * <li> Storage in db of {@link AIP}</li>
     * <li> Storage in db of each {@link DataFile} associated </li>
     * <li> Physical storage of each {@link DataFile} through {@link IDataStorage} plugins</li>
     * <li> Creation of physical file containing AIP metadata informations and storage through {@link IDataStorage} plugins</li>
     * </ul>
     * @param pAIP new {@link Set}<{@link AIP}> to create
     * @return {@link Set}<{@link UUID}> of scheduled store AIP Jobs.
     * @throws ModuleException
     */
    Set<UUID> create(Set<AIP> pAIP) throws ModuleException;

    /**
     * Update existing AIPs
     * @param pAIP existing {@link Set}<{@link AIP}> to update
     * @return {@link Set}<{@link UUID}> of scheduled update AIP Jobs.
     * @throws ModuleException
     */
    Set<UUID> update(Set<AIP> pAIP) throws ModuleException;

    /**
     * @param pIpId
     * @return
     * @throws EntityNotFoundException
     */
    List<DataObject> retrieveAIPFiles(UniformResourceName pIpId) throws EntityNotFoundException;

    /**
     * @param pIpId
     * @return
     */
    List<String> retrieveAIPVersionHistory(UniformResourceName pIpId);

    /**
     * load files into the cache if necessary
     * @param availabilityRequest
     * @return checksums of files that are already available
     */
    AvailabilityResponse loadFiles(AvailabilityRequest availabilityRequest);

    void updateAlreadyStoredMetadata();

    ///////////////////////////////////////////////////////////////////////////////////////////
    ////////////////// These methods should only be called by IAIPServices
    ///////////////////////////////////////////////////////////////////////////////////////////

    void scheduleStorageMetadata(Set<DataFile> metadataToStore);

    void scheduleStorageMetadataUpdate(Set<AIPService.History> metadataToUpdate);

    Set<AIPService.History> prepareUpdatedAIP(Path tenantWorkspace);

    Set<DataFile> prepareNotFullyStored(Path tenantWorkspace);
}
