/*
 * Copyright 2017 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
package fr.cnes.regards.modules.storage.service;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.google.gson.Gson;

import fr.cnes.regards.framework.jpa.multitenant.test.AbstractMultitenantServiceTest;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.plugins.domain.PluginConfiguration;
import fr.cnes.regards.framework.modules.plugins.domain.PluginParameter;
import fr.cnes.regards.framework.modules.plugins.service.IPluginService;
import fr.cnes.regards.framework.utils.plugins.PluginParametersFactory;
import fr.cnes.regards.framework.utils.plugins.PluginUtils;
import fr.cnes.regards.modules.storage.dao.IAIPEntityRepository;
import fr.cnes.regards.modules.storage.domain.AIPCollection;
import fr.cnes.regards.modules.storage.domain.AIPState;
import fr.cnes.regards.modules.storage.domain.RejectedAip;
import fr.cnes.regards.modules.storage.plugin.allocation.strategy.DefaultAllocationStrategyPlugin;
import fr.cnes.regards.modules.storage.plugin.datastorage.local.LocalDataStorage;

/**
 * Test CDPP storage
 *
 * @author Marc Sordi
 *
 */
@TestPropertySource(properties = { "spring.jpa.properties.hibernate.default_schema=cdpp_it",
        "regards.storage.cache.path=target/cache", "regards.storage.cache.minimum.time.to.live.hours=1",
        "regards.tenants=PROJECT", "regards.tenant=PROJECT", "regards.amqp.enabled=true",
        "regards.storage.check.aip.metadata.delay=5000", "spring.jpa.show-sql=false" })
// Storage uses AMQP to synchronize storage
@ActiveProfiles({ "testAmqp" })
public class CDPPStoreTest extends AbstractMultitenantServiceTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(CDPPStoreTest.class);

    @Autowired
    private Gson gson;

    @Autowired
    private IAIPService aipService;

    @Autowired
    private IPluginService pluginService;

    @Autowired
    private IDataStorageEventHandler dseh;

    @Autowired
    private IPrioritizedDataStorageService prioritizedDataStorageService;

    @Autowired
    private IAIPEntityRepository aipRepository;

    @Before
    public void before() throws IOException {
        // Local storage
        Path localStorage = Paths.get("target", "localstorage");
        Files.createDirectories(localStorage);

        Files.createDirectories(Paths.get("target", "cache"));
        dseh.onApplicationEvent(null); // Done once
    }

    private AIPCollection getCollection(String filename) throws IOException {
        Path filepath = Paths.get("src", "test", "resources", "cdpp", filename);
        Reader json = new InputStreamReader(Files.newInputStream(filepath), Charset.forName("UTF-8"));
        return gson.fromJson(json, AIPCollection.class);
    }

    private void configure() throws ModuleException {

        // Define a local data storage
        List<PluginParameter> parameters = PluginParametersFactory.build()
                .addParameter(LocalDataStorage.BASE_STORAGE_LOCATION_PLUGIN_PARAM_NAME,
                              Paths.get("target", "localstorage").toUri().toString())
                .addParameter(LocalDataStorage.LOCAL_STORAGE_TOTAL_SPACE, 100_000_000).getParameters();

        PluginConfiguration localDataStorageConf = PluginUtils
                .getPluginConfiguration(parameters, LocalDataStorage.class, Arrays.asList("fr.cnes.regards"));
        localDataStorageConf.setIsActive(true);
        localDataStorageConf.setLabel("Local data storage");
        prioritizedDataStorageService.create(localDataStorageConf);

        // Enable default allocation strategy
        PluginConfiguration defaultAllocStrategyConf = PluginUtils
                .getPluginConfiguration(null, DefaultAllocationStrategyPlugin.class, Arrays.asList("fr.cnes.regards"));
        pluginService.savePluginConfiguration(defaultAllocStrategyConf);
    }

    @Test
    public void storeAIP() throws IOException, ModuleException, InterruptedException {

        runtimeTenantResolver.forceTenant(DEFAULT_TENANT);
        configure();

        long startTime = System.currentTimeMillis();

        AIPCollection collection = getCollection("all_cdpp_aips3.json");
        List<RejectedAip> rejectedAips = aipService.validateAndStore(collection);
        Assert.assertNotNull(rejectedAips);
        Assert.assertTrue(rejectedAips.isEmpty());
        long submitTime = System.currentTimeMillis();
        LOGGER.info("Submission time : {}", submitTime - startTime);

        // Wait until all AIP are STORED
        int storedAIP = 0;
        int expected = 305;
        int loops = 1000;
        do {
            Thread.sleep(1_000);
            storedAIP = aipRepository.findAllByStateIn(AIPState.STORED).size();
            loops--;
        } while ((storedAIP != expected) && (loops != 0));

        long stopTime = System.currentTimeMillis();
        LOGGER.info("Time elapsed : {}", stopTime - startTime);

        if (storedAIP != expected) {
            Assert.fail();
        }
    }
}