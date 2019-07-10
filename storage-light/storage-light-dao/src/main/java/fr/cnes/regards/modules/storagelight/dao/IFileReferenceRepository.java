package fr.cnes.regards.modules.storagelight.dao;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import fr.cnes.regards.modules.storagelight.domain.StorageMonitoringAggregation;
import fr.cnes.regards.modules.storagelight.domain.database.FileReference;

/**
 *
 * @author Sébastien Binda
 */
public interface IFileReferenceRepository
        extends JpaRepository<FileReference, Long>, JpaSpecificationExecutor<FileReference> {

    Optional<FileReference> findByMetaInfoChecksumAndLocationStorage(String checksum, String storage);

    @Query("select fr.location.storage as storage, sum(fr.metaInfo.fileSize) as usedSize, count(*) as numberOfFileReference, max(fr.id) as lastFileReferenceId"
            + " from FileReference fr group by storage")
    Collection<StorageMonitoringAggregation> getTotalFileSizeAggregation();

    @Query("select fr.location.storage as storage, sum(fr.metaInfo.fileSize) as usedSize, count(*) as numberOfFileReference, max(fr.id) as lastFileReferenceId"
            + " from FileReference fr where fr.id >= :id")
    Collection<StorageMonitoringAggregation> getTotalFileSizeAggregation(@Param("id") Long fromFileReferenceId);

}
