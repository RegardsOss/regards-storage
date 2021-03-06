/*
 * Copyright 2017-2020 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
package fr.cnes.regards.modules.storage.service.file;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.stream.IntStream;

import fr.cnes.regards.framework.urn.DataType;
import io.vavr.control.Try;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import fr.cnes.regards.framework.module.rest.exception.EntityNotFoundException;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.jobs.domain.JobInfo;
import fr.cnes.regards.modules.storage.domain.DownloadableFile;
import fr.cnes.regards.modules.storage.domain.database.CacheFile;
import fr.cnes.regards.modules.storage.domain.database.DownloadToken;
import fr.cnes.regards.modules.storage.domain.database.FileReference;
import fr.cnes.regards.modules.storage.domain.database.request.FileCacheRequest;
import fr.cnes.regards.modules.storage.domain.database.request.FileRequestStatus;
import fr.cnes.regards.modules.storage.domain.exception.NearlineFileNotAvailableException;
import fr.cnes.regards.modules.storage.service.AbstractStorageTest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test class
 *
 * @author Sébastien Binda
 */
@ActiveProfiles({ "noschedule" })
@TestPropertySource(properties = { "spring.jpa.properties.hibernate.default_schema=storage_download_tests",
        "regards.storage.cache.path=target/cache" }, locations = { "classpath:application-test.properties" })
public class FileDownloadServiceTest extends AbstractStorageTest {

    @Before
    @Override
    public void init() throws ModuleException {
        super.init();
    }

    @Test
    public void downloadFileOnlineAndNotNearline() throws ExecutionException, InterruptedException, ModuleException {
        FileReference fileRef = this.generateRandomStoredNearlineFileReference();
        fileRef = this.generateStoredFileReference(fileRef.getMetaInfo().getChecksum(),
                                                   fileRef.getOwners().stream().findFirst().get(),
                                                   fileRef.getMetaInfo().getFileName(),
                                                   ONLINE_CONF_LABEL_WITHOUT_DELETE,
                                                   Optional.empty(),
                                                   Optional.empty());
        downloadService.downloadFile(fileRef.getMetaInfo().getChecksum());
        // there should not be any exception as the file is at the same time online and nearline
    }

    @Test
    public void downloadFileReferenceOnline()
            throws ModuleException, InterruptedException, ExecutionException, FileNotFoundException {
        downloadService.downloadFile(this.generateRandomStoredOnlineFileReference().getMetaInfo().getChecksum());
    }

    @Test
    public void downloadFileReferenceOffLine()
            throws ModuleException, InterruptedException, ExecutionException, FileNotFoundException {
        FileReference fileRef = this
                .referenceFile(UUID.randomUUID().toString(), "owner", null, "file.test", "somewhere").get();
        Try<Callable<DownloadableFile>> result = Try.of(() -> downloadService.downloadFile(fileRef.getMetaInfo().getChecksum()));
        assertTrue("File should not be available for download as it is not handled by a known storage location plugin", result.isFailure());
        assertTrue(result.getCause() instanceof ModuleException);
    }

