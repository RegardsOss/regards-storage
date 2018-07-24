/*
 * Copyright 2018 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
package fr.cnes.regards.modules.storage.service.job;

import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.jobs.domain.AbstractJob;
import fr.cnes.regards.framework.modules.jobs.domain.JobParameter;
import fr.cnes.regards.framework.modules.jobs.domain.exception.JobParameterInvalidException;
import fr.cnes.regards.framework.modules.jobs.domain.exception.JobParameterMissingException;
import fr.cnes.regards.modules.storage.dao.AIPQueryGenerator;
import fr.cnes.regards.modules.storage.dao.IAIPDao;
import fr.cnes.regards.modules.storage.domain.AIP;
import fr.cnes.regards.modules.storage.domain.database.AIPSession;
import fr.cnes.regards.modules.storage.domain.job.AIPQueryFilters;
import fr.cnes.regards.modules.storage.domain.job.RemovedAipsInfos;
import fr.cnes.regards.modules.storage.service.IAIPService;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Add or remove tags to several AIPs, inside a job.
 * @author Léo Mieulet
 */
public class DeleteAIPsJob extends AbstractJob<RemovedAipsInfos> {

    /**
     * Class logger
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(DeleteAIPsJob.class);

    /**
     * Job parameter name for the AIP User request id to use
     */
    public static final String FILTER_PARAMETER_NAME = "query";

    @Autowired
    private IAIPService aipService;

    @Autowired
    private IAIPDao aipDao;

    private Map<String, JobParameter> parameters;

    private AtomicInteger nbError;

    private AtomicInteger nbEntityRemoved;

    private Integer nbEntity;

    @Override
    public void run() {
        AIPQueryFilters tagFilter = parameters.get(FILTER_PARAMETER_NAME).getValue();
        AIPSession aipSession = aipService.getSession(tagFilter.getSession(), false);
        Set<AIP> aips = aipDao.findAll(AIPQueryGenerator.search(tagFilter.getState(), tagFilter.getFrom(), tagFilter.getTo(), tagFilter.getTags(), aipSession, tagFilter.getAipIds(), tagFilter.getAipIdsExcluded()));
        nbError = new AtomicInteger(0);
        nbEntityRemoved = new AtomicInteger(0);
        nbEntity = aips.size();
        aips.forEach(aip -> {
            try {
                aipService.deleteAip(aip);
                nbEntityRemoved.incrementAndGet();
            } catch (ModuleException e) {
                // Exception thrown while removing AIP
                LOGGER.error(e.getMessage(), e);
                nbError.incrementAndGet();
            }
        });
        RemovedAipsInfos infos = new RemovedAipsInfos(nbError, nbEntityRemoved);
        this.setResult(infos);
    }


    @Override
    public boolean needWorkspace() {
        return false;
    }

    @Override
    public int getCompletionCount() {
        if (nbError.get() + nbEntityRemoved.get() == 0) {
            return 0;
        }
        return (int) Math.floor(100 * (nbError.get() + nbEntityRemoved.get()) / nbEntity);
    }

    @Override
    public void setParameters(Map<String, JobParameter> parameters) throws JobParameterMissingException, JobParameterInvalidException {
        checkParameters(parameters);
        this.parameters = parameters;
    }

    private void checkParameters(Map<String, JobParameter> parameters) throws JobParameterInvalidException, JobParameterMissingException {
        JobParameter filterParam = parameters.get(FILTER_PARAMETER_NAME);
        if (filterParam == null) {

            JobParameterMissingException e = new JobParameterMissingException(
                    String.format("Job %s: parameter %s not provided", this.getClass().getName(), FILTER_PARAMETER_NAME));
            logger.error(e.getMessage(), e);
            throw e;
        }
        // Check if the filterParam can be correctly parsed, depending of its type
        if (!(filterParam.getValue() instanceof AIPQueryFilters)) {
            JobParameterInvalidException e = new JobParameterInvalidException(
                    String.format("Job %s: cannot read the parameter %s", this.getClass().getName(), FILTER_PARAMETER_NAME));
            logger.error(e.getMessage(), e);
            throw e;
        }
    }
}
