package fr.cnes.regards.modules.storage.service;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.MimeType;

import com.google.common.collect.Sets;

import fr.cnes.regards.framework.jpa.utils.RegardsTransactional;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.plugins.domain.PluginConfiguration;
import fr.cnes.regards.framework.modules.plugins.domain.PluginMetaData;
import fr.cnes.regards.framework.modules.plugins.domain.PluginParameter;
import fr.cnes.regards.framework.modules.plugins.service.IPluginService;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.framework.oais.EventType;
import fr.cnes.regards.framework.oais.urn.DataType;
import fr.cnes.regards.framework.oais.urn.EntityType;
import fr.cnes.regards.framework.oais.urn.OAISIdentifier;
import fr.cnes.regards.framework.oais.urn.UniformResourceName;
import fr.cnes.regards.framework.test.integration.AbstractRegardsServiceTransactionalIT;
import fr.cnes.regards.framework.utils.plugins.PluginParametersFactory;
import fr.cnes.regards.framework.utils.plugins.PluginUtils;
import fr.cnes.regards.modules.notification.client.INotificationClient;
import fr.cnes.regards.modules.storage.dao.IAIPDao;
import fr.cnes.regards.modules.storage.dao.IDataFileDao;
import fr.cnes.regards.modules.storage.domain.AIP;
import fr.cnes.regards.modules.storage.domain.AIPBuilder;
import fr.cnes.regards.modules.storage.domain.database.PrioritizedDataStorage;
import fr.cnes.regards.modules.storage.domain.database.StorageDataFile;
import fr.cnes.regards.modules.storage.domain.plugin.IAllocationStrategy;
import fr.cnes.regards.modules.storage.domain.plugin.IDataStorage;
import fr.cnes.regards.modules.storage.domain.plugin.IOnlineDataStorage;
import fr.cnes.regards.modules.storage.domain.plugin.WorkingSubsetWrapper;
import fr.cnes.regards.modules.storage.plugin.allocation.strategy.DefaultAllocationStrategyPlugin;
import fr.cnes.regards.modules.storage.plugin.datastorage.local.LocalDataStorage;
import fr.cnes.regards.modules.storage.plugin.datastorage.local.LocalWorkingSubset;
import fr.cnes.regards.modules.templates.dao.ITemplateRepository;
import fr.cnes.regards.modules.templates.service.ITemplateService;
import fr.cnes.regards.modules.templates.service.TemplateServiceConfiguration;

/**
 * Set of visual tests on the result, allows to be sure that the default template is interpreted with no major issues
 *
 * @author Sylvain VISSIERE-GUERINET
 */
@ContextConfiguration(classes = { TestConfig.class, TemplateIT.Config.class })
@TestPropertySource(locations = "classpath:test.properties")
@RegardsTransactional
@ActiveProfiles({ "testAmqp", "disableStorageTasks" })
public class TemplateIT extends AbstractRegardsServiceTransactionalIT {

    private static final String DATA_STORAGE_CONF_LABEL = "DataStorage_TemplateIT";

    private static final String ALLO_CONF_LABEL = "Allo_TemplateIT";

    private static final Logger LOGGER = LoggerFactory.getLogger(TemplateIT.class);

    @Autowired
    private ITemplateService templateService;

    @Autowired
    private IPluginService pluginService;

    @Autowired
    private IPrioritizedDataStorageService prioritizedDataStorageService;

    @Autowired
    private IRuntimeTenantResolver runtimeTenantResolver;

    @Autowired
    private ITemplateRepository templateRepository;

    @Autowired
    private IAIPDao aipDao;

    @Autowired
    private IDataFileDao dataFileDao;

