/*
 * Copyright 2017-2018 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
package fr.cnes.regards.modules.storage.dao;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import javax.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import fr.cnes.regards.modules.storage.domain.AIPState;
import fr.cnes.regards.modules.storage.domain.database.AIPEntity;

/**
 *
 * Repository handling JPA representation of AIP.
 *
 * @author Sylvain Vissiere-Guerinet
 *
 */
public interface IAIPEntityRepository extends JpaRepository<AIPEntity, Long> {

    /**
     * Find a page of aips which state is the provided one
     * @return a page of aips which state is the provided one
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    Page<AIPEntity> findAllWithLockByState(AIPState state, Pageable pageable);

    /**
     * Find a page of aips which state is the provided one
     * @return a page of aips which state is the provided one
     */
    @EntityGraph("graph.aip.tags")
    Page<AIPEntity> findAllByStateIn(AIPState state, Pageable pageable);

    /**
     * Find all aips which state is one of the provided one
     * @return aips which state is one of the provided one
     */
    @EntityGraph("graph.aip.tags")
    Set<AIPEntity> findAllByStateIn(AIPState... states);

    /**
     * Find a page of aips which state is the provided one and which has been submitted after the given date and which
     * last event occurred before the given date
     * @return a page of aips which state is the provided one and which has been submitted after the given date and
     *         which last event occurred before the given date
     */
    @EntityGraph("graph.aip.tags")
    Page<AIPEntity> findAllByStateAndSubmissionDateAfterAndLastEventDateBefore(AIPState state, OffsetDateTime from,
            OffsetDateTime to, Pageable pageable);

    /**
     * Find a page of aips which state is the provided one and which has been submitted after the given date
     * @return a page of aips which state is the provided one and which has been submitted after the given date
     */
    @EntityGraph("graph.aip.tags")
    Page<AIPEntity> findAllByStateAndSubmissionDateAfter(AIPState state, OffsetDateTime from, Pageable pageable);

    /**
     * Find a page of aips which state is the provided one and which last event occurred before the given date
     * @return a page of aips which state is the provided one and which last event occurred before the given date
     */
    @EntityGraph("graph.aip.tags")
    Page<AIPEntity> findAllByStateAndLastEventDateBefore(AIPState state, OffsetDateTime from, Pageable pageable);

    /**
     * Find a page of aips which has been submitted after the given date and which last event occurred before the given
     * date
     * @return a page of aips which has been submitted after the given date and which last event occurred before the
     *         given date
     */
    @EntityGraph("graph.aip.tags")
    Page<AIPEntity> findAllBySubmissionDateAfterAndLastEventDateBefore(OffsetDateTime from, OffsetDateTime to,
            Pageable pageable);

    /**
     * Find a page of aips which has been submitted after the given date
     * @return a page of aips which has been submitted after the given date
     */
    @EntityGraph("graph.aip.tags")
    Page<AIPEntity> findAllBySubmissionDateAfter(OffsetDateTime from, Pageable pageable);

    /**
     * Retrieve a page of aips which last event occurred before the given date
     * @return a page of aips which last event occurred before the given date
     */
    @EntityGraph("graph.aip.tags")
    Page<AIPEntity> findAllByLastEventDateBefore(OffsetDateTime to, Pageable pageable);

    /**
     * Retrieve a page of aip which state is the one provided and contains the provided tags and which last event
     * occurred after the given date
     * @return a page of aip which state is the one provided and contains the provided tags and which last event
     *         occurred after the given date
     */
    @EntityGraph("graph.aip.tags")
    Page<AIPEntity> findAllByStateAndTagsInAndLastEventDateAfter(AIPState state, Set<String> tags,
            OffsetDateTime fromLastUpdateDate, Pageable pageable);

    /**
     * Retrieve a page of aip which state is the one provided and contains the provided tags
     * @return a page of aip which state is the one provided and contains the provided tags
     */
    @EntityGraph("graph.aip.tags")
    Page<AIPEntity> findAllByStateAndTagsIn(AIPState state, Set<String> tags, Pageable pageable);

    /**
     * Retrieve all aips which ip id starts with the provided string
     * @return aips respecting the constraints
     */
    @Query("from AIPEntity aip where aip.ipId LIKE :urnWithoutVersion%")
    Set<AIPEntity> findAllByIpIdStartingWith(@Param("urnWithoutVersion") String urnWithoutVersion);

    /**
     * Retrieve an aip by its ip id
     * @return requested aip
     */
    @EntityGraph("graph.aip.tags")
    Optional<AIPEntity> findOneByIpId(String ipId);

    /**
     * Retrieve all aips which ip id is one of the provided ones
     * @return all aips which respects the constraints
     */
    @EntityGraph("graph.aip.tags")
    Set<AIPEntity> findAllByIpIdIn(Collection<String> ipIds);

    /**
     * Retrieve all aips which ip id is one of the provided ones
     * No entity graph specified
     * @return a Stream
     */
    Stream<AIPEntity> findByIpIdIn(Collection<String> ipIds);

    /**
     * Retrieve all aips which are tagged by the provided tag
     * @return aips which respects the constraints
     */
    @EntityGraph("graph.aip.tags")
    Set<AIPEntity> findAllByTags(String tag);

    /**
     * Retrieve all aips which sip id is the provided one
     * @return aips which respects the constraints
     */
    @EntityGraph("graph.aip.tags")
    Set<AIPEntity> findAllBySipId(String sipIpId);

    /**
     * Retrieve page of aips which sip id is the provided one
     * @return a page of aip respecting the constraints
     */
    @EntityGraph("graph.aip.tags")
    Page<AIPEntity> findAllBySipId(String sipId, Pageable pageable);

    @EntityGraph("graph.aip.tags")
    Page<AIPEntity> findAllByStateAndLastEventDateAfter(AIPState state, OffsetDateTime fromLastUpdateDate, Pageable pageable);
}
