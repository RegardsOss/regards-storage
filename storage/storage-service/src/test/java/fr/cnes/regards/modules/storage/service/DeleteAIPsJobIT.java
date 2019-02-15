package fr.cnes.regards.modules.storage.service;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.data.domain.PageRequest;

import fr.cnes.regards.framework.modules.jobs.domain.JobInfo;
import fr.cnes.regards.framework.modules.jobs.domain.JobStatus;
import fr.cnes.regards.modules.storage.domain.AIPState;
import fr.cnes.regards.modules.storage.domain.job.AddAIPTagsFilters;
import fr.cnes.regards.modules.storage.domain.job.RemovedAipsInfos;

/**
 * @author Léo Mieulet
 */
public class DeleteAIPsJobIT extends AbstractJobIT {

    @Test
    public void testDeleteAIP() throws InterruptedException {
        AddAIPTagsFilters filters = new AddAIPTagsFilters();
        Set<String> tags = new HashSet<String>();
        tags.add("first tag");
        tags.add("new tag");
        filters.setTagsToAdd(tags);
        filters.setSession(SESSION);

        // Create the job
        aipService.deleteAIPsByQuery(filters);

        // Wait until the job finishes
        JobInfo jobInfo = waitForJobFinished();

        // Check the job is finished and has a result
        Optional<JobInfo> jobInfoRefreshed = jobInfoRepo.findById(jobInfo.getId());
        Assert.assertEquals(JobStatus.SUCCEEDED, jobInfoRefreshed.get().getStatus().getStatus());
        RemovedAipsInfos result = jobInfoRefreshed.get().getResult();
        Assert.assertEquals("should not produce error", 0, result.getNbErrors());
        int nbUpdated = 20;
        Assert.assertEquals("should remove AIP", nbUpdated, result.getNbRemoved());
        Assert.assertEquals("AIP shall be mark as removed", nbUpdated,
                            aipDao.findAllByState(AIPState.DELETED, PageRequest.of(0, 10000)).getContent().size());
    }

}
