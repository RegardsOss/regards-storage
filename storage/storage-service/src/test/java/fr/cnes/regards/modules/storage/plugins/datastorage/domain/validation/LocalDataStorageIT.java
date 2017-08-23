package fr.cnes.regards.modules.storage.plugins.datastorage.domain.validation;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.transaction.BeforeTransaction;

import com.google.gson.Gson;
import fr.cnes.regards.framework.jpa.multitenant.transactional.MultitenantTransactional;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.plugins.domain.PluginConfiguration;
import fr.cnes.regards.framework.modules.plugins.domain.PluginMetaData;
import fr.cnes.regards.framework.modules.plugins.domain.PluginParameter;
import fr.cnes.regards.framework.modules.plugins.domain.PluginParametersFactory;
import fr.cnes.regards.framework.modules.plugins.service.IPluginService;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.framework.test.integration.AbstractRegardsServiceIT;
import fr.cnes.regards.modules.storage.domain.AIP;
import fr.cnes.regards.modules.storage.plugins.datastorage.IDataStorage;
import fr.cnes.regards.modules.storage.plugins.datastorage.impl.LocalDataStorage;
import fr.cnes.regards.plugins.utils.PluginUtils;

/**
 * @author Sylvain VISSIERE-GUERINET
 */
@ContextConfiguration(classes = { MockingResourceServiceConfiguration.class })
@TestPropertySource(locations = { "classpath:application-default.properties" })
@MultitenantTransactional
public class LocalDataStorageIT extends AbstractRegardsServiceIT {

    private static final Logger LOG = LoggerFactory.getLogger(LocalDataStorageIT.class);

    private static final String LOCAL_STORAGE_LABEL = "LocalDataStorageIT";

    @Autowired
    private IPluginService pluginService;

    @Autowired
    private Gson gson;

    @Autowired
    private IRuntimeTenantResolver runtimeTenantResolver;

    private PluginConfiguration localStorageConf;

    private URL baseStorageLocation;

    @BeforeTransaction
    public void setTenant() {
        runtimeTenantResolver.forceTenant(DEFAULT_TENANT);
    }

    @Before
    public void init() throws IOException, ModuleException, URISyntaxException {
        pluginService.addPluginPackage(LocalDataStorage.class.getPackage().getName());
        pluginService.addPluginPackage(IDataStorage.class.getPackage().getName());
        baseStorageLocation = new URL("file", "", System.getProperty("user.dir") + "/target/LocalDataStorageIT");
        Files.createDirectories(Paths.get(baseStorageLocation.toURI()));
        List<PluginParameter> parameters = PluginParametersFactory.build()
                .addParameter(LocalDataStorage.BASE_STORAGE_LOCATION_PLUGIN_PARAM_NAME,
                              gson.toJson(baseStorageLocation)).getParameters();
        //new plugin conf for LocalDataStorage storage into target/LocalDataStorageIT
        PluginMetaData localStorageMeta = PluginUtils
                .createPluginMetaData(LocalDataStorage.class, LocalDataStorage.class.getPackage().getName(),
                                      IDataStorage.class.getPackage().getName());
        localStorageConf = new PluginConfiguration(localStorageMeta, LOCAL_STORAGE_LABEL, parameters);
        localStorageConf = pluginService.savePluginConfiguration(localStorageConf);
    }

    @Test
    public void testStore() throws ModuleException, IOException {
        AIP aip = getAipFromFile();
        LocalDataStorage storagePlugin = pluginService.getPlugin(localStorageConf.getId());
        aip=storagePlugin.storeMetadata(aip);
        //just to be sure checksum is correct, lets compare the given checksum by our algorithm to one from a 3rd party(md5sum)
        Assert.assertEquals("b59cbcaaf597c3251d9e5bd6b892264a", aip.getChecksum());
    }

    private AIP getAipFromFile() throws IOException {
        FileReader fr = new FileReader("src/test/resources/aip_sample.json");
        BufferedReader br = new BufferedReader(fr);
        String fileLine = br.readLine();
        AIP aip = gson.fromJson(fileLine, AIP.class);
        br.close();
        fr.close();
        return aip;
    }

    @After
    public void cleanUp() throws ModuleException, URISyntaxException, IOException {
        pluginService.deletePluginConfiguration(localStorageConf.getId());
        Files.walk(Paths.get(baseStorageLocation.toURI())).sorted(Comparator.reverseOrder()).map(Path::toFile)
                .peek(System.out::println).forEach(File::delete);
    }

    @Override
    protected Logger getLogger() {
        return LOG;
    }

}
