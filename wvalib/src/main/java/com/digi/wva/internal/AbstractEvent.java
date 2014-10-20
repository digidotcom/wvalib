/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva.internal;

import com.digi.wva.async.EventFactory;

import org.joda.time.DateTime;


public abstract class AbstractEvent<R extends AbstractVehicleResponse<?>> {

    private EventFactory.Type type;
    private String uri, shortName, endpoint;
    private DateTime sent;
    private R response;

    /**
     * Base class for representing the contents of an event (received through the event channel).
     * @param <R> the type of data encapsulated by this event class
     */
    protected AbstractEvent(EventFactory.Type type, String uri, String endpoint, DateTime sent, String shortName, R response) {
        this.type = type;
        this.uri = uri;
        this.endpoint = endpoint;
        this.sent = sent;
        this.shortName = shortName;
        this.response = response;
    }

    /**
     * @return the event type (alarm or subscription)
     */
    public final EventFactory.Type getType() {
        return type;
    }

    /**
     * @return the URI contained in the event data
     */
    public final String getUri() {
        return uri;
    }

    /**
     * @return the last section of the URI
     */
    public String getEndpoint() {
        return endpoint;
    }

    /**
     * @return the short name of the event (the {@code "short_name"} key)
     */
    public final String getShortName() {
        return shortName;
    }

    /**
     * @return the timestamp of the event message
     */
    public final DateTime getSent() {
        return sent;
    }

    /**
     * @return the vehicle response data
     */
    public final R getResponse() {
        return response;
    }
}
