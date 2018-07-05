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
package fr.cnes.regards.modules.storage.service;

import fr.cnes.regards.modules.storage.domain.database.AIPSession;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.annotation.DirtiesContext.HierarchyMode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.MimeType;

import com.google.common.collect.Sets;

import fr.cnes.regards.framework.amqp.ISubscriber;
import fr.cnes.regards.framework.module.rest.exception.EntityInconsistentIdentifierException;
import fr.cnes.regards.framework.module.rest.exception.EntityNotFoundException;
import fr.cnes.regards.framework.module.rest.exception.EntityOperationForbiddenException;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.jobs.dao.IJobInfoRepository;
import fr.cnes.regards.framework.modules.plugins.dao.IPluginConfigurationRepository;
import fr.cnes.regards.framework.modules.plugins.domain.PluginConfiguration;
import fr.cnes.regards.framework.modules.plugins.domain.PluginMetaData;
import fr.cnes.regards.framework.modules.plugins.domain.PluginParameter;
import fr.cnes.regards.framework.modules.plugins.service.IPluginService;
import fr.cnes.regards.framework.modules.workspace.service.IWorkspaceService;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.framework.oais.ContentInformation;
import fr.cnes.regards.framework.oais.Event;
import fr.cnes.regards.framework.oais.EventType;
import fr.cnes.regards.framework.oais.urn.DataType;
import fr.cnes.regards.framework.oais.urn.EntityType;
import fr.cnes.regards.framework.oais.urn.OAISIdentifier;
import fr.cnes.regards.framework.oais.urn.UniformResourceName;
import fr.cnes.regards.framework.test.integration.AbstractRegardsTransactionalIT;
import fr.cnes.regards.framework.test.report.annotation.Requirement;
import fr.cnes.regards.framework.test.report.annotation.Requirements;
import fr.cnes.regards.framework.utils.plugins.PluginParametersFactory;
import fr.cnes.regards.framework.utils.plugins.PluginUtils;
import fr.cnes.regards.modules.notification.client.INotificationClient;
import fr.cnes.regards.modules.storage.dao.IAIPDao;
import fr.cnes.regards.modules.storage.dao.IDataFileDao;
import fr.cnes.regards.modules.storage.dao.IPrioritizedDataStorageRepository;
import fr.cnes.regards.modules.storage.domain.AIP;
import fr.cnes.regards.modules.storage.domain.AIPBuilder;
import fr.cnes.regards.modules.storage.domain.AIPCollection;
import fr.cnes.regards.modules.storage.domain.AIPState;
import fr.cnes.regards.modules.storage.domain.database.DataFileState;
import fr.cnes.regards.modules.storage.domain.database.PrioritizedDataStorage;
import fr.cnes.regards.modules.storage.domain.database.StorageDataFile;
import fr.cnes.regards.modules.storage.domain.event.AIPEvent;
import fr.cnes.regards.modules.storage.domain.plugin.IAllocationStrategy;
import fr.cnes.regards.modules.storage.domain.plugin.IDataStorage;
import fr.cnes.regards.modules.storage.domain.plugin.IOnlineDataStorage;
import fr.cnes.regards.modules.storage.plugin.allocation.strategy.DefaultMultipleAllocationStrategy;
import fr.cnes.regards.modules.storage.plugin.datastorage.local.LocalDataStorage;

/**
 * @author Sylvain VISSIERE-GUERINET
 */
@ContextConfiguration(classes = { TestConfig.class, AIPServiceIT.Config.class })
@TestPropertySource(locations = "classpath:test.properties")
@ActiveProfiles({ "testAmqp", "disableStorageTasks" })
@DirtiesContext(hierarchyMode = HierarchyMode.EXHAUSTIVE, classMode = ClassMode.BEFORE_CLASS)
public class AIPServiceIT extends AbstractRegardsTransactionalIT {

