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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for the WvaCallback class.
 */
public class WvaCallbackTest extends TestCase {
    public void testRunsOnUiThread() {
        WvaCallback<Void> callback = new WvaCallback<Void>() {
            @Override
            public void onResponse(Throwable error, Void response) {
                // nothing here
            }
        };

        // Verify that the default runsOnUiThread return value is true
        assertTrue(callback.runsOnUiThread());
    }

    public void testWrapNull() {
        assertNull(WvaCallback.wrap(null, new Handler()));
    }

    public void testWrapNonUiThread() {
        WvaCallback<Void> callback = new WvaCallback<Void>() {
            @Override
            public void onResponse(Throwable error, Void response) {
                // nothing here
            }

            @Override
            public boolean runsOnUiThread() {
                return false;
            }
        };

        assertEquals(callback, WvaCallback.wrap(callback, new Handler()));
    }

    public void testWrappedCallback() {
        Handler uiThread = mock(Handler.class);
        // Handler#post is final, but sendMessageAtTime, which post eventually calls, is not.
        doReturn(true).when(uiThread).sendMessageAtTime(any(Message.class), anyLong());
        // Captures the Message passed into sendMessageAtTime
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);

        final AtomicBoolean onResponseCalled = new AtomicBoolean(false);
        final AtomicReference<Throwable> callbackError = new AtomicReference<Throwable>(null);
        final AtomicInteger callbackResponse = new AtomicInteger(-1);

        WvaCallback<Integer> callback = new WvaCallback<Integer>() {
            @Override
            public void onResponse(Throwable error, Integer response) {
                callbackError.set(error);
                callbackResponse.set(response);
                onResponseCalled.set(true);
            }
        };

        WvaCallback<Integer> wrapped = WvaCallback.wrap(callback, uiThread);

        wrapped.onResponse(null, 5432);
        verify(uiThread).sendMessageAtTime(messageCaptor.capture(), anyLong());
        assertFalse(onResponseCalled.get());

        // Run the Runnable, expect it to call onResponse
        messageCaptor.getValue().getCallback().run();
        assertTrue(onResponseCalled.get());
        assertNull(callbackError.get());
        assertEquals(5432, callbackResponse.get());
    }
}
