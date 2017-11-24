package fr.cnes.regards.modules.storage.dao;

import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.assertj.core.util.Lists;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import com.google.common.collect.Sets;
import fr.cnes.regards.framework.jpa.multitenant.test.AbstractDaoTransactionalTest;
import fr.cnes.regards.framework.modules.plugins.dao.IPluginConfigurationRepository;
import fr.cnes.regards.framework.modules.plugins.domain.PluginConfiguration;
import fr.cnes.regards.framework.oais.EventType;
import fr.cnes.regards.framework.oais.InformationPackageProperties;
import fr.cnes.regards.framework.oais.builder.InformationPackagePropertiesBuilder;
import fr.cnes.regards.framework.oais.urn.DataType;
import fr.cnes.regards.framework.oais.urn.EntityType;
import fr.cnes.regards.framework.oais.urn.OAISIdentifier;
import fr.cnes.regards.framework.oais.urn.UniformResourceName;
import fr.cnes.regards.modules.storage.domain.AIP;
import fr.cnes.regards.modules.storage.domain.AIPBuilder;
import fr.cnes.regards.modules.storage.domain.database.DataFile;
import fr.cnes.regards.modules.storage.domain.database.MonitoringAggregation;

/**
 * @author Sylvain VISSIERE-GUERINET
 */
@TestPropertySource(
        properties = { "spring.jpa.properties.hibernate.default_schema=storage", "spring.application.name=storage", "spring.jmx.enabled=false" })
public class DataFileRepoIT extends AbstractDaoTransactionalTest {

    @Autowired
    private IDataFileRepository dataFileRepository;

    @Autowired
    private IAIPEntityRepository aipEntityRepository;

    private IAIPDao aipDao;

    private IDataFileDao dataFileDao;

    @Autowired
    private IPluginConfigurationRepository pluginRepo;

    private Long dataStorage2Id;

    private Long dataStorage1Id;

    private Long dataStorage3Id;

    private Long dataStorage1UsedSize = 0L;

    private Long dataStorage2UsedSize = 0L;

    private Long dataStorage3UsedSize = 0L;

    @Before
    public void init() throws MalformedURLException, NoSuchAlgorithmException {
        aipDao = new AIPDao(aipEntityRepository);
        dataFileDao = new DataFileDao(dataFileRepository, aipEntityRepository);
        // lets get some data storage configurations
        PluginConfiguration dataStorage1 = new PluginConfiguration();
        dataStorage1.setPluginId("LocalDataStorage");
        dataStorage1.setLabel("DataStorage1");
        dataStorage1.setPriorityOrder(0);
        dataStorage1.setVersion("1.0");
        dataStorage1.setInterfaceNames(Sets.newHashSet());
        dataStorage1.setParameters(Lists.newArrayList());
        dataStorage1 = pluginRepo.save(dataStorage1);
        PluginConfiguration dataStorage2 = new PluginConfiguration();
        dataStorage2.setPluginId("LocalDataStorage");
        dataStorage2.setLabel("DataStorage2");
        dataStorage2.setPriorityOrder(0);
        dataStorage2.setVersion("1.0");
        dataStorage2.setInterfaceNames(Sets.newHashSet());
        dataStorage2.setParameters(Lists.newArrayList());
        dataStorage2 = pluginRepo.save(dataStorage2);
        PluginConfiguration dataStorage3 = new PluginConfiguration();
        dataStorage3.setPluginId("LocalDataStorage");
        dataStorage3.setLabel("DataStorage3");
        dataStorage3.setPriorityOrder(0);
        dataStorage3.setVersion("1.0");
        dataStorage3.setInterfaceNames(Sets.newHashSet());
        dataStorage3.setParameters(Lists.newArrayList());
        dataStorage3 = pluginRepo.save(dataStorage3);
        // lets get some aips and dataFiles
        AIP aip1 = generateRandomAIP();
        aip1 = aipDao.save(aip1);
        List<DataFile> dataFiles = Lists.newArrayList();
        Set<DataFile> dataFilesAip = DataFile.extractDataFiles(aip1);
        for (DataFile df : dataFilesAip) {
            df.setDataStorageUsed(dataStorage1);
            dataStorage1Id = dataStorage1.getId();
            dataStorage1UsedSize += df.getFileSize();
        }
        dataFiles.addAll(dataFilesAip);
        AIP aip2 = generateRandomAIP();
        aip2 = aipDao.save(aip2);
        dataFilesAip = DataFile.extractDataFiles(aip2);
        for (DataFile df : dataFilesAip) {
            df.setDataStorageUsed(dataStorage2);
            dataStorage2Id = dataStorage2.getId();
            dataStorage2UsedSize += df.getFileSize();
        }
        dataFiles.addAll(dataFilesAip);
        AIP aip3 = generateRandomAIP();
        aip3 = aipDao.save(aip3);
        dataFilesAip = DataFile.extractDataFiles(aip3);
        for (DataFile df : dataFilesAip) {
            df.setDataStorageUsed(dataStorage3);
            dataStorage3Id = dataStorage3.getId();
            dataStorage3UsedSize += df.getFileSize();
        }
        dataFiles.addAll(dataFilesAip);
        dataFileDao.save(dataFiles);
    }

    @Test
    public void testMonitoringAggregation() {
        Collection<MonitoringAggregation> monitoringAggregations = dataFileRepository.getMonitoringAggregation();
        for(MonitoringAggregation agg: monitoringAggregations) {
            if(agg.getDataStorageUsedId().equals(dataStorage1Id)) {
                Assert.assertTrue(agg.getUsedSize().equals(dataStorage1UsedSize));
            }
            if(agg.getDataStorageUsedId().equals(dataStorage2Id)) {
                Assert.assertTrue(agg.getUsedSize().equals(dataStorage2UsedSize));
            }
            if(agg.getDataStorageUsedId().equals(dataStorage3Id)) {
                Assert.assertTrue(agg.getUsedSize().equals(dataStorage3UsedSize));
            }
        }
    }

    public AIP generateRandomAIP() throws NoSuchAlgorithmException, MalformedURLException {

        UniformResourceName ipId = new UniformResourceName(OAISIdentifier.AIP,
                                                           EntityType.COLLECTION,
                                                           "tenant",
                                                           UUID.randomUUID(),
                                                           1);
        String sipId = String.valueOf(generateRandomString(new Random(), 40));

        // Init AIP builder
        AIPBuilder aipBuilder = new AIPBuilder(ipId, sipId, EntityType.DATA);

        return aipBuilder.build(generateRandomInformationPackageProperties(ipId));
    }

    public InformationPackageProperties generateRandomInformationPackageProperties(UniformResourceName ipId)
            throws NoSuchAlgorithmException, MalformedURLException {

        // Init Information object builder
        InformationPackagePropertiesBuilder ippBuilder = new InformationPackagePropertiesBuilder();
        // Content information
        generateRandomContentInformations(ippBuilder);
        // PDI
        ippBuilder.getPDIBuilder().addProvenanceInformationEvent(EventType.SUBMISSION.name(),
                                                                 "addition of this aip into our beautiful system!",
                                                                 OffsetDateTime.now());
        // - ContextInformation
        ippBuilder.getPDIBuilder().addTags(generateRandomTags(ipId));
        // - Provenance
        ippBuilder.getPDIBuilder().setFacility("TestPerf");
        // - Access right
        Random random = new Random();
        int maxStringLength = 20;
        ippBuilder.getPDIBuilder().setAccessRightInformation(generateRandomString(random, maxStringLength));

        return ippBuilder.build();
    }

    private void generateRandomContentInformations(InformationPackagePropertiesBuilder ippBuilder)
            throws NoSuchAlgorithmException, MalformedURLException {
        int listMaxSize = 5;
        Random random = new Random();
        int listSize = random.nextInt(listMaxSize) + 1;
        for (int i = 0; i < listSize; i++) {
            ippBuilder.getContentInformationBuilder().setDataObject(DataType.OTHER,
                                                                    new URL("ftp://bla"),
                                                                    null,
                                                                    "SHA1",
                                                                    sha1("blahblah"),
                                                                    new Long((new Random()).nextInt(10000000)));
            ippBuilder.getContentInformationBuilder()
                    .setSyntaxAndSemantic("NAME", "SYNTAX_DESCRIPTION", "application/name", "DESCRIPTION");
            ippBuilder.addContentInformation();
        }
    }

    private String generateRandomString(Random random, int maxStringLength) {
        String possibleLetters = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOPQRSTUVWYXZ";
        int stringSize = random.nextInt(maxStringLength) + 1;
        char[] string = new char[stringSize];
        for (int j = 0; j < stringSize; j++) {
            string[j] = possibleLetters.charAt(random.nextInt(possibleLetters.length()));
        }
        return new String(string);
    }

    private String sha1(String input) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA1");
        byte[] result = digest.digest(input.getBytes());
        StringBuffer sb = new StringBuffer();
        for (byte element : result) {
            sb.append(Integer.toString((element & 0xff) + 0x100, 16).substring(1));
        }
        return sb.toString();
    }

    /**
     * generate random tags (length and content) but always have at least one tag which is the aip IP ID
     * @param ipId
     * @return
     */
    private String[] generateRandomTags(UniformResourceName ipId) {
        int listMaxSize = 15;
        int tagMaxSize = 10;
        Random random = new Random();
        int listSize = random.nextInt(listMaxSize) + 1;
        String[] tags = new String[listSize];
        tags[0] = ipId.toString();
        for (int i = 1; i < listSize; i++) {
            tags[i] = generateRandomString(random, tagMaxSize);
        }
        return tags;
    }
}