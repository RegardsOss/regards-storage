/*
 * Copyright 2017-2019 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
package fr.cnes.regards.modules.storagelight.domain.event;

import java.util.Collection;
import java.util.Set;

import org.springframework.util.Assert;

import com.google.common.collect.Sets;

import fr.cnes.regards.framework.amqp.event.Event;
import fr.cnes.regards.framework.amqp.event.ISubscribable;
import fr.cnes.regards.framework.amqp.event.Target;
import fr.cnes.regards.modules.storagelight.domain.flow.DeletionFlowItem;
import fr.cnes.regards.modules.storagelight.domain.flow.FlowItemStatus;
import fr.cnes.regards.modules.storagelight.domain.flow.ReferenceFlowItem;
import fr.cnes.regards.modules.storagelight.domain.flow.StorageFlowItem;

/**
 * Bus message response of a request like {@link StorageFlowItem},
 * {@link ReferenceFlowItem} or {@link DeletionFlowItem}.<br/>
 * <br/>
 * FileRequestEventState :<ul>
 * <li> GRANTED : sent when the request is handled.</li>
 * <li> DENIED : sent when the request is refused.</li>
 * <li> DONE / ERROR : sent when all files in the request are handled.</li>
 * <li>
 *
 * @author Sébastien Binda
 */
@Event(target = Target.ONE_PER_MICROSERVICE_TYPE)
public class FileRequestEvent implements ISubscribable {

    /**
     * Business request identifier
     */
    private String requestId;

    /**
     * Request status
     */
    private FlowItemStatus state;

    /**
     * Request type
     */
    private FileRequestType type;

    /**
     * Files in error status
     */
    private final Set<ErrorFile> errors = Sets.newHashSet();

    public String getRequestId() {
        return requestId;
    }

    public FlowItemStatus getState() {
        return state;
    }

    /**
     * Build a message event with the given state
     * @param requestId
     * @param type
     * @param state
     * @return {@link FileRequestEvent}
     */
    public static FileRequestEvent build(String requestId, FileRequestType type, FlowItemStatus state) {
        Assert.notNull(requestId, "Request Id is mandatory");
        Assert.notNull(type, "Request type is mandatory");
        Assert.notNull(state, "Request state is mandatory");
        FileRequestEvent event = new FileRequestEvent();
        event.requestId = requestId;
        event.state = state;
        event.type = type;
        return event;
    }

    /**
     * Build an error message event with the given {@link ErrorFile}s
     * @param requestId
     * @param type
     * @param errors
     * @return {@link FileRequestEvent}
     */
    public static FileRequestEvent buildError(String requestId, FileRequestType type, Collection<ErrorFile> errors) {
        Assert.notNull(requestId, "Request Id is mandatory");
        Assert.notNull(type, "Request type is mandatory");
        FileRequestEvent event = new FileRequestEvent();
        event.requestId = requestId;
        event.state = FlowItemStatus.ERROR;
        event.errors.addAll(errors);
        event.type = type;
        return event;
    }

    public Set<ErrorFile> getErrors() {
        return errors;
    }

    public FileRequestType getType() {
        return type;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "FileRequestEvent [" + (requestId != null ? "requestId=" + requestId + ", " : "")
                + (state != null ? "state=" + state + ", " : "") + (type != null ? "type=" + type + ", " : "")
                + (errors != null ? "errors=" + errors : "") + "]";
    }

    public static class ErrorFile {

        private String checksum;

        private String storage;

        private String errorCause;

        public static ErrorFile build(String checksum, String storage, String errorCause) {
            ErrorFile err = new ErrorFile();
            err.checksum = checksum;
            err.storage = storage;
            err.errorCause = errorCause;
            return err;
        }

        public String getChecksum() {
            return checksum;
        }

        public String getStorage() {
            return storage;
        }

        public String getErrorCause() {
            return errorCause;
        }
    }

}