    @Test
    public void downloadFileReferenceNearline()
            throws ModuleException, InterruptedException, ExecutionException, IOException {
        FileReference fileRef = this.generateRandomStoredNearlineFileReference();

        Try<DownloadableFile> result =
            Try.of(() -> downloadService.downloadFile(fileRef.getMetaInfo().getChecksum()))
                .mapTry(Callable::call);
        assertTrue("File should not be available for download as it is not online", result.isFailure());
        assertTrue(result.getCause() instanceof NearlineFileNotAvailableException);

        // A cache request should be created
        Optional<FileCacheRequest> oReq = fileCacheRequestService.search(fileRef.getMetaInfo().getChecksum());
        Assert.assertTrue("FileCacheRequest should be created", oReq.isPresent());
        assertEquals("FileCacheRequest should be created to retrieve file from nearline storage",
            NEARLINE_CONF_LABEL,
            oReq.get().getStorage());
        assertEquals("FileCacheRequest should be created to retrieve file from nearline storage",
            FileRequestStatus.TO_DO,
            oReq.get().getStatus());
        Collection<JobInfo> jobs = fileCacheRequestService.scheduleJobs(FileRequestStatus.TO_DO);
        runAndWaitJob(jobs);

        Optional<CacheFile> oCf = cacheService.search(fileRef.getMetaInfo().getChecksum());
        Assert.assertTrue("File should be present in cache", oCf.isPresent());
        assertEquals("File should be present in cache",
            cacheService.getFilePath(fileRef.getMetaInfo().getChecksum()),
            oCf.get().getLocation().getPath().toString());

        // Now the file is available in cache try to download it again.
        result = Try.of(() -> downloadService.downloadFile(fileRef.getMetaInfo().getChecksum()))
            .mapTry(Callable::call);
        assertTrue(result.isSuccess());
        DownloadableFile file = result.get();
        Assert.assertNotNull("File should be downloadable", file);
        Assert.assertNotNull("File should be downloadable", file.getFileInputStream());
        assertEquals("File should be downloadable with a valid name",
            fileRef.getMetaInfo().getFileName(),
            file.getFileName());
        assertEquals("File should be downloadable with a valid mime type",
            fileRef.getMetaInfo().getMimeType(),
            file.getMimeType());
        file.getFileInputStream().close();
    }

    @Test(expected = NearlineFileNotAvailableException.class)
    public void download_without_cache() throws InterruptedException, ExecutionException, EntityNotFoundException,
            NearlineFileNotAvailableException {
        FileReference fileRef = this.generateRandomStoredNearlineFileReference();
        try {
            downloadService.download(fileRef);
        } finally {
            Assert.assertTrue("A cache request should be done for the near line file to download",
                              fileCacheRequestService.search(fileRef.getMetaInfo().getChecksum()).isPresent());
        }
    }

    @Test
    public void download_with_cache()
            throws InterruptedException, ExecutionException, EntityNotFoundException, IOException,
            NearlineFileNotAvailableException {
        FileReference fileRef = this.generateRandomStoredNearlineFileReference();
        this.simulateFileInCache(fileRef.getMetaInfo().getChecksum());
        InputStream stream = downloadService.download(fileRef);
        Assert.assertNotNull(stream);
        stream.close();
    }

    @Test
    public void testGenerateDownloadUrl() throws ModuleException {
        Assert.assertTrue(downloadTokenRepo.findAll().isEmpty());
        downloadService.generateDownloadUrl(UUID.randomUUID().toString());
        assertEquals(1, downloadTokenRepo.findAll().size());

        downloadTokenRepo.save(DownloadToken.build("plop", "pllip", OffsetDateTime.now().minusHours(2)));
        assertEquals(2, downloadTokenRepo.findAll().size());
        downloadService.purgeTokens();
        assertEquals(1, downloadTokenRepo.findAll().size());

    }

    @Test
    public void downloadFileTypeDependsOnFileReferenceType() {
        Random r = new Random();
        DataType[] typesCache = DataType.values();
        IntStream.range(0, 100)
            .forEach(i -> Try.run(() -> {
                DataType type = typesCache[r.nextInt(typesCache.length)];
                FileReference fileRef = generateStoredFileReference(
                    UUID.randomUUID().toString(),
                    "someone",
                    "file.test",
                    ONLINE_CONF_LABEL,
                    Optional.empty(),
                    Optional.of(type.name())
                );

                DownloadableFile dlFile =
                    Try.of(() -> downloadService.downloadFile(fileRef.getMetaInfo().getChecksum()))
                        .mapTry(Callable::call)
                        .get();

                assertTrue(
                    type.equals(DataType.RAWDATA)
                        ? dlFile instanceof FileDownloadService.QuotaLimitedDownloadableFile
                        : dlFile instanceof FileDownloadService.StandardDownloadableFile
                );
            }));
    }
}
