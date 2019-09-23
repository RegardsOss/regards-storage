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
package fr.cnes.regards.modules.storagelight.service.cache;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import fr.cnes.regards.framework.jpa.multitenant.test.AbstractMultitenantServiceTest;
import fr.cnes.regards.modules.storagelight.dao.ICacheFileRepository;
import fr.cnes.regards.modules.storagelight.domain.database.CacheFile;

/**
 * Test class for cache service.
 * @author Sébastien Binda
 */
@ActiveProfiles({ "noscheduler" })
@TestPropertySource(properties = { "spring.jpa.properties.hibernate.default_schema=storage_cache_tests",
        "regards.storage.cache.path=target/cache" })
public class CacheServiceTest extends AbstractMultitenantServiceTest {

    @Autowired
    private CacheService service;

    @Autowired
    private ICacheFileRepository repository;

    @Before
    public void init() {
        repository.deleteAll();
        simulateApplicationReadyEvent();
        runtimeTenantResolver.forceTenant(getDefaultTenant());
        service.initCacheFileSystem(getDefaultTenant());
        runtimeTenantResolver.forceTenant(getDefaultTenant());
    }

    @Test
    public void createCacheFile() throws MalformedURLException {
        // Initialize new file in cache
        String checksum = UUID.randomUUID().toString();
        OffsetDateTime expirationDate = OffsetDateTime.now().plusDays(1);
        Assert.assertFalse("File should not referenced in cache", service.getCacheFile(checksum).isPresent());
        service.addFile(checksum, 123L, new URL("file", null, "/plop/test.file.test"), expirationDate,
                        UUID.randomUUID().toString());
        Optional<CacheFile> oCf = service.getCacheFile(checksum);
        Assert.assertTrue("File should be referenced in cache", oCf.isPresent());
        Assert.assertEquals("Invalid expiration date", expirationDate, oCf.get().getExpirationDate());
        // Try to reference again the same file in cache
        OffsetDateTime newExpirationDate = OffsetDateTime.now().plusDays(2);
        service.addFile(checksum, 123L, new URL("file", null, "/plop/test.file.test"), newExpirationDate,
                        UUID.randomUUID().toString());
        oCf = service.getCacheFile(checksum);
        Assert.assertTrue("File should be referenced in cache", oCf.isPresent());
        Assert.assertEquals("Invalid expiration date", newExpirationDate, oCf.get().getExpirationDate());
    }

    @Test
    public void calculateCacheSize() throws MalformedURLException {
        OffsetDateTime expirationDate = OffsetDateTime.now().plusDays(1);
        for (int i = 0; i < 1_000; i++) {
            service.addFile(UUID.randomUUID().toString(), 10L, new URL("file", null, "/plop/test.file.test"),
                            expirationDate, UUID.randomUUID().toString());
        }
        Assert.assertEquals("Total size not valid", 10_000L, service.getCacheSizeUsedBytes().longValue());
    }

    @Test
    public void purge() throws IOException {
        OffsetDateTime expirationDate = OffsetDateTime.now().minusDays(100);
        // Create some files in cache
        for (int i = 0; i < 1_000; i++) {
            expirationDate = expirationDate.plusDays(1);
            service.addFile(UUID.randomUUID().toString(), 10L, new URL("file", null, "/plop/test.file.test"),
                            expirationDate, UUID.randomUUID().toString());
        }
        Assert.assertEquals("There should be 1000 files in cache", 1000, repository.findAll().size());
        service.purge();
        Assert.assertEquals("There should be 900 files in cache", 900, repository.findAll().size());
        // As we do not have create files on disk, all files in cache are invalid and should deleted
        service.checkDiskDBCoherence();
        runtimeTenantResolver.forceTenant(getDefaultTenant());
        Assert.assertEquals("There should be 0 files in cache", 0, repository.findAll().size());
    }
}