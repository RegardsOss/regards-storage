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
package fr.cnes.regards.modules.storage.service.cache.job;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;

import fr.cnes.regards.framework.modules.jobs.domain.AbstractJob;
import fr.cnes.regards.framework.modules.jobs.domain.exception.JobRuntimeException;
import fr.cnes.regards.modules.storage.service.cache.CacheService;

/**
 * JOB to run verification of cache files coherence.
 * <ul>
 * <li> Does files in database always exists on disk ?</li>
 * <li> Does files on disk always exists in database ?</li>
 * </ul>
 *
 * @author Sébastien Binda
 *
 */
public class CacheVerificationJob extends AbstractJob<Void> {

    @Autowired
    private CacheService cacheService;

    @Override
    public void run() {
        try {
            cacheService.checkDiskDBCoherence();
        } catch (IOException e) {
            logger.error("Error during cache coherence verification. Cause : {}", e.getMessage());
            throw new JobRuntimeException(e.getMessage(), e);
        }
    }
}
