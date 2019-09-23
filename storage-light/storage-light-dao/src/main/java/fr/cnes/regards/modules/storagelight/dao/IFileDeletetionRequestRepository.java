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
package fr.cnes.regards.modules.storagelight.dao;

import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import fr.cnes.regards.modules.storagelight.domain.database.request.FileDeletionRequest;
import fr.cnes.regards.modules.storagelight.domain.database.request.FileRequestStatus;

/**
 * @author sbinda
 *
 */
public interface IFileDeletetionRequestRepository extends JpaRepository<FileDeletionRequest, Long> {

    Optional<FileDeletionRequest> findByFileReferenceId(Long fileReferenceId);

    Page<FileDeletionRequest> findByStorage(String storage, Pageable page);

    @Query("select storage from FileDeletionRequest where status = :status")
    Set<String> findStoragesByStatus(@Param("status") FileRequestStatus status);

    @Modifying
    @Query("update FileDeletionRequest fdr set fdr.status = :status where fdr.id = :id")
    int updateStatus(@Param("status") FileRequestStatus status, @Param("id") Long id);

    boolean existsByGroupId(String groupId);

    Set<FileDeletionRequest> findByGroupId(String groupId);

    Set<FileDeletionRequest> findByGroupIdAndStatus(String groupId, FileRequestStatus error);

    Page<FileDeletionRequest> findByStatus(FileRequestStatus status, Pageable page);

    boolean existsByGroupIdAndStatusNot(String groupId, FileRequestStatus error);

    Page<FileDeletionRequest> findByStorageAndStatus(String storage, FileRequestStatus status, Pageable page);

    Long countByStorageAndStatus(String storage, FileRequestStatus status);

    void deleteByStorage(String storageLocationId);

}