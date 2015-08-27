/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva.async;

import org.joda.time.DateTime;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

import com.digi.wva.internal.AbstractEvent;
import com.digi.wva.util.WvaUtil;

/** Container for static factory function {@link #fromTCP} and common event enumeration(s). */
public final class EventFactory {
    private EventFactory() {}

    /**
     * This method is the preferred way of creating an event object. It takes
     * a JSON object from the {@link com.digi.wva.internal.EventChannel EventChannel}'s object queue
     * and transforms it into an event consumable by anything registered to the client.
     *
     * Any {@link JSONObject} can be sent through this method, but only objects
     * of the correct format will produce a non-null object.
     *
     * @param obj JSONObject to be used in constructing a new Event object
     * @return a new event object constructed from <b>obj</b>'s data. The correct
     *             event class will be selected based on the values in <b>obj</b>
     *
     * @throws JSONException if <b>obj</b> is of a recognized format but has bad values
     */
    public static AbstractEvent<?> fromTCP(JSONObject obj) throws JSONException {
        Type type;
        String uri, shortName;
        DateTime sent;

        if (!obj.isNull("alarm")) {
            // This is an alarm event
            obj = obj.getJSONObject("alarm");
            type = Type.ALARM;
        } else if (!obj.isNull("data")) {
            // This is a subscription event.
            type = Type.SUBSCRIPTION;
            obj = obj.getJSONObject("data");
        } else {
            // Object is malformed, or in an unrecognized format.
            Log.e("EventFactory", "Couldn't parse event object " + obj.toString());
            return null;
        }

        uri = obj.getString("uri");
        shortName = obj.getString("short_name");
        sent = WvaUtil.dateTimeFromString(obj.getString("timestamp"));

        // The "endpoint" here might be an actual vehicle data endpoint (e.g. EngineSpeed) or just
        // the last element in the URI (e.g. ignition).
        String endpoint = WvaUtil.getEndpointFromUri(uri);

        if (!obj.has(endpoint)) {
            // This would be a very strange case indeed - unless something changes in the events
            // API that would allow this to happen.
            throw new JSONException(
                    String.format("Event with uri \"%s\" does not contain key \"%s\"", uri, endpoint));
        }

        JSONObject valueObj = obj.optJSONObject(endpoint);
        if (valueObj == null) {
            // The value in this event is not a JSON object. Presumably this means
            // the event corresponds to some other URI data (e.g. vehicle/ignition).
            // To handle this case, we'll change that key to "value" so that we can treat
            // `obj` the same as a regular vehicle data event - we'll use the event timestamp
            // as the data timestamp, and the value as, well, the value.
            obj.put("value", obj.remove(endpoint));
            valueObj = obj;
        }

        AbstractEvent<?> evt;

        // Decide which type of vehicle response this must be, based on the URI
        if (uri.startsWith(FaultCodeCommon.FAULT_CODE_BASE)) {
            // Fault code
            FaultCodeResponse response = new FaultCodeResponse(valueObj);

            evt = new FaultCodeEvent(type, uri, endpoint, sent, shortName, response);
        } else if (uri.startsWith("vehicle/")) {
            // Vehicle data, or some other URI under vehicle/
            VehicleDataResponse response = new VehicleDataResponse(valueObj);

            evt = new VehicleDataEvent(type, uri, endpoint, sent, shortName, response);
        } else {
            // Unrecognized
            Log.w("EventFactory", "Unrecognized event type. URI: " + uri);
            return null;
        }

        return evt;
    }

    /**
     * Enumeration of the general types of events that can be sent by the WVA.
     *
     * <p>Values: {@code ALARM}, {@code SUBSCRIPTION}.</p>
     */
    public static enum Type {
        /** Indicates that a configured alarm condition has been matched. */
        ALARM,
        /** A periodic update of vehicle status. */
        SUBSCRIPTION;
    }
}