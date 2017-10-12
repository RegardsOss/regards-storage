/*
 * LICENSE_PLACEHOLDER
 */
package fr.cnes.regards.modules.storage.service;

import java.util.Set;

import org.assertj.core.util.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.cnes.regards.framework.amqp.domain.IHandler;
import fr.cnes.regards.framework.amqp.domain.TenantWrapper;
import fr.cnes.regards.modules.storage.domain.event.DataFileEvent;
import fr.cnes.regards.modules.storage.domain.event.DataFileEventState;

public class TestDataStorageEventHandler implements IHandler<DataFileEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(RestoreJobEventHandler.class);

    private final Set<String> restoredChecksum = Sets.newHashSet();

    @Override
    public synchronized void handle(TenantWrapper<DataFileEvent> pWrapper) {
        if (pWrapper.getContent().getState().equals(DataFileEventState.AVAILABLE)) {
            restoredChecksum.add(pWrapper.getContent().getChecksum());
        }
    }

    public synchronized Set<String> getRestoredChecksum() {
        return restoredChecksum;
    }

    public synchronized void reset() {
        restoredChecksum.clear();
    }

}