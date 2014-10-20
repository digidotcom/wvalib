/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva.internal;


import com.digi.wva.async.FaultCodeEvent;
import com.digi.wva.async.VehicleDataEvent;
import com.digi.wva.test_auxiliary.JsonFactory;

import junit.framework.TestCase;

import org.json.JSONObject;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;


public class EventDispatcherTest extends TestCase {
    EventDispatcher dispatcher;
    VehicleData mockVehicleData;
    FaultCodes mockFaultCodes;

    EventChannel mockChannel;
    BlockingQueue<JSONObject> mockingQueue;
    JsonFactory jsonFactory;

    @SuppressWarnings("unchecked")
    protected void setUp() throws Exception {
        super.setUp();
        jsonFactory = new JsonFactory();
        mockingQueue = (BlockingQueue<JSONObject>) mock(BlockingQueue.class);

        mockChannel = mock(EventChannel.class);
        when(mockChannel.getQueue()).thenReturn(mockingQueue);

        mockVehicleData = mock(VehicleData.class);
        mockFaultCodes = mock(FaultCodes.class);
    }

    public void testStartStop() throws Exception{
        dispatcher = new EventDispatcher(mockChannel, mockVehicleData, mockFaultCodes);
        dispatcher.start();
        dispatcher.stopThread();
        dispatcher.join();
        assertTrue(!dispatcher.isAlive());
    }

    public void testData() throws Exception {
        final CyclicBarrier dataAvailable = new CyclicBarrier(2);

        when(mockingQueue.take()).thenReturn(jsonFactory.data())
                                 .thenAnswer(
        new Answer<JSONObject>() {
            @Override
            public JSONObject answer(InvocationOnMock invocation) throws Throwable {
                dataAvailable.await();
                return jsonFactory.faultCodeData();
            }
        }).thenAnswer(new Answer<JSONObject>() {
            @Override
            public JSONObject answer(InvocationOnMock invocation) throws Throwable {
                dataAvailable.await();
                return jsonFactory.junk();
            }
             }).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                dataAvailable.await();
                // This call will never return
                return null;
            }
        });

        dispatcher = new EventDispatcher(mockChannel, mockVehicleData, mockFaultCodes);
        dispatcher.start();

        ArgumentCaptor<VehicleDataEvent> arg = ArgumentCaptor.forClass(VehicleDataEvent.class);
        ArgumentCaptor<FaultCodeEvent> arg2 = ArgumentCaptor.forClass(FaultCodeEvent.class);

        // Wait until the dispatcher gets back to take() the second time, since that
        // means we handled the first value pulled off.
        dataAvailable.await(3, TimeUnit.SECONDS);
        verify(mockVehicleData).updateCachedVehicleData(arg.capture());
        VehicleDataEvent capturedVehicleData = arg.getValue();

        // Wait until the dispatcher gets back to take() the third time, since that
        // means we handled the second value pulled off.
        dataAvailable.await(3, TimeUnit.SECONDS);
        verify(mockFaultCodes).updateCachedFaultCode(arg2.capture());
        FaultCodeEvent capturedDTC = arg2.getValue();

        // Wait until the dispatcher gets back to take() the fourth time, since that
        // means we handled the third value pulled off (junk data).
        dataAvailable.await(3, TimeUnit.SECONDS);
        // Failed to parse as subscription/alarm data
        verifyZeroInteractions(mockVehicleData);
        verifyZeroInteractions(mockFaultCodes);

        // Kill the dispatcher thread.
        dispatcher.stopThread();
        dispatcher.join();
        assertTrue(!dispatcher.isAlive());

        // Check the vehicle data is correct
        assertNotNull(capturedVehicleData);
        assertEquals("vehicle/data/baz", capturedVehicleData.getUri());
        assertEquals(4.3, capturedVehicleData.getResponse().getValue());

        // Check the fault code is correct
        assertNotNull(capturedDTC);
        assertEquals("vehicle/dtc/can0_active/ecu0", capturedDTC.getUri());
        assertEquals("00ff00000000ffff", capturedDTC.getResponse().getValue());
    }

    protected void tearDown() throws Exception {
        super.tearDown();
    }

}
