/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva.async;

import android.os.Handler;
import android.os.Message;

import junit.framework.TestCase;

import org.mockito.ArgumentCaptor;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for the VehicleDataListener class.
 */
public class VehicleDataListenerTest extends TestCase {
    public void testRunsOnUiThread() {
        VehicleDataListener listener = new VehicleDataListener() {
            @Override
            public void onEvent(VehicleDataEvent event) {
                // nothing here
            }
        };

        // Verify that the default runsOnUiThread return value is true
        assertTrue(listener.runsOnUiThread());
    }

    public void testWrapNull() {
        assertNull(VehicleDataListener.wrap(null, new Handler()));
    }

    public void testWrapNonUiThread() {
        VehicleDataListener listener = new VehicleDataListener() {
            @Override
            public void onEvent(VehicleDataEvent event) {
                // nothing here
            }

            @Override
            public boolean runsOnUiThread() {
                return false;
            }
        };

        assertEquals(listener, VehicleDataListener.wrap(listener, new Handler()));
    }

    public void testWrappedListener() {
        Handler uiThread = mock(Handler.class);
        // Handler#post is final, but sendMessageAtTime, which post eventually calls, is not.
        doReturn(true).when(uiThread).sendMessageAtTime(any(Message.class), anyLong());
        // Captures the Message passed into sendMessageAtTime
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);

        VehicleDataEvent mockEvent = mock(VehicleDataEvent.class);

        final AtomicBoolean onEventCalled = new AtomicBoolean(false);
        final AtomicReference<VehicleDataEvent> receivedEvent = new AtomicReference<VehicleDataEvent>(null);

        VehicleDataListener listener = new VehicleDataListener() {
            @Override
            public void onEvent(VehicleDataEvent event) {
                receivedEvent.set(event);
                onEventCalled.set(true);
            }
        };

        VehicleDataListener wrapped = VehicleDataListener.wrap(listener, uiThread);

        wrapped.onEvent(mockEvent);
        verify(uiThread).sendMessageAtTime(messageCaptor.capture(), anyLong());
        assertFalse(onEventCalled.get());

        // Run the Runnable, expect it to call onEvent
        messageCaptor.getValue().getCallback().run();
        assertTrue(onEventCalled.get());
        assertEquals(mockEvent, receivedEvent.get());
    }
}