    private static final Logger LOG = LoggerFactory.getLogger(AIPServiceIT.class);

    private static final String ALLOCATION_CONF_LABEL = "AIPServiceIT_ALLOCATION";

    private static final String DATA_STORAGE_1_CONF_LABEL = "AIPServiceIT_DATA_STORAGE_LOCAL1";

    private static final String DATA_STORAGE_2_CONF_LABEL = "AIPServiceIT_DATA_STORAGE_LOCAL2";

    private static final int WAITING_TIME_MS = 1000;

    private static final String SESSION = "Session 1";

    @Autowired
    private IAIPService aipService;

    @Autowired
    private IPluginService pluginService;

    @Autowired
    private IPluginConfigurationRepository pluginRepo;

    @Autowired
    private IAIPDao aipDao;

    @Autowired
    private IDataFileDao dataFileDao;

    @Autowired
    private IJobInfoRepository jobInfoRepo;

    @Autowired
    private ISubscriber subscriber;

    @Autowired
    private IPrioritizedDataStorageService prioritizedDataStorageService;

    @Autowired
    private IRuntimeTenantResolver tenantResolver;

    @Autowired
    private IWorkspaceService workspaceService;

    @Autowired
    private IPrioritizedDataStorageRepository prioritizedDataStorageRepository;

    private AIP aip;

    private URL baseStorage1Location;

    private URL baseStorage2Location;

    private final MockEventHandler mockEventHandler = new MockEventHandler();

    private PluginConfiguration dsConfWithDeleteDisabled;

    @Before
    public void init() throws IOException, ModuleException, URISyntaxException, InterruptedException {
        tenantResolver.forceTenant(DEFAULT_TENANT);
        cleanUp();
        mockEventHandler.clear();
        subscriber.subscribeTo(AIPEvent.class, mockEventHandler);
        initDb();
    }

    private Set<AIPEvent> waitForEventsReceived(AIPState state, int nbExpectedEvents) throws InterruptedException {
        Set<AIPEvent> events = mockEventHandler.getReceivedEvents().stream().filter(e -> e.getAipState().equals(state))
                .collect(Collectors.toSet());
        int waitCount = 0;
        while ((events.size() < nbExpectedEvents) && (waitCount < 5)) {
            Thread.sleep(WAITING_TIME_MS);
            mockEventHandler.log();
            events = mockEventHandler.getReceivedEvents().stream().filter(e -> e.getAipState().equals(state))
                    .collect(Collectors.toSet());
            waitCount++;
        }
        return events;
    }

