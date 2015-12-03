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
 * Encapsulation of an event containing fault code data.
 */
public class FaultCodeEvent extends AbstractEvent<FaultCodeResponse> {
    private final FaultCodeCommon.Bus bus;
    private final FaultCodeCommon.FaultCodeType type;
    private final String ecu;

    /**
     * Constructor of a fault code event. Takes in values that are sent as part of
     * each object in the event channel.
     *
     * @param type the event type (alarm or subscription)
     * @param uri the URI ({@code "uri"} field)
     * @param endpoint the last section of the URI
     * @param sent the timestamp of the message (not of the value)
     * @param shortName the short name (the {@code "short_name" field})
     * @param response a representation of the event data/value
     */
    public FaultCodeEvent(EventFactory.Type type, String uri, String endpoint, Date sent,
                          String shortName,
                          FaultCodeResponse response) {
        super(type, uri, endpoint, sent, shortName, response);

        // Parse the URI into the bus, message type and ECU name
        String subUri = uri.substring(FaultCodeCommon.FAULT_CODE_BASE.length());
        String[] split = subUri.split("/");

        String[] busAndType = split[0].split("_");

        bus = FaultCodeCommon.Bus.valueOf(busAndType[0].toUpperCase());
        this.type = FaultCodeCommon.FaultCodeType.valueOf(busAndType[1].toUpperCase());
        ecu = split[1];
    }

    /**
     * Gets the CAN bus that reported this data
     * @return the CAN bus that reported this data
     */
    public FaultCodeCommon.Bus getBus() {
        return bus;
    }

    /**
     * Gets the message type of this data
     * @return the message type (active/inactive) of this data
     */
    public FaultCodeCommon.FaultCodeType getMessageType() {
        return type;
    }

    /**
     * Gets the name of the ECU that reported this data
     * @return the name of the ECU that reported this data
     */
    public String getEcu() {
        return ecu;
    }
}
