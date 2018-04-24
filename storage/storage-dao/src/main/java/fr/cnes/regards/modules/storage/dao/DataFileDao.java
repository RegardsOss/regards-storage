package fr.cnes.regards.modules.storage.dao;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.collect.Sets;

import fr.cnes.regards.framework.oais.urn.DataType;
import fr.cnes.regards.modules.storage.domain.AIP;
import fr.cnes.regards.modules.storage.domain.database.AIPEntity;
import fr.cnes.regards.modules.storage.domain.database.DataFileState;
import fr.cnes.regards.modules.storage.domain.database.MonitoringAggregation;
import fr.cnes.regards.modules.storage.domain.database.StorageDataFile;

/**
 * @author Sylvain VISSIERE-GUERINET
 */
@Component
public class DataFileDao implements IDataFileDao {

    /**
     * {@link IStorageDataFileRepository} instance
     */
    @Autowired
    private final IStorageDataFileRepository repository;

    /**
     * {@link IAIPEntityRepository} instance
     */
    @Autowired
    private final IAIPEntityRepository aipRepo;

    /**
     * Constructor setting the parameters as attributes
     * @param repository
     * @param aipRepo
     */
    public DataFileDao(IStorageDataFileRepository repository, IAIPEntityRepository aipRepo) {
        this.repository = repository;
        this.aipRepo = aipRepo;
    }

    @Override
    public Set<StorageDataFile> findAllByStateAndAip(DataFileState stored, AIP aip) {
        Optional<AIPEntity> aipDatabase = getAipDataBase(aip);
        if (aipDatabase.isPresent()) {
            return repository.findAllByStateAndAipEntity(stored, aipDatabase.get());
        } else {
            return Sets.newHashSet();
        }
    }

    @Override
    public Set<StorageDataFile> findAllByStateAndAipIn(DataFileState dataFileState, Collection<AIP> aips) {
        Set<AIPEntity> aipDataBases = Sets.newHashSet();
        for (AIP aip : aips) {
            Optional<AIPEntity> aipDatabase = getAipDataBase(aip);
            if (aipDatabase.isPresent()) {
                aipDataBases.add(aipDatabase.get());
            }
        }
        if (aipDataBases.isEmpty()) {
            return Sets.newHashSet();
        } else {
            return repository.findAllByStateAndAipEntityIn(dataFileState, aipDataBases);
        }
    }

    @Override
    public Set<StorageDataFile> findAllByAip(AIP aip) {
        Optional<AIPEntity> aipDatabase = getAipDataBase(aip);
        if (aipDatabase.isPresent()) {
            return repository.findAllByAipEntity(aipDatabase.get());
        } else {
            return Sets.newHashSet();
        }
    }

    @Override
    public Set<StorageDataFile> findAllByAipIn(Collection<AIP> aips) {
        Collection<AIPEntity> aipEntities = findAipsDataBase(aips);
        return repository.findAllByAipEntityIn(aipEntities);
    }

    @Override
    public StorageDataFile save(StorageDataFile dataFile) {
        Optional<AIPEntity> aipDatabase = getAipDataBase(dataFile);
        if (aipDatabase.isPresent()) {
            dataFile.setAipEntity(aipDatabase.get());
        }
        return repository.save(dataFile);
    }

    @Override
    public Collection<StorageDataFile> save(Collection<StorageDataFile> dataFiles) {
        for (StorageDataFile dataFile : dataFiles) {
            Optional<AIPEntity> aipDatabase = getAipDataBase(dataFile);
            if (aipDatabase.isPresent()) {
                dataFile.setAipEntity(aipDatabase.get());
            }
        }
        return repository.save(dataFiles);
    }

    @Override
    public Optional<StorageDataFile> findByAipAndType(AIP aip, DataType dataType) {
        Optional<AIPEntity> aipDatabase = getAipDataBase(aip);
        if (aipDatabase.isPresent()) {
            return repository.findByAipEntityAndDataType(aipDatabase.get(), dataType);
        } else {
            return Optional.empty();
        }
    }

    @Override
    public void deleteAll() {
        repository.deleteAll();
    }

    @Override
    public Optional<StorageDataFile> findOneById(Long dataFileId) {
        return repository.findOneById(dataFileId);
    }

    @Override
    public Optional<StorageDataFile> findLockedOneById(Long dataFileId) {
        return repository.findLockedOneById(dataFileId);
    }

    @Override
    public Set<StorageDataFile> findAllByChecksumIn(Set<String> checksums) {
        return repository.findAllByChecksumIn(checksums);
    }

    @Override
    public void remove(StorageDataFile data) {
        repository.delete(data.getId());
    }

    @Override
    public Collection<MonitoringAggregation> getMonitoringAggregation() {
        return repository.getMonitoringAggregation();
    }

    private Optional<AIPEntity> getAipDataBase(AIP aip) {
        return aipRepo.findOneByIpId(aip.getId().toString());
    }

    private Collection<AIPEntity> findAipsDataBase(Collection<AIP> aips) {
        return aipRepo.findAllByIpIdIn(aips.stream().map(aip -> aip.getId().toString()).collect(Collectors.toSet()));
    }


    private Optional<AIPEntity> getAipDataBase(StorageDataFile dataFile) {
        return aipRepo.findOneByIpId(dataFile.getAipEntity().getIpId());
    }
}
