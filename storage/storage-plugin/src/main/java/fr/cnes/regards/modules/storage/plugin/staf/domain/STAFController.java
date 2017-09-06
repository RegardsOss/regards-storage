package fr.cnes.regards.modules.storage.plugin.staf.domain;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import fr.cnes.regards.framework.file.utils.CutFileUtils;
import fr.cnes.regards.framework.staf.ArchiveAccessModeEnum;
import fr.cnes.regards.framework.staf.STAFArchiveModeEnum;
import fr.cnes.regards.framework.staf.STAFConfiguration;
import fr.cnes.regards.framework.staf.STAFException;
import fr.cnes.regards.framework.staf.STAFService;
import fr.cnes.regards.modules.storage.plugin.staf.domain.protocol.STAFUrlFactory;

public class STAFController {

    /**
     * Class logger
     */
    private static final Logger LOG = LoggerFactory.getLogger(STAFController.class);

    /**
     * URL STAF Protocole name
     */
    public static final String STAF_PROTOCOLE = "staf";

    /**
     * STAF temporary directory used to handle cut and tar files.
     */
    private static final String TMP_DIRECTORY = "tmp";

    /**
     * STAF global configuration
     */
    private final STAFConfiguration stafConfiguration;

    /**
     * Instance of STAFService for the configured STAFArchive
     */
    private final STAFService stafService;

    /**
     * Path to the local workspace of the STAF System. Use to process files before transfert to STAF System.
     */
    private final Path localWorkspace;

    /**
     * List of prepared files to store into STAF System.
     */
    private final Set<AbstractPhysicalFile> filesToArchive = Sets.newHashSet();

    /**
     * List of prepared tar to store into STAF System.
     */
    private final Set<PhysicalTARFile> tarsToArchive = Sets.newHashSet();

    /**
     * Controller to handle TAR files.
     */
    private final TARController tarController;

    /**
     * Constructor
     * @param pStafConfiguration {@link STAFConfiguration} Global STAF Configuration
     * @param pLocalWorkspace {@link Path} STAF local workspace
     * @param pSTAFService {@link STAFService} STAF Service to call STAF Commands.
     * @throws IOException Thrown if workspace is not available.
     */
    public STAFController(STAFConfiguration pStafConfiguration, Path pLocalWorkspace, STAFService pSTAFService)
            throws IOException {
        super();
        stafConfiguration = pStafConfiguration;
        localWorkspace = pLocalWorkspace;
        stafService = pSTAFService;
        // Check if workspace exists and is writable/readable
        if (!Files.exists(localWorkspace)) {
            Files.createDirectories(localWorkspace);
            if (!Files.isReadable(localWorkspace)) {
                throw new IOException(
                        String.format("[STAF] Local workspace %s is not readable", localWorkspace.toString()));
            }
            if (!Files.isWritable(localWorkspace)) {
                throw new IOException(
                        String.format("[STAF] Local workspace %s is not writable", localWorkspace.toString()));
            }
        }
        tarController = new TARController(pStafConfiguration, pLocalWorkspace);
    }

    /**
     * Prepare the list of file to archive into the STAF for the given files
     * @param pFileToArchivePerStafNode
     * @param pMode
     * @return
     */
    public Set<AbstractPhysicalFile> prepareFilesToArchive(Map<String, Set<Path>> pFileToArchivePerStafNode,
            STAFArchiveModeEnum pMode) {

        this.clearPreparedFiles();
        for (String stafNode : pFileToArchivePerStafNode.keySet()) {
            for (Path fileToArchive : pFileToArchivePerStafNode.get(stafNode)) {
                try {
                    this.prepareFileToArchive(fileToArchive, stafNode, pMode);
                } catch (STAFException e) {
                    LOG.error("[STAF] Error preparing file for STAF transfer. " + e.getMessage());
                    LOG.debug(e.getMessage(), e);
                }
            }
        }
        return this.getAllPreparedFilesToArchive();
    }

    /**
     * Store given pFilesToArchiveMap in STAF.<br/>
     * For each element of pFilesToArchiveMap :<br/>
     *  - key : is the local path of the file to archive<br/>
     *  - value : is the staf file path to store.<br/>
     * @param pFilesToArchiveMap files to store
     * @param pReplaceMode replace file in STAF if already exists ?
     * @return list of successfuly stored local file path.
     * @throws STAFException staf store error
     */
    public Set<AbstractPhysicalFile> doArchivePreparedFiles(boolean pReplaceMode) throws STAFException {
        Set<String> archivedFiles = Sets.newHashSet();

        // Create map bewteen localFile to archive and destination file path into STAF
        Map<String, String> localFileToArchiveMap = Maps.newHashMap();
        this.getAllPreparedFilesToArchive().forEach(stafFile -> {
            if (PhysicalFileStatusEnum.TO_STORE.equals(stafFile.getStatus())) {
                try {
                    if ((stafFile.getLocalFilePath() != null) && (stafFile.getSTAFFilePath() != null)) {
                        localFileToArchiveMap.put(stafFile.getLocalFilePath().toString(),
                                                  stafFile.getSTAFFilePath().toString());
                    } else {
                        stafFile.setStatus(PhysicalFileStatusEnum.ERROR);
                        LOG.warn("Undefined file to archive for origine(local)={} and destination(STAF)={}",
                                 stafFile.getLocalFilePath(), stafFile.getSTAFFilePath());
                    }
                } catch (fr.cnes.regards.modules.storage.plugin.staf.domain.STAFException e) {
                    LOG.error(e.getMessage(), e);
                }
            }
        });

        stafService.connectArchiveSystem(ArchiveAccessModeEnum.ARCHIVE_MODE);
        try {
            archivedFiles = stafService.archiveFiles(localFileToArchiveMap, "/", pReplaceMode);

            archivedFiles.forEach(archivedFile ->
            // For each file to store, check if the file has really been stored and set the status to STORED.
            // @formatter:off
            this.getAllPreparedFilesToArchive()
                .stream()
                .filter(f -> PhysicalFileStatusEnum.TO_STORE.equals(f.getStatus()))
                .filter(f -> archivedFile.equals(f.getLocalFilePath().toString()))
                .forEach(f -> {
                    // Set status to STORED
                    f.setStatus(PhysicalFileStatusEnum.STORED);
                    // Delete local temporary files
                    deleteTemporaryFiles(f);
                }));
            // @formatter:on

        } finally {
            stafService.disconnectArchiveSystem(ArchiveAccessModeEnum.ARCHIVE_MODE);
        }

        // handle special case of TAR PENDING. Status is not STORED but LOCALY_STORED.
        // @formatter:off
        tarsToArchive
            .stream()
            .filter(tar -> PhysicalFileStatusEnum.PENDING.equals(tar.getStatus()))
            .forEach(tar -> {
                LOG.info("[STAF] Working TAR {} not big enought for transfert to STAF System. This TAR is localy stored waiting for new files.",tar.getLocalTarDirectory());
                tar.setStatus(PhysicalFileStatusEnum.LOCALY_STORED);
            });
        // @formatter:on

        // Return all Physical file stored
        // @formatter:off
        return this.getAllPreparedFilesToArchive().stream()
                .filter(file -> PhysicalFileStatusEnum.STORED.equals(file.getStatus()))
                .collect(Collectors.toSet());
        // @formatter:on
    }

    /**
     * Return a mapping between raw files given by the preparation step and STAF URL of archived files by the sotre step.
     * @return {@link Map}<{@link Path}, {@link URL}> (key : Path of the raw file to archive, value : URL of STAF file)
     */
    public Map<Path, URL> getRawFilesArchived() {

        Map<Path, URL> rawFilesStored = Maps.newHashMap();
        // @formatter:off
        this.getAllPreparedFilesToArchive()
            .stream()
            .filter(f ->
                PhysicalFileStatusEnum.STORED.equals(f.getStatus()) ||
                PhysicalFileStatusEnum.LOCALY_STORED.equals(f.getStatus()))
            .forEach(file -> {
                // Create urls for the given stored file.
                // NORMAL : 1 File stored -> 1 URL
                // TAR : 1 File stored -> X URL (one per file in TAR)
                // CUT : X Files stored -> 1 URL
                try {
                    Map<Path, URL> urls = STAFUrlFactory.getSTAFFullURLs(file);
                    rawFilesStored.putAll(urls);
                } catch (MalformedURLException | fr.cnes.regards.modules.storage.plugin.staf.domain.STAFException e) {
                    // Error creating file URL
                    LOG.error("Error during STAF URL creation for staf file {}",file.getLocalFilePath(),e);
                }
            });
        // @formatter:on

        return rawFilesStored;

    }

    /**
     * Return all {@link AbstractPhysicalFile} prepared for storage into STAF System.
     * @return {@link AbstractPhysicalFile}s
     */
    public Set<AbstractPhysicalFile> getAllPreparedFilesToArchive() {
        Set<AbstractPhysicalFile> allFilesToArchive = Sets.newHashSet();
        allFilesToArchive.addAll(filesToArchive);
        allFilesToArchive.addAll(tarsToArchive);
        return allFilesToArchive;
    }

    /**
     * Allow to clear prepared files to handle a new preparation.
     */
    public void clearPreparedFiles() {
        // Clear already calculated files to archive
        filesToArchive.clear();
        tarsToArchive.clear();
    }

    /**
     * The archive mode to store file in STAF is calculated with the file size.
     * The modes are {@link STAFArchiveModeEnum}
     * @param pFileSize int
     * @return
     */
    public STAFArchiveModeEnum getFileArchiveMode(Long pFileSize) {

        if (pFileSize < stafConfiguration.getMinFileSize()) {
            return STAFArchiveModeEnum.TAR;
        }

        if (pFileSize > stafConfiguration.getMaxFileSize()) {
            return STAFArchiveModeEnum.CUT;
        }

        return STAFArchiveModeEnum.NORMAL;
    }

    /**
     * Prepare the given file to archive into the staf.
     * @param pFileToArchivePerStafNode
     * @param pSTAFNode
     * @param pMode
     * @throws STAFException
     */
    private void prepareFileToArchive(Path pFileToArchive, String pSTAFNode, STAFArchiveModeEnum pMode)
            throws STAFException {
        try {

            // 1. Check file existance
            if (!Files.exists(pFileToArchive) || !Files.isReadable(pFileToArchive)) {
                String message = String.format("[STAF] File %s to archive, is not accessible",
                                               pFileToArchive.toString());
                LOG.error(message);
                throw new STAFException(message);
            }

            // 2. Manage file transformation if needed before staf storage
            switch (pMode) {
                case CUT:
                    filesToArchive.addAll(cutFile(pFileToArchive, pSTAFNode).getCutedFileParts());
                    break;
                case TAR:
                    tarController.addFileToTar(pFileToArchive, tarsToArchive,
                                               stafService.getStafArchive().getArchiveName(), pSTAFNode);
                    break;
                case NORMAL:
                    filesToArchive.add(new PhysicalNormalFile(pFileToArchive, pFileToArchive,
                            stafService.getStafArchive().getArchiveName(), pSTAFNode));
                    break;
                default:
                    throw new STAFException(String.format("Unhandle Archive mode %s", pMode.toString()));

            }

        } catch (IOException | STAFTarException e) {
            LOG.error("[STAF] Error preparing file {}", pFileToArchive, e);
        }
    }

    /**
     * Cur a file which is too big to be archive in one part into STAF System.
     * @param pPhysicalFileToArchive
     * @param pStafNode
     * @return
     * @throws IOException
     */
    private PhysicalCutFile cutFile(Path pPhysicalFileToArchive, String pSTAFNode) throws IOException {

        // 1. Create cut temporary directory into workspace
        Path tmpCutDirectory = Paths.get(localWorkspace.toString(), TMP_DIRECTORY,
                                         pPhysicalFileToArchive.getFileName().toString());
        if (!tmpCutDirectory.toFile().exists()) {
            tmpCutDirectory.toFile().mkdirs();
        }

        // 3. Do cut files
        Set<File> cutedLocalFiles = CutFileUtils.cutFile(pPhysicalFileToArchive.toFile(), tmpCutDirectory.toString(),
                                                         stafConfiguration.getMaxFileSize());
        LOG.debug("[STAF] Number of cuted files : {} for file {}", cutedLocalFiles.size(),
                  pPhysicalFileToArchive.toString());

        // 4. Create cut Physical file object to return
        PhysicalCutFile physicalCutFile = new PhysicalCutFile(pPhysicalFileToArchive,
                stafService.getStafArchive().getArchiveName(), pSTAFNode);
        physicalCutFile.addRawAssociatedFile(pPhysicalFileToArchive);
        int partIndex = 0;
        for (File cutedFile : cutedLocalFiles) {
            Path cutedFilePath = Paths.get(cutedFile.getPath());
            PhysicalCutPartFile cutFilePart = new PhysicalCutPartFile(cutedFilePath, pPhysicalFileToArchive,
                    physicalCutFile, partIndex, stafService.getStafArchive().getArchiveName(), pSTAFNode);
            physicalCutFile.addCutedPartFile(cutFilePart);
            partIndex++;
        }

        return physicalCutFile;
    }

    private void deleteTemporaryFiles(AbstractPhysicalFile pFile) {
        switch (pFile.getArchiveMode()) {
            case CUT_PART:
                if (pFile.getLocalFilePath().getParent().toFile().exists()) {
                    try {
                        Files.walk(pFile.getLocalFilePath().getParent()).map(Path::toFile)
                                .sorted((o1, o2) -> -o1.compareTo(o2)).forEach(File::delete);
                    } catch (IOException e) {
                        LOG.error("[STAF] Error deleting file (CUT MODE", e);
                    }
                }
                break;
            case NORMAL:
            case TAR:
                if (pFile.getLocalFilePath().toFile().exists() && pFile.getLocalFilePath().startsWith(localWorkspace)) {
                    try {
                        Files.delete(pFile.getLocalFilePath());
                    } catch (IOException e) {
                        LOG.error("[STAF] Error deleting file (TAR MODE", e);
                    }
                }
                break;
            case CUT:
            default:
                // Nothing to do
                break;

        }
    }

}
