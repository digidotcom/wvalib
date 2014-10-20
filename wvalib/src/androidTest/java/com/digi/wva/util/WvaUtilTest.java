/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva.util;

import com.digi.wva.util.WvaUtil;

import junit.framework.TestCase;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

public class WvaUtilTest extends TestCase {

    /**
     * Test that dateTimeFromString is able to correctly parse a timestamp string.
     */
    public void testDateTimeFromString() {
        DateTime t1 = WvaUtil.dateTimeFromString("2014-08-12T08:00:00Z");

        // Treat t1 as UTC time, because if we don't, it will be treated
        // as local time on the test device, and these tests will likely fail.
        t1 = t1.toDateTime(DateTimeZone.UTC);

        assertEquals(2014, t1.getYear());
        assertEquals(8, t1.getMonthOfYear());
        assertEquals(12, t1.getDayOfMonth());
        assertEquals(8, t1.getHourOfDay());
        assertEquals(0, t1.getMinuteOfHour());
        assertEquals(0, t1.getSecondOfMinute());

        DateTime t2 = WvaUtil.dateTimeFromString("2014-08-12T08:00:00.000Z");
        t2 = t2.toDateTime(DateTimeZone.UTC);

        assertEquals(2014, t2.getYear());
        assertEquals(8, t2.getMonthOfYear());
        assertEquals(12, t2.getDayOfMonth());
        assertEquals(8, t2.getHourOfDay());
        assertEquals(0, t2.getMinuteOfHour());
        assertEquals(0, t2.getSecondOfMinute());
    }

    /**
     * Test that getEndpointFromUri does indeed return the last section of the given path,
     * or null if appropriate.
     */
    public void testGetEndpointFromUri() {
        assertNull(WvaUtil.getEndpointFromUri(null));
        assertNull(WvaUtil.getEndpointFromUri(""));

        assertEquals("baz", WvaUtil.getEndpointFromUri("foo/bar/baz"));

        assertEquals("foo", WvaUtil.getEndpointFromUri("foo"));
    }

    /**
     * Test that getConfigKeyFromUri is able to extract the correct key from various
     * example paths.
     */
    public void testGetConfigKeyFromUri() {
        assertEquals("canbus", WvaUtil.getConfigKeyFromUri("config/canbus/1"));

        assertNull(WvaUtil.getConfigKeyFromUri("http://192.168.100.1/ws/config"));

        assertEquals("idigi", WvaUtil.getConfigKeyFromUri("idigi"));

        assertEquals("idigi_network", WvaUtil.getConfigKeyFromUri("idigi_network/eth"));

        // Handles trailing slashes
        assertEquals("host", WvaUtil.getConfigKeyFromUri("ws/config/host/"));
    }
}