    private void initDb() throws ModuleException, IOException, URISyntaxException {
        clearDb();
        // first of all, lets get an AIP with accessible dataObjects and real checksums
        aip = getAIP();
        // second, lets storeAndCreate a plugin configuration of IDataStorage with the highest priority
        PluginMetaData dataStoMeta = PluginUtils.createPluginMetaData(LocalDataStorage.class,
                                                                      IDataStorage.class.getPackage().getName(),
                                                                      IOnlineDataStorage.class.getPackage().getName());
        baseStorage1Location = new URL("file", "", Paths.get("target/AIPServiceIT/Local1").toFile().getAbsolutePath());
        Files.createDirectories(Paths.get(baseStorage1Location.toURI()));
        List<PluginParameter> parameters = PluginParametersFactory.build()
                .addParameter(LocalDataStorage.LOCAL_STORAGE_TOTAL_SPACE, 9000000000000L)
                .addParameter(LocalDataStorage.BASE_STORAGE_LOCATION_PLUGIN_PARAM_NAME, baseStorage1Location.toString())
                .addParameter(LocalDataStorage.LOCAL_STORAGE_DELETE_OPTION, true).getParameters();
        PluginConfiguration dataStorageConf = new PluginConfiguration(dataStoMeta, DATA_STORAGE_1_CONF_LABEL,
                parameters, 0);
        dataStorageConf.setIsActive(true);
        PrioritizedDataStorage pds = prioritizedDataStorageService.create(dataStorageConf);
        Set<Long> dataStorageIds = Sets.newHashSet(pds.getId());
        // third, lets create a second local storage
        baseStorage2Location = new URL("file", "", Paths.get("target/AIPServiceIT/Local2").toFile().getAbsolutePath());
        Files.createDirectories(Paths.get(baseStorage2Location.toURI()));
        parameters = PluginParametersFactory.build()
                .addParameter(LocalDataStorage.LOCAL_STORAGE_TOTAL_SPACE, 9000000000000L)
                .addParameter(LocalDataStorage.BASE_STORAGE_LOCATION_PLUGIN_PARAM_NAME, baseStorage2Location.toString())
                .addParameter(LocalDataStorage.LOCAL_STORAGE_DELETE_OPTION, false).getParameters();
        dsConfWithDeleteDisabled = new PluginConfiguration(dataStoMeta, DATA_STORAGE_2_CONF_LABEL, parameters, 0);
        dsConfWithDeleteDisabled.setIsActive(true);
        pds = prioritizedDataStorageService.create(dsConfWithDeleteDisabled);
        dataStorageIds.add(pds.getId());
        // forth, lets create a plugin configuration for IAllocationStrategy
        PluginMetaData allocationMeta = PluginUtils
                .createPluginMetaData(DefaultMultipleAllocationStrategy.class,
                                      DefaultMultipleAllocationStrategy.class.getPackage().getName(),
                                      IAllocationStrategy.class.getPackage().getName());
        List<PluginParameter> allocationParameter = PluginParametersFactory.build()
                .addParameter(DefaultMultipleAllocationStrategy.DATA_STORAGE_IDS_PARAMETER_NAME, dataStorageIds)
                .getParameters();
        PluginConfiguration allocationConfiguration = new PluginConfiguration(allocationMeta, ALLOCATION_CONF_LABEL,
                allocationParameter, 0);
        allocationConfiguration.setIsActive(true);
        pluginService.savePluginConfiguration(allocationConfiguration);
    }

    private void storeAIP(AIP aipToStore, Boolean storeMeta) throws ModuleException, InterruptedException {
        aipService.validateAndStore(new AIPCollection(aipToStore));
        aipService.store();
        Thread.sleep(2000);
        if (storeMeta) {
            aipService.storeMetadata();
            Thread.sleep(2000);
        }
    }

    private void updateAIP(AIP aipToUpdate) throws InterruptedException, ModuleException {
        aipService.updateAip(aip.getId().toString(), aip);
        aipService.updateAipMetadata();
        Thread.sleep(3000);
    }

