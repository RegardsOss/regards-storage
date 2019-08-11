package fr.cnes.regards.modules.storagelight.service.file.reference;

import java.time.OffsetDateTime;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Service;

import com.google.common.collect.Sets;

import fr.cnes.regards.framework.jpa.multitenant.transactional.MultitenantTransactional;
import fr.cnes.regards.modules.storagelight.dao.IFileCopyRequestRepository;
import fr.cnes.regards.modules.storagelight.domain.FileRequestStatus;
import fr.cnes.regards.modules.storagelight.domain.database.request.FileCopyRequest;
import fr.cnes.regards.modules.storagelight.domain.event.FileReferenceEvent;
import fr.cnes.regards.modules.storagelight.domain.plugin.INearlineStorageLocation;

/**
 * Service to handle {@link FileCopyRequest}s.
 * Those requests are created when a file reference need to be restored physically thanks to an existing {@link INearlineStorageLocation} plugin.
 *
 * @author Sébastien Binda
 */
@Service
@MultitenantTransactional
public class FileCopyRequestService {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(FileCopyRequestService.class);
	
	private static final int NB_REFERENCE_BY_PAGE = 1000;
	
	@Autowired
	private IFileCopyRequestRepository copyRepository;

    @Autowired
    private FileReferenceService fileRefService;
	
    public void scheduleAvailabilityRequests(FileRequestStatus status) {
    	Pageable page = PageRequest.of(0, NB_REFERENCE_BY_PAGE, Direction.ASC, "id");
    	Page<FileCopyRequest> pageResp = null;
    	OffsetDateTime expDate = OffsetDateTime.now().plusDays(1);
    	do {
    		String fileCacheRequestId = UUID.randomUUID().toString();
    		Set<String> checksums = Sets.newHashSet();
    		pageResp = copyRepository.findByStatus(status, page);
    		for (FileCopyRequest request : pageResp.getContent()) {
    			checksums.add(request.getMetaInfo().getChecksum());
    			request.setFileCacheRequestId(fileCacheRequestId);
    			request.setStatus(FileRequestStatus.PENDING);
    		}
    		if  (!checksums.isEmpty()) {
        		fileRefService.makeAvailable(checksums, expDate, fileCacheRequestId);
        	}
    		page = page.next();
    	} while (pageResp.hasNext());
    }
    
    public void handleSuccess(FileCopyRequest request) {
    	LOGGER.info("[COPY SUCCESS] File {} successfully copied in {} storage location");
    	// Delete the copy request
    	copyRepository.delete(request);
    }
    
    public void handleError(FileCopyRequest request, String errorCause) {
    	// Update copy request to error status
    	request.setStatus(FileRequestStatus.ERROR);
    	request.setErrorCause(errorCause);
    	update(request);
    }
    
    public Optional<FileCopyRequest> search(FileReferenceEvent event) {
    	Optional<FileCopyRequest> req = Optional.empty();
    	Iterator<String> it;
    	switch (event.getType()) {
	        case AVAILABLE:
	        case AVAILABILITY_ERROR:
	        	it = event.getRequestIds().iterator();
	        	while (it.hasNext() && !req.isPresent()) {
	        		req = copyRepository.findByFileCacheRequestId(it.next());
	        	}
	        	break;
	        case STORED:
	        case STORE_ERROR:
	        	it = event.getRequestIds().iterator();
	        	while (it.hasNext() && !req.isPresent()) {
	        		req = copyRepository.findByFileStorageRequestId(it.next());
	        	}
	        	break;
	        case DELETED_FOR_OWNER:
	        case FULLY_DELETED:
	        case DELETION_ERROR:
	        default:
	            break;
    	}
    	return req;
    }
    
    public FileCopyRequest update(FileCopyRequest request) {
    	return copyRepository.save(request);
    }
}