    @Test
    public void testNotSubsetted() throws ModuleException, MalformedURLException {
        runtimeTenantResolver.forceTenant(DEFAULT_TENANT);
        Map<String, Object> dataMap = new HashMap<>();
        PluginMetaData dataStoMeta = PluginUtils.createPluginMetaData(LocalDataStorage.class,
                                                                      IDataStorage.class.getPackage().getName(),
                                                                      IOnlineDataStorage.class.getPackage().getName());
        URL baseStorageLocation = new URL("file", "",
                Paths.get("target/AIPServiceIT/Local2").toFile().getAbsolutePath());
        List<PluginParameter> parameters = PluginParametersFactory.build()
                .addParameter(LocalDataStorage.LOCAL_STORAGE_TOTAL_SPACE, 9000000000000L)
                .addParameter(LocalDataStorage.BASE_STORAGE_LOCATION_PLUGIN_PARAM_NAME, baseStorageLocation.toString())
                .addParameter(LocalDataStorage.LOCAL_STORAGE_DELETE_OPTION, false).getParameters();
        PluginConfiguration dataStorageConf = new PluginConfiguration(dataStoMeta, DATA_STORAGE_CONF_LABEL, parameters,
                0);
        dataStorageConf.setIsActive(true);
        PrioritizedDataStorage prioritizedDataStorage = prioritizedDataStorageService.create(dataStorageConf);

        //lets simulate as in the code, so lets create a workingSubsetWrapper full of rejected data files
        WorkingSubsetWrapper<LocalWorkingSubset> workingSubsetWrapper = new WorkingSubsetWrapper<>();
        AIP aip = aipDao.save(getAIP());
        Set<StorageDataFile> dataFiles = StorageDataFile.extractDataFiles(aip);
        dataFiles = Sets.newHashSet(dataFileDao.save(dataFiles));
        Iterator<StorageDataFile> dataFilesIter = dataFiles.iterator();
        int i = 0;
        while (dataFilesIter.hasNext()) {
            i++;
            workingSubsetWrapper.addRejectedDataFile(dataFilesIter.next(), "Test" + i);
        }
        dataMap.put("dataFilesMap", workingSubsetWrapper.getRejectedDataFiles());
        dataMap.put("dataStorage", pluginService.getPluginConfiguration(prioritizedDataStorage.getId()));
        // lets use the template service to get our message
        SimpleMailMessage email = templateService
                .writeToEmail(TemplateServiceConfiguration.NOT_SUBSETTED_DATA_FILES_CODE, dataMap);
        Assert.assertNotEquals(templateRepository
                .findOneByCode(TemplateServiceConfiguration.NOT_SUBSETTED_DATA_FILES_CODE).get().getContent(),
                               email.getText());
        LOGGER.info(email.getText());
    }

    @Test
    public void testNotDispatched() throws ModuleException, MalformedURLException {
        runtimeTenantResolver.forceTenant(DEFAULT_TENANT);
        Map<String, Object> dataMap = new HashMap<>();
        PluginMetaData AlloMeta = PluginUtils.createPluginMetaData(DefaultAllocationStrategyPlugin.class,
                                                                   IAllocationStrategy.class.getPackage().getName());
        PluginConfiguration alloConf = new PluginConfiguration(AlloMeta, ALLO_CONF_LABEL, new ArrayList<>(), 0);
        alloConf.setIsActive(true);
        alloConf = pluginService.savePluginConfiguration(alloConf);

        AIP aip = aipDao.save(getAIP());
        Set<StorageDataFile> dataFiles = StorageDataFile.extractDataFiles(aip);
        dataFiles = Sets.newHashSet(dataFileDao.save(dataFiles));
        dataMap.put("dataFiles", dataFiles);
        dataMap.put("allocationStrategy", alloConf);
        // lets use the template service to get our message
        SimpleMailMessage email = templateService
                .writeToEmail(TemplateServiceConfiguration.NOT_DISPATCHED_DATA_FILES_CODE, dataMap);
        Assert.assertNotEquals(templateRepository
                .findOneByCode(TemplateServiceConfiguration.NOT_DISPATCHED_DATA_FILES_CODE).get().getContent(),
                               email.getText());
        LOGGER.info(email.getText());
    }

    private AIP getAIP() throws MalformedURLException {

        AIPBuilder aipBuilder = new AIPBuilder(
                new UniformResourceName(OAISIdentifier.AIP, EntityType.DATA, DEFAULT_TENANT, UUID.randomUUID(), 1),
                null, EntityType.DATA);

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

    @Configuration
    static class Config {

        @Bean
        public INotificationClient notificationClient() {
            return Mockito.mock(INotificationClient.class);
        }
    }
}