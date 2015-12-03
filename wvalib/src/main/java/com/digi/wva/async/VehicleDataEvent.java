/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva.async;

import com.digi.wva.internal.AbstractEvent;

import java.util.Date;



/**
 * Encapsulation of an event containing vehicle data.
 */
public class VehicleDataEvent extends AbstractEvent<VehicleDataResponse> {
    /**
     * Constructor of a vehicle data event. Takes in values that are sent as part of
     * each object in the event channel.
     *
     * @param type the event type
     * @param uri the uri
     * @param endpoint the last section of the URI
     * @param sent the timestamp of the message (not of the value)
     * @param shortName the shortname
     * @param response a representation of the event data/value
     */
    public VehicleDataEvent(EventFactory.Type type, String uri, String endpoint, Date sent, String shortName,
                               VehicleDataResponse response) {
        super(type, uri, endpoint, sent, shortName, response);
    }
}
