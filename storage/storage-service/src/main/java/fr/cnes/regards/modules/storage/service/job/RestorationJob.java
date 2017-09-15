package fr.cnes.regards.modules.storage.service.job;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.jobs.domain.JobParameter;
import fr.cnes.regards.framework.modules.jobs.domain.exception.JobParameterInvalidException;
import fr.cnes.regards.framework.modules.jobs.domain.exception.JobParameterMissingException;
import fr.cnes.regards.framework.modules.plugins.domain.PluginConfiguration;
import fr.cnes.regards.modules.storage.domain.database.DataFile;
import fr.cnes.regards.modules.storage.plugin.IDataStorage;
import fr.cnes.regards.modules.storage.plugin.INearlineDataStorage;
import fr.cnes.regards.modules.storage.plugin.IWorkingSubset;

/**
 * @author Sylvain VISSIERE-GUERINET
 */
public class RestorationJob extends AbstractStoreFilesJob {

    public static final String DESTINATION_PATH_PARAMETER_NAME = "destination";

    @Override
    protected Map<String, JobParameter> checkParameters(Set<JobParameter> parameters)
            throws JobParameterMissingException, JobParameterInvalidException {
        Map<String, JobParameter> jobParamMap = super.checkParameters(parameters);
        //lets see if destination has been given or not
        JobParameter oldDataFiles;
        if (((oldDataFiles = jobParamMap.get(DESTINATION_PATH_PARAMETER_NAME)) == null) || !(oldDataFiles
                .getValue() instanceof Path)) {
            JobParameterMissingException e = new JobParameterMissingException(
                    String.format(PARAMETER_MISSING, this.getClass().getName(), Path.class.getName(),
                                  DESTINATION_PATH_PARAMETER_NAME));
            LOG.error(e.getMessage(), e);
            throw e;
        }
        return jobParamMap;
    }

    @Override
    protected void doRun(Map<String, JobParameter> parameterMap) {
        // lets instantiate the plugin to use
        PluginConfiguration confToUse = parameterMap.get(PLUGIN_TO_USE_PARAMETER_NAME).getValue();
        Path destination = parameterMap.get(DESTINATION_PATH_PARAMETER_NAME).getValue();
        try {
            INearlineDataStorage storagePlugin = pluginService.getPlugin(confToUse.getId());
            // now that we have the plugin instance, lets retrieve the aip from the job parameters and ask the plugin to do the storage
            IWorkingSubset workingSubset = parameterMap.get(WORKING_SUB_SET_PARAMETER_NAME).getValue();
            // before storage on file system, lets update the DataFiles by setting which data storage is used to store them.
            for (DataFile data : workingSubset.getDataFiles()) {
                data.setDataStorageUsed(confToUse);
            }
            storagePlugin.retrieve(workingSubset, destination, progressManager);
        } catch (ModuleException e) {
            //throwing new runtime allows us to make the job fail.
            throw new RuntimeException(e);
        }
    }

}