    @Test
    @Requirements({ @Requirement("REGARDS_DSL_STO_AIP_010"), @Requirement("REGARDS_DSL_STOP_AIP_070") })
    public void createSuccessTest() throws ModuleException, InterruptedException {
        storeAIP(aip, true);
        Set<AIPEvent> events = waitForEventsReceived(AIPState.STORED, 1);
        Assert.assertEquals("There whould be only one datastorage success event", 1, events.size());

        AIPEvent event = events.stream().findFirst().get();
        Assert.assertEquals(aip.getId().toString(), event.getIpId());
        Optional<AIP> aipFromDB = aipDao.findOneByIpId(aip.getId().toString());
        Assert.assertEquals(AIPState.STORED, aipFromDB.get().getState());
        LOG.info("AIP {} stored", aip.getId().toString());

        // Check for metadata stored
        Set<StorageDataFile> dataFiles = dataFileDao.findAllByStateAndAip(DataFileState.STORED, aip);
        Assert.assertEquals(2, dataFiles.size());
        Assert.assertNotNull("AIP metadata checksum should be stored into DB",
                             dataFiles.stream()
                                     .filter(storageDataFile -> storageDataFile.getDataType().equals(DataType.AIP))
                                     .findFirst().get().getChecksum());
        // lets check that the data has been successfully stored into the two storages and nothing else
        StorageDataFile dataFile = dataFiles.stream().filter(df -> df.getDataType().equals(DataType.RAWDATA))
                .findFirst().get();
        Assert.assertTrue("stored raw data should have only 2 urls", dataFile.getUrls().size() == 2);
        String storedLocation1 = Paths
                .get(baseStorage1Location.getPath(), dataFile.getChecksum().substring(0, 3), dataFile.getChecksum())
                .toString();
        String storedLocation2 = Paths
                .get(baseStorage2Location.getPath(), dataFile.getChecksum().substring(0, 3), dataFile.getChecksum())
                .toString();
        Assert.assertTrue(dataFile.getUrls().stream().map(url -> url.getPath()).collect(Collectors.toSet())
                .containsAll(Sets.newHashSet(storedLocation1, storedLocation2)));
        // same for the aips
        StorageDataFile aip = dataFiles.stream().filter(df -> df.getDataType().equals(DataType.AIP)).findFirst().get();
        Assert.assertTrue("stored metadata should have only 2 urls", dataFile.getUrls().size() == 2);
        storedLocation1 = Paths
                .get(baseStorage1Location.getPath(), aip.getChecksum().substring(0, 3), aip.getChecksum()).toString();
        storedLocation2 = Paths
                .get(baseStorage2Location.getPath(), aip.getChecksum().substring(0, 3), aip.getChecksum()).toString();
        Assert.assertTrue(aip.getUrls().stream().map(url -> url.getPath()).collect(Collectors.toSet())
                .containsAll(Sets.newHashSet(storedLocation1, storedLocation2)));
    }

    @Test
    public void createFailOnDataTest() throws MalformedURLException, ModuleException, InterruptedException {
        // first lets change the data location to be sure it fails
        aip.getProperties().getContentInformations()
                .toArray(new ContentInformation[aip.getProperties().getContentInformations().size()])[0].getDataObject()
                        .setUrls(Sets.newHashSet(new URL("file", "", Paths
                                .get("src/test/resources/data_that_does_not_exists.txt").toFile().getAbsolutePath())));
        storeAIP(aip, true);

        // Wait for error event
        Set<AIPEvent> events = waitForEventsReceived(AIPState.STORAGE_ERROR, 2);
        Assert.assertEquals("There should be two error events. One per storage location (multistorage).", 2,
                            events.size());
        Optional<AIP> aipFromDB = aipDao.findOneByIpId(aip.getId().toString());
        Assert.assertEquals(AIPState.STORAGE_ERROR, aipFromDB.get().getState());
        LOG.info("AIP {} is in ERROR State", aip.getId().toString());
        Set<StorageDataFile> dataFiles = dataFileDao.findAllByStateAndAip(DataFileState.ERROR, aip);
        Assert.assertEquals(1, dataFiles.size());
        StorageDataFile dataFile = dataFiles.iterator().next();
        Assert.assertFalse("The data file should contains its error", dataFile.getFailureCauses().isEmpty());
    }

