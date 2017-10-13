/*
 * LICENSE_PLACEHOLDER
 */
package fr.cnes.regards.modules.storage.plugin.datastorage.staf;

import java.net.URL;
import java.nio.file.Path;

import fr.cnes.regards.framework.staf.event.IClientCollectListener;
import fr.cnes.regards.modules.storage.domain.database.DataFile;
import fr.cnes.regards.modules.storage.plugin.datastorage.IProgressManager;
import fr.cnes.regards.modules.storage.plugin.datastorage.IWorkingSubset;

/**
 * Implementation of {@link IClientCollectListener} for {@link STAFDataStorage}.<br/>
 * For each file restored inform the {@link ProgressManager}<br/>
 * @author Sébastien Binda
 *
 */
public class STAFRetrieveListener implements IClientCollectListener {

    /**
     * Storage {@link ProgressManager}
     */
    private final IProgressManager progressManager;

    /**
     * Current {@link IWorkingSubset}
     */
    private final STAFRetrieveWorkingSubset wokingSubset;

    /**
     * Constructor
     * @param pProgressManager Storage {@link ProgressManager}
     * @param pWorkingSubset Current {@link IWorkingSubset}
     */
    public STAFRetrieveListener(IProgressManager pProgressManager, STAFRetrieveWorkingSubset pWorkingSubset) {
        super();
        progressManager = pProgressManager;
        wokingSubset = pWorkingSubset;
    }

    @Override
    public void fileRetreived(URL pSTAFFileUrl, Path pLocalFilePathRetrieved) {
        for (DataFile file : wokingSubset.getDataFiles()) {
            if (file.getUrl().equals(pSTAFFileUrl)) {
                progressManager.restoreSucceed(file, pLocalFilePathRetrieved);
            }
        }
    }

    @Override
    public void fileRetrieveError(URL pSTAFFileUrl, String pErrorMessage) {
        for (DataFile file : wokingSubset.getDataFiles()) {
            if (file.getUrl().equals(pSTAFFileUrl)) {
                progressManager.restoreFailed(file, pErrorMessage);
            }
        }
    }

}