/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva.async;

import android.os.Handler;
import android.os.Message;

import com.digi.wva.WVA;

import junit.framework.TestCase;

import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for the EventChannelStateListener class.
 */
public class EventChannelStateListenerTest extends TestCase {
    public void testRunsOnUiThread() {
        EventChannelStateListener listener = new EventChannelStateListener() {};

        // Verify that the default runsOnUiThread return value is true
        assertTrue(listener.runsOnUiThread());
    }

    public void testWrapNull() {
        assertNull(EventChannelStateListener.wrap(null, new Handler()));
    }

    public void testWrapNonUiThread() {
        EventChannelStateListener listener = new EventChannelStateListener() {
            @Override
            public boolean runsOnUiThread() {
                return false;
            }
        };

        assertEquals(listener, EventChannelStateListener.wrap(listener, new Handler()));
    }

    public void testWrappedListener() {
        Handler uiThread = mock(Handler.class);
        // Handler#post is final, but sendMessageAtTime, which post eventually calls, is not.
        doReturn(true).when(uiThread).sendMessageAtTime(any(Message.class), anyLong());

        WVA mockWVA = mock(WVA.class);
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);

        EventChannelStateListener listener = mock(EventChannelStateListener.class);
        doReturn(true).when(listener).runsOnUiThread();
        // For code coverage purposes, call real onFailedConnection method
        doCallRealMethod().when(listener).onFailedConnection(any(WVA.class), anyInt());

        EventChannelStateListener wrapped = EventChannelStateListener.wrap(listener, uiThread);
        // `wrap' should check if the listener runs on the UI thread or not.
        verify(listener).runsOnUiThread();

        @SuppressWarnings("ThrowableInstanceNeverThrown")
        IOException error = new IOException("Foo");

        // Test that each method on the wrapping listener posts a Runnable to the handler,
        // which, when run, calls the wrapped listener's method.

        wrapped.onConnected(mockWVA);
        verify(uiThread).sendMessageAtTime(messageCaptor.capture(), anyLong());
        verify(listener, never()).onConnected(any(WVA.class));
        // Run the Runnable, expect it to call onConnected
        messageCaptor.getValue().getCallback().run();
        verify(listener).onConnected(mockWVA);

        reset(uiThread);

        wrapped.onDone(mockWVA);
        verify(uiThread).sendMessageAtTime(messageCaptor.capture(), anyLong());
        verify(listener, never()).onDone(any(WVA.class));
        // Run the Runnable, expect it to call onDone
        messageCaptor.getValue().getCallback().run();
        verify(listener).onDone(mockWVA);

        reset(uiThread);

        wrapped.onError(mockWVA, error);
        verify(uiThread).sendMessageAtTime(messageCaptor.capture(), anyLong());
        verify(listener, never()).onError(any(WVA.class), any(IOException.class));
        // Run the Runnable, expect it to call onError
        messageCaptor.getValue().getCallback().run();
        verify(listener).onError(mockWVA, error);

        reset(uiThread);

        wrapped.onFailedConnection(mockWVA, 555);
        verify(uiThread).sendMessageAtTime(messageCaptor.capture(), anyLong());
        verify(listener, never()).onFailedConnection(any(WVA.class), anyInt());
        // Run the Runnable, expect it to call onError
        messageCaptor.getValue().getCallback().run();
        verify(listener).onFailedConnection(mockWVA, 555);

        reset(uiThread);

        wrapped.onRemoteClose(mockWVA, 555);
        verify(uiThread).sendMessageAtTime(messageCaptor.capture(), anyLong());
        verify(listener, never()).onRemoteClose(any(WVA.class), anyInt());
        // Run the Runnable, expect it to call onError
        messageCaptor.getValue().getCallback().run();
        verify(listener).onRemoteClose(mockWVA, 555);
    }

    public void testReconnectAfter() throws Exception {
        // Verify that reconnectAfter calls connectEventChannel after the given delay
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicLong callTime = new AtomicLong();

        WVA mockWVA = mock(WVA.class);
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                callTime.set(System.currentTimeMillis());
                latch.countDown();

                return null;
            }
        }).when(mockWVA).connectEventChannel(anyInt());

        EventChannelStateListener listener = new EventChannelStateListener() {};

        long start = System.currentTimeMillis();
        listener.reconnectAfter(mockWVA, 1500, 9999);
        // Wait for the call
        assertTrue("connectEventChannel was not called!", latch.await(3, TimeUnit.SECONDS));
        verify(mockWVA).connectEventChannel(9999);

        long timeToCall = callTime.get() - start;
        assertTrue("Call didn't take around 1500 ms", timeToCall > 1400 && timeToCall < 2000);
    }

    public void testReconnectAfter_NoReconnect() throws Exception {
        WVA mockWVA = mock(WVA.class);
        EventChannelStateListener listener = new EventChannelStateListener() {};

        listener.stopReconnects();
        listener.reconnectAfter(mockWVA, 500, 9999);
        // Wait 1.5 seconds - should be long enough to verify we never reconnected
        Thread.sleep(1500);
        verify(mockWVA, never()).connectEventChannel(anyInt());
    }
}
