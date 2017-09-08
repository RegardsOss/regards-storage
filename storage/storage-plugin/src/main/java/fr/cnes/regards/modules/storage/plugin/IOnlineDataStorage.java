package fr.cnes.regards.modules.storage.plugin;

import java.io.IOException;
import java.io.InputStream;

import fr.cnes.regards.framework.modules.plugins.annotations.PluginInterface;
import fr.cnes.regards.modules.storage.domain.database.DataFile;

/**
 * @author Sylvain VISSIERE-GUERINET
 */
@PluginInterface(description = "Contract to respect by any ONLINE data storage plugin")
public interface IOnlineDataStorage<T extends IWorkingSubset>  extends IDataStorage<T>{


    /**
     * Do the retreive action for the given {@link DataFile}
     * @param data DataFile to retrieve
     */
//    OutputStream retrieve(T workingSubset, ProgressManager progressManager);
    InputStream retrieve(DataFile data) throws IOException;

}