/*
 * LICENSE_PLACEHOLDER
 */
package fr.cnes.regards.modules.storage.plugin.staf.domain;

import java.nio.file.Path;
import java.nio.file.Paths;

import fr.cnes.regards.framework.staf.STAFArchiveModeEnum;

/**
 * Class to represent one of the parts of a file stored in STAF as multiple parts.
 * @author Sébastien Binda
 *
 */
public class PhysicalCutPartFile extends AbstractPhysicalFile {

    /**
     * Local cuted part file to store
     */
    private final Path localFilePath;

    /**
     * AbstractPhysicalFile type CUT including all cuted part of the original file.
     */
    private final PhysicalCutFile includingCutFile;

    /**
     * Index of the current cut part file.
     */
    private final int partIndex;

    /**
     * Constructor
     * @param pLocalFile {@link Path} Local file representing one part of the including uncuted file.
     * @param pIncludingCutFile {@link PhysicalCutFile} Including global file wher all parts of the uncuted file are listed.
     * @param pPartIndex Index of the current part
     * @param pSTAFArchiveName {@link String} STAF Archive name where the part is stored.
     * @param pSTAFNode {@link String} STAF Node where the part is stored.
     */
    public PhysicalCutPartFile(Path pLocalFile, PhysicalCutFile pIncludingCutFile, int pPartIndex,
            String pSTAFArchiveName, String pSTAFNode) {
        super(STAFArchiveModeEnum.CUT_PART, pSTAFArchiveName, pSTAFNode, PhysicalFileStatusEnum.TO_STORE);
        localFilePath = pLocalFile;
        includingCutFile = pIncludingCutFile;
        partIndex = pPartIndex;
        pIncludingCutFile.getRawAssociatedFiles().stream().forEach(super::addRawAssociatedFile);
    }

    @Override
    public Path getLocalFilePath() {
        return localFilePath;
    }

    @Override
    public Path getSTAFFilePath() {
        // The file path of the stored cuted part file is not calculated with the md5 of the part
        // but with the md5 of the global file with index prefix.
        return Paths.get(String.format("%s_%d", includingCutFile.getSTAFFilePath(), partIndex));
    }

    public PhysicalCutFile getIncludingCutFile() {
        return includingCutFile;
    }

    public int getPartIndex() {
        return partIndex;
    }

}