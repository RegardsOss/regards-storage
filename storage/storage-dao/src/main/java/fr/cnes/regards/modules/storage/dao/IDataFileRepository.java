package fr.cnes.regards.modules.storage.dao;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import fr.cnes.regards.framework.oais.urn.DataType;
import fr.cnes.regards.modules.storage.domain.database.AIPEntity;
import fr.cnes.regards.modules.storage.domain.database.DataFile;
import fr.cnes.regards.modules.storage.domain.database.DataFileState;
import fr.cnes.regards.modules.storage.domain.database.MonitoringAggregation;

/**
 * Repository handling JPA representation of metadata of files associated to aips
 *
 * @author Sylvain VISSIERE-GUERINET
 */
public interface IDataFileRepository extends JpaRepository<DataFile, Long> {

    /**
     * Find all data files which state is the given one and which are associated to the provided aip entity
     * @param stored
     * @param aipEntity
     * @return data files which state is the given one and which are associated to the provided aip entity
     */
    @EntityGraph(value = "graph.datafile.full")
    Set<DataFile> findAllByStateAndAipEntity(DataFileState stored, AIPEntity aipEntity);

    /**
     * Find all {@link DataFile}s associated to the given aip entity
     * @param aipEntity
     * @return {@link DataFile}s
     */
    @EntityGraph(value = "graph.datafile.full")
    Set<DataFile> findAllByAipEntity(AIPEntity aipEntity);

    /**
     * Find the data file associated to the given aip entity and which type the provided one
     * @param aipEntity
     * @param dataType
     * @return the data file wrapped into an optional to avoid nulls
     */
    @EntityGraph(value = "graph.datafile.full")
    Optional<DataFile> findByAipEntityAndDataType(AIPEntity aipEntity, DataType dataType);

    /**
     * Retrieve a data file by its id
     * @param dataFileId
     * @return the data file wrapped into an optional to avoid nulls
     */
    @EntityGraph(value = "graph.datafile.full")
    Optional<DataFile> findOneById(Long dataFileId);

    /**
     * Find all data files which checksum is one of the provided ones
     * @param checksums
     * @return data files which checksum is one of the provided ones
     */
    @EntityGraph(value = "graph.datafile.full")
    Set<DataFile> findAllByChecksumIn(Set<String> checksums);

    /**
     * Find all data files which state is the provided one and that are associated to at least one of the provided aip entities
     * @param dataFileState
     * @param aipEntities
     * @return data files which state is the provided one and that are associated to at least one of the provided aip entities
     */
    @EntityGraph(value = "graph.datafile.full")
    Set<DataFile> findAllByStateAndAipEntityIn(DataFileState dataFileState, Collection<AIPEntity> aipEntities);

    /**
     * Calculate the monitoring information on all data files of the database
     * @return the monitoring aggregation
     */
    @Query("SELECT df.dataStorageUsed.id as dataStorageUsedId, sum(df.fileSize) as usedSize FROM DataFile df GROUP BY df.dataStorageUsed.id")
    Collection<MonitoringAggregation> getMonitoringAggregation();
}
