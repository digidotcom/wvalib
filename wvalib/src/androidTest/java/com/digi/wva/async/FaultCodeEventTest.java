/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva.async;

import com.digi.wva.async.EventFactory;
import com.digi.wva.async.FaultCodeCommon;
import com.digi.wva.async.FaultCodeEvent;

import junit.framework.TestCase;

/**
  * Created by mwadsten on 8/14/2014.
  */
 public class FaultCodeEventTest extends TestCase {
    public void testConstructor() {
        String uri = "vehicle/dtc/can0_inactive/ecu1";
        FaultCodeEvent e = new FaultCodeEvent(
                EventFactory.Type.SUBSCRIPTION, uri, "ecu1", null, "can0_active~ecu1~dtcsub", null);

        assertEquals(uri, e.getUri());
        assertEquals("ecu1", e.getEndpoint());
        assertNull(e.getSent());
        assertEquals("can0_active~ecu1~dtcsub", e.getShortName());
        assertNull(e.getResponse());

        assertEquals(FaultCodeCommon.Bus.CAN0, e.getBus());
        assertEquals(FaultCodeCommon.FaultCodeType.INACTIVE, e.getMessageType());
        assertEquals("ecu1", e.getEcu());
    }
 }