    @Test
    public void createFailOnMetadataTest() throws ModuleException, InterruptedException, IOException {
        LOG.info("");
        LOG.info("START -> createFailOnMetadataTest");
        LOG.info("---------------------------------");
        LOG.info("");
        Path workspacePath = workspaceService.getMicroserviceWorkspace();
        Set<PosixFilePermission> oldPermissions = Files.getPosixFilePermissions(workspacePath);
        try {
            // Run AIP storage
            aipService.validateAndStore(new AIPCollection(aip));
            aipService.store();
            LOG.info("Waiting for storage jobs ends ...");
            Thread.sleep(2000);
            LOG.info("Waiting for storage jobs ends OK");
            // to make the process fail just on metadata storage, lets remove permissions from the workspace
            Files.setPosixFilePermissions(workspacePath, Sets.newHashSet());
            aipService.storeMetadata();
            Thread.sleep(2000);
            // Wait for error event
            Set<AIPEvent> events = waitForEventsReceived(AIPState.STORAGE_ERROR, 1);
            Assert.assertEquals("There should be one storage error event", 1, events.size());

            // Check state of AIP
            Optional<AIP> aipFromDB = aipDao.findOneByIpId(aip.getId().toString());
            Assert.assertNotEquals("Test failed because storage didn't failed! It succeeded!", AIPState.STORED,
                                   aipFromDB.get().getState());
            Assert.assertEquals(AIPState.STORAGE_ERROR, aipFromDB.get().getState());
            Set<StorageDataFile> dataFiles = dataFileDao.findAllByStateAndAip(DataFileState.STORED, aip);
            Assert.assertEquals("File should have been stored but not the metadatas", 1, dataFiles.size());
        } finally {
            // to avoid issues with following tests, lets set back the permissions
            Files.setPosixFilePermissions(workspacePath, oldPermissions);
            LOG.info("");
            LOG.info("STOP -> createFailOnMetadataTest");
            LOG.info("---------------------------------");
            LOG.info("");
        }
    }

    //
    @Test
    @Requirements({ @Requirement("REGARDS_DSL_STO_AIP_030"), @Requirement("REGARDS_DSL_STO_AIP_040") })
    public void testUpdate() throws InterruptedException, ModuleException, URISyntaxException {
        // first lets storeAndCreate the aip
        createSuccessTest();
        mockEventHandler.clear();
        // now that it is correctly created, lets say it has been updated and add a tag
        aip = aipDao.findOneByIpId(aip.getId().toString()).get();
        String newTag = "Exemple Tag For Fun";
        aip.getTags().add(newTag);
        Optional<StorageDataFile> oldDataFile = dataFileDao.findByAipAndType(aip, DataType.AIP);
        updateAIP(aip);
        Set<AIPEvent> events = waitForEventsReceived(AIPState.STORED, 1);
        Assert.assertEquals("There should be only one stored event for updated AIP", 1, events.size());
        Assert.assertTrue(oldDataFile.isPresent());
        for (URL url : oldDataFile.get().getUrls()) {
            Assert.assertFalse("The old data file should not exists anymore !" + url.getPath(),
                               Files.exists(Paths.get(url.getPath())));
        }

        AIP updatedAip = aipDao.findOneByIpId(aip.getId().toString()).get();
        Assert.assertEquals("AIP should be in storing metadata state", AIPState.STORED, updatedAip.getState());

        Assert.assertTrue("Updated AIP should contains new tag", updatedAip.getTags().contains(newTag));
        Set<Event> updateEvents = updatedAip.getHistory().stream()
                .filter(e -> e.getType().equals(EventType.UPDATE.toString())).collect(Collectors.toSet());
        Assert.assertEquals("There should be one update event in the updated aip history", 1, updateEvents.size());

        // After job is done, the new AIP metadata file should be present in local datastorage
        StorageDataFile file = dataFileDao.findByAipAndType(updatedAip, DataType.AIP).get();

        for (URL url : file.getUrls()) {
            Assert.assertTrue("The new data file should exists !" + url.getPath(),
                              Files.exists(Paths.get(url.getPath())));
        }
    }

