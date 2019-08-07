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
package fr.cnes.regards.modules.storagelight.domain.flow;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Set;

import org.springframework.util.Assert;

import com.google.common.collect.Sets;

import fr.cnes.regards.framework.amqp.event.Event;
import fr.cnes.regards.framework.amqp.event.ISubscribable;
import fr.cnes.regards.framework.amqp.event.Target;

/**
 * @author sbinda
 *
 */
@Event(target = Target.ONE_PER_MICROSERVICE_TYPE)
public class AvailabilityFileRefFlowItem implements ISubscribable {

    private final Set<String> checksums = Sets.newHashSet();

    private final OffsetDateTime expirationDate;

    private final String requestId;

    public AvailabilityFileRefFlowItem(Collection<String> checksums, OffsetDateTime expirationDate, String requestId) {
        super();
        Assert.notNull(checksums, "Checksums is mandatory");
        Assert.notEmpty(checksums, "Checksums is mandatory");
        Assert.notNull(expirationDate, "Expiration date is mandatory");
        Assert.notNull(requestId, "Request id is mandatory");
        this.checksums.addAll(checksums);
        this.expirationDate = expirationDate;
        this.requestId = requestId;
    }

    public Set<String> getChecksums() {
        return checksums;
    }

    public OffsetDateTime getExpirationDate() {
        return expirationDate;
    }

    public String getRequestId() {
        return requestId;
    }

}
