/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva.internal;

import junit.framework.TestCase;

import com.digi.wva.async.VehicleDataEvent;
import com.digi.wva.async.VehicleDataResponse;
import com.digi.wva.async.EventFactory.Type;
import com.digi.wva.async.EventFactory;
import com.digi.wva.test_auxiliary.JsonFactory;

import org.json.JSONException;
import org.json.JSONObject;

public class EventFactoryTest extends TestCase {
    VehicleDataEvent subscriptionEventTest = null;
    VehicleDataEvent alarmEventTest = null;
    JsonFactory jFactory = new JsonFactory();

    protected void setUp() throws Exception {
        subscriptionEventTest = (VehicleDataEvent) EventFactory.fromTCP(jFactory.data());
        alarmEventTest = (VehicleDataEvent) EventFactory.fromTCP(jFactory.alarm());
        super.setUp();
    }

    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testFromTCP() throws Exception {
        AbstractEvent<?> e1 = EventFactory.fromTCP(jFactory.data());
        AbstractEvent<?> e2 = EventFactory.fromTCP(jFactory.alarm());
        AbstractEvent<?> e3 = EventFactory.fromTCP(jFactory.junk());
        AbstractEvent<?> e4 = EventFactory.fromTCP(jFactory.faultCodeDataWithBadUri());

        //Should return an object on correct input, null on bad input
        assertNotNull(e1);
        assertNotNull(e2);
        assertNull(e3);
        assertNull(e4);
    }

    public void testGetType() {
        Type type1 = subscriptionEventTest.getType();
        assertEquals(EventFactory.Type.SUBSCRIPTION, type1);

        Type type2 = alarmEventTest.getType();
        assertEquals(EventFactory.Type.ALARM, type2);
    }

    public void testVehicleDataResponse() {
        VehicleDataResponse resp1 = subscriptionEventTest.getResponse();
        assertEquals(4.3, resp1.getValue());

        VehicleDataResponse resp2 = alarmEventTest.getResponse();
        assertEquals(4.3, resp2.getValue());
    }

    public void testFromTCP_MissingKey() throws JSONException {
        // Should throw a JSONException if the value key is missing.
        JSONObject data = jFactory.data();

        // Get rid of the key-value pair associated with the data value
        data.getJSONObject("data").remove("baz");

        try {
            EventFactory.fromTCP(data);
            fail("EventFactory.fromTCP on data missing its value key should have thrown!");
        } catch (JSONException e) {
            assertTrue(e.getMessage().contains("does not contain key"));
        }
    }

    public void testFromTCP_OtherVehicleData() throws JSONException {
        // otherVehicleDataEvent() yields a vehicle/ignition event, where the "ignition" value is
        // simply "on", in contrast with normal vehicle data events, where the value is a JSON
        // object containing a timestamp and value.
        VehicleDataEvent e = (VehicleDataEvent) EventFactory.fromTCP(jFactory.otherVehicleDataEvent());
        assertNotNull(e);

        VehicleDataResponse resp = e.getResponse();
        assertNotNull(resp);

        assertEquals("on", resp.getRawValue());
        // Cannot parse "on" as a double value, so on .getValue() we should get null
        assertNull(resp.getValue());
        // We reuse the event timestamp as the data timestamp in these cases, so these DateTime
        // objects should be the same.
        assertEquals(resp.getTime(), e.getSent());
    }

    public void testGetEndpoint() {
        String endpoint1 = subscriptionEventTest.getEndpoint();
        assertEquals("baz", endpoint1);

        String endpoint2 = alarmEventTest.getEndpoint();
        assertEquals("baz", endpoint2);
    }

    public void testGetUri() {
        String uri1 = subscriptionEventTest.getUri();
        assertEquals("vehicle/data/baz", uri1);

        String uri2 = alarmEventTest.getUri();
        assertEquals("vehicle/data/baz", uri2);
    }
}