    @Test
    @Requirements({ @Requirement("REGARDS_DSL_STO_ARC_100"), @Requirement("REGARDS_DSL_STO_AIP_310") })
    public void testPartialDeleteAip() throws InterruptedException, ModuleException, URISyntaxException {
        createSuccessTest();
        String aipIpId = aip.getId().toString();
        // lets get all the dataFile before deleting them for further verification
        Set<StorageDataFile> aipFiles = dataFileDao.findAllByAip(aip);
        aipService.deleteAip(aipIpId);

        Thread.sleep(1000);

        aipService.removeDeletedAIPMetadatas();

        // Wait for AIP deleteion
        Set<AIPEvent> events = waitForEventsReceived(AIPState.DELETED, 1);
        Assert.assertEquals("There should not been any AIP delete event ", 0, events.size());
        Assert.assertTrue("AIP should be referenced in the database", aipDao.findOneByIpId(aipIpId).isPresent());
        for (StorageDataFile df : aipFiles) {
            // As only one of the two storage system allow deletion, only one file should be deleted on disk
            if (df.getDataType().equals(DataType.AIP)) {
                for (URL fileLocation : df.getUrls()) {
                    Assert.assertTrue("AIP metadata should be on disk. As a datafile cannot be deleted metadata should never be deleted.",
                                      Files.exists(Paths.get(fileLocation.toURI())));
                }
            } else {
                for (URL fileLocation : df.getUrls()) {
                    if (fileLocation.toString().contains(baseStorage1Location.toString())) {
                        Assert.assertFalse("AIP data should not be on disk anymore",
                                           Files.exists(Paths.get(fileLocation.toURI())));
                    } else if (fileLocation.toString().contains(baseStorage2Location.toString())) {
                        Assert.assertTrue("AIP data should be on disk. The storage configuration do not allow deletion",
                                          Files.exists(Paths.get(fileLocation.toURI())));
                    } else {
                        Assert.fail("The file should not be stored in " + fileLocation.toString());
                    }
                }
            }
        }
    }

    @Test
    @Requirements({ @Requirement("REGARDS_DSL_STO_ARC_100") })
    public void testDeleteAip() throws InterruptedException, ModuleException, URISyntaxException {

        dsConfWithDeleteDisabled.getParameter(LocalDataStorage.LOCAL_STORAGE_DELETE_OPTION)
                .setValue(Boolean.TRUE.toString());
        pluginService.updatePluginConfiguration(dsConfWithDeleteDisabled);

        createSuccessTest();
        String aipIpId = aip.getId().toString();
        // lets get all the dataFile before deleting them for further verification
        Set<StorageDataFile> aipFiles = dataFileDao.findAllByAip(aip);
        aipService.deleteAip(aipIpId);

        Thread.sleep(5000);

        aipService.removeDeletedAIPMetadatas();

        // Wait for AIP deleteion
        Set<AIPEvent> events = waitForEventsReceived(AIPState.DELETED, 1);
        Assert.assertEquals("There should been only one AIP delete event ", 1, events.size());
        Assert.assertFalse("AIP should not be referenced in the database", aipDao.findOneByIpId(aipIpId).isPresent());
        for (StorageDataFile df : aipFiles) {
            // As only one of the two storage system allow deletion, only one file should be deleted on disk
            for (URL fileLocation : df.getUrls()) {
                Assert.assertFalse("AIP data should not be on disk anymore",
                                   Files.exists(Paths.get(fileLocation.toURI())));
            }
        }
    }

    @Test
    public void testDeleteErrorAip() throws InterruptedException, ModuleException, URISyntaxException {

        dsConfWithDeleteDisabled.getParameter(LocalDataStorage.LOCAL_STORAGE_DELETE_OPTION)
                .setValue(Boolean.TRUE.toString());
        pluginService.updatePluginConfiguration(dsConfWithDeleteDisabled);

        // Store a new AIP withou metadata
        storeAIP(aip, false);
        // Simulate aip state to STORE_ERROR
        aip.setState(AIPState.STORAGE_ERROR);
        aip = aipService.save(aip, false);

        // lets get all the dataFile before deleting them for further verification
        String aipIpId = aip.getId().toString();
        Set<StorageDataFile> aipFiles = dataFileDao.findAllByAip(aip);

        // Delete AIP
        aipService.deleteAip(aipIpId);
        Thread.sleep(5000);
        aipService.removeDeletedAIPMetadatas();

        // Wait for AIP deletion
        Set<AIPEvent> events = waitForEventsReceived(AIPState.DELETED, 1);
        Assert.assertEquals("There should been only one AIP delete event ", 1, events.size());
        Assert.assertFalse("AIP should not be referenced in the database", aipDao.findOneByIpId(aipIpId).isPresent());
        for (StorageDataFile df : aipFiles) {
            // All files should be deleted. But no AIP metadata as it was not stored
            Assert.assertFalse("No AIP metadata should be stored", DataType.AIP.equals(df.getDataType()));
            for (URL fileLocation : df.getUrls()) {
                Assert.assertFalse("AIP data should not be on disk anymore",
                                   Files.exists(Paths.get(fileLocation.toURI())));
            }
        }
    }

    @Test
    @Requirement("REGARDS_DSL_STO_AIP_210")
    public void testUpdatePDI() throws MalformedURLException, EntityNotFoundException,
            EntityOperationForbiddenException, EntityInconsistentIdentifierException {
        AIP aip = getAIP();
        aip.setState(AIPState.STORED);
        AIPSession aipSession = aipService.getSession(aip.getSession(), true);
        aip = aipDao.save(aip, aipSession);
        // we are going to add an update event, so lets get the old event
        int oldHistorySize = aip.getHistory().size();
        AIPBuilder updated = new AIPBuilder(aip);
        updated.addEvent(EventType.UPDATE.name(), "lets test update", OffsetDateTime.now());
        AIP preUpdateAIP = updated.build();
        AIP updatedAip = aipService.updateAip(aip.getId().toString(), preUpdateAIP);
        Assert.assertEquals("new history size should be oldhistorysize + 2", oldHistorySize + 2,
                            updatedAip.getHistory().size());
    }

    private AIP getAIP() throws MalformedURLException {

        AIPBuilder aipBuilder = new AIPBuilder(
                new UniformResourceName(OAISIdentifier.AIP, EntityType.DATA, DEFAULT_TENANT, UUID.randomUUID(), 1),
                null, EntityType.DATA, SESSION);

        String path = System.getProperty("user.dir") + "/src/test/resources/data.txt";
        aipBuilder.getContentInformationBuilder().setDataObject(DataType.RAWDATA, new URL("file", "", path), "MD5",
                                                                "de89a907d33a9716d11765582102b2e0");
        aipBuilder.getContentInformationBuilder().setSyntax("text", "description", MimeType.valueOf("text/plain"));
        aipBuilder.addContentInformation();
        aipBuilder.getPDIBuilder().setAccessRightInformation("public");
        aipBuilder.getPDIBuilder().setFacility("CS");
        aipBuilder.getPDIBuilder().addProvenanceInformationEvent(EventType.SUBMISSION.name(), "test event",
                                                                 OffsetDateTime.now());
        aipBuilder.addTags("tag");

        return aipBuilder.build();
    }

    @After
    public void cleanUp() throws URISyntaxException, IOException {
        subscriber.unsubscribeFrom(AIPEvent.class);
        subscriber.purgeQueue(AIPEvent.class, mockEventHandler.getClass());
        clearDb();
        if (baseStorage1Location != null) {
            Files.walk(Paths.get(baseStorage1Location.toURI())).sorted(Comparator.reverseOrder()).map(Path::toFile)
                    .forEach(File::delete);
        }
        if (baseStorage2Location != null) {
            Files.walk(Paths.get(baseStorage2Location.toURI())).sorted(Comparator.reverseOrder()).map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    private void clearDb() {
        jobInfoRepo.deleteAll();
        dataFileDao.deleteAll();
        aipDao.deleteAll();
        prioritizedDataStorageRepository.deleteAll();
        pluginRepo.deleteAll();
    }

    @Configuration
    static class Config {

        @Bean
        public INotificationClient notificationClient() {
            return Mockito.mock(INotificationClient.class);
        }
    }

}
