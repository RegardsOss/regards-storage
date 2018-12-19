package fr.cnes.regards.modules.storage.dao;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import fr.cnes.regards.framework.oais.urn.DataType;
import fr.cnes.regards.modules.storage.domain.AIP;
import fr.cnes.regards.modules.storage.domain.database.DataFileState;
import fr.cnes.regards.modules.storage.domain.database.MonitoringAggregation;
import fr.cnes.regards.modules.storage.domain.database.StorageDataFile;

/**
 * DAO to access metadata of files associated to aips into the database
 *
 * @author Sylvain VISSIERE-GUERINET
 */
public interface IDataFileDao {

    /**
     * Find all data files which state is the given one and which are associated to the provided aip
     * @param stored
     * @param aip
     * @return data files which state is the given one and which are associated to the provided aip
     */
    Set<StorageDataFile> findAllByStateAndAip(DataFileState stored, AIP aip);

    long findAllByStateAndAipSession(DataFileState stored, String session);

    /**
     * Find all data files which state is the given one
     * @param state
     * @return data files which state is the given one
     */
    Page<StorageDataFile> findAllByState(DataFileState state, Pageable pageable);

    Page<StorageDataFile> findPageByState(DataFileState state, Pageable pageable);

    /**
     * Find all data files which state is the provided one and that are associated to at least one of the provided aips
     * @param dataFileState
     * @param aips
     * @return data files which state is the provided one and that are associated to at least one of the provided aips
     */
    Set<StorageDataFile> findAllByStateAndAipIn(DataFileState dataFileState, Collection<AIP> aips);

    /**
     * Find all {@link StorageDataFile}s associated to the given {@link AIP}
     * @param aip {@link AIP}
     * @return {@link StorageDataFile}s
     */
    Set<StorageDataFile> findAllByAip(AIP aip);

    /**
     * Find all {@link StorageDataFile}s associated to the given {@link AIP}s
     * @param aips some {@link AIP}s
     * @return {@link StorageDataFile}s
     */
    Set<StorageDataFile> findAllByAipIn(Collection<AIP> aips);

    /**
     * Save a data file into the database
     * @param prepareFailed
     * @return saved data file
     */
    StorageDataFile save(StorageDataFile prepareFailed);

    /**
     * Save data files into the database
     * @param dataFiles
     * @return saved data files
     */
    Collection<StorageDataFile> save(Collection<StorageDataFile> dataFiles);

    /**
     * Find the data file associated to the given aip and which type the provided one
     * @param aip
     * @param dataType
     * @return the data file wrapped into an optional to avoid nulls
     */
    Set<StorageDataFile> findByAipAndType(AIP aip, DataType dataType);

    /**
     * Remove all data files from the database
     */
    void deleteAll();

    /**
     * Retrieve a data file by its id
     * @param dataFileId
     * @return the data file wrapped into an optional to avoid nulls
     */
    Optional<StorageDataFile> findOneById(Long dataFileId);

    Optional<StorageDataFile> findLockedOneById(Long dataFileId);

    /**
     * Find all data files which checksum is one of the provided ones
     * @param checksums
     * @return data files which checksum is one of the provided ones
     */
    Set<StorageDataFile> findAllByChecksumIn(Set<String> checksums);

    Page<StorageDataFile> findPageByChecksumIn(Set<String> checksums, Pageable pageable);

    Page<StorageDataFile> findPageByStateAndChecksumIn(DataFileState stored, Set<String> requestedChecksums, Pageable page);

    /**
     * Remove a data file from the database
     * @param data
     */
    void remove(StorageDataFile data);

    /**
     * Calculate the monitoring information on all data files of the database
     * @return the monitoring aggregation
     */
    Collection<MonitoringAggregation> getMonitoringAggregation();

    long countByChecksumAndStorageDirectory(String checksum, String storageDirectory);

    long countByAip(AIP aip);

    long countByAipAndStateNotIn(AIP aip, Collection<DataFileState> dataFilesStates);

    long findAllByAipSession(String id);

    Set<StorageDataFile> findAllByAipIpIdIn(Collection<String> ipId);

    long countByAipAndByState(AIP aip, DataFileState dataFileState);

    List<StorageDataFile> findAllByAipInQuery(String aipQuery);

    List<StorageDataFile> findAllById(List<Long> dataFileIds);
}
