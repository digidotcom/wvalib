/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva.test_auxiliary;

import android.text.TextUtils;
import android.util.Log;

import com.digi.wva.WVA;
import com.digi.wva.async.EventChannelStateListener;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Custom {@link EventChannelStateListener} subclass, specialized for use in
 * integration testing of the WVA library.
 *
 * <p>
 *     This class will capture and record any invocations of its callback methods (onConnected, etc.)
 *     and provides methods to "expect" a call to any of those methods. Example usage is as follows:
 *
 *     <pre>
 *         listener = new MockStateListener();
 *         wva.setEventChannelStateListener(listener);
 *         wva.connectEventChannel(...);
 *
 *         MockStateListener.OnConnectedCall call = listener.expectOnConnected();
 *         assertEquals(wva, call.device);
 *
 *         // Alternative
 *         wva.connectEventChannel(some_bad_port);
 *         MockStateListener.OnFailedConnectionCall call = listener.expectOnFailedConnection();
 *         assertEquals(wva, call.device);
 *         assertEquals(some_bad_port, call.port);
 *     </pre>
 *
 *     These {@code expect*} methods all use the same timeout period, 5 seconds.
 * </p>
*/
public final class MockStateListener extends EventChannelStateListener {
    private static final String TAG = "MockStateListener";
    private static final long TIMEOUT_SECONDS = 5;

    private final BlockingQueue<MethodCall> calls = new LinkedBlockingQueue<MethodCall>();

    /** Enumeration of the different methods available on this class. */
    private static enum Method {
        ON_CONNECTED("onConnected"),
        ON_ERROR("onError"),
        ON_REMOTE_CLOSE("onRemoteClose"),
        ON_FAILED_CONNECTION("onFailedConnection"),
        ON_DONE("onDone");

        private final String name;
        private Method(String name) {
            this.name = name;
        }

        @Override public String toString() {
            return name;
        }
    }

    /** Encapsulation of a call to one of the methods in this class. */
    private static class MethodCall {
        public final Method method;
        public final Object[] args;

        public MethodCall(Method method, Object... args) {
            this.method = method;
            this.args = args;
        }

        public String toString() {
            return String.format("%s(%s)", method.toString(), TextUtils.join(", ", args));
        }
    }

    private MethodCall expect(Method method) {
        MethodCall call;
        try {
            call = calls.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new AssertionError("Thread interrupted waiting for " + method.toString());
        }

        if (call == null) {
            throw new AssertionError(String.format("Timeout waiting for %s: No methods called.", method));
        }

        if (call.method != method) {
            throw new AssertionError(String.format("Expected %s to be called, got %s instead.", method, call));
        }

        return call;
    }

    @Override
    public void onConnected(WVA device) {
        Log.i(TAG, "onConnected");

        calls.add(new MethodCall(Method.ON_CONNECTED, device));
    }

    /**
     * Encapsulation of a call to {@link #onConnected(WVA) onConnected}. The {@code device}
     * argument of the invocation is available on the public final field {@link #device}.
     */
    public static final class OnConnectedCall {
        /** The {@code device} argument to this invocation.
         */
        public final WVA device;

        private OnConnectedCall(Object[] args) {
            this.device = (WVA) args[0];
        }
    }

    /**
     * Waits until {@link #onConnected} has been called, or the wait timeout period elapses.
     *
     * <p>This method, and those like it on this class, will throw an AssertionError if any exception
     * occurs, or if the first captured method is not onConnected. This AssertionError will cause
     * the test case to fail.</p>
     *
     * @return an encapsulation of the captured {@link #onConnected} call
     */
    public OnConnectedCall expectOnConnected() {
        // Will throw AssertionError if anything is wrong.
        MethodCall call = expect(Method.ON_CONNECTED);

        return new OnConnectedCall(call.args);
    }

    @Override
    public void onError(WVA device, IOException error) {
        Log.i(TAG, "onError: " + error.getMessage());

        calls.add(new MethodCall(Method.ON_ERROR, device, error));
    }

    /**
     * Encapsulation of a call to {@link #onError(WVA, IOException) onError}.
     * The {@code device} and {@code error} arguments of the invocation is available on the public
     * final fields {@link #device} and {@link #error}.
     */
    public static final class OnErrorCall {
        /** The {@code device} argument of this invocation. */
        public final WVA device;
        /** The {@code error} argument of this invocation. */
        public final IOException error;

        private OnErrorCall(Object[] args) {
            this.device = (WVA) args[0];
            this.error = (IOException) args[1];
        }
    }

    /**
     * Waits until {@link #onError} has been called, or the wait timeout period elapses.
     *
     * <p>This method, and those like it on this class, will throw an AssertionError if any exception
     * occurs, or if the first captured method is not onError. This AssertionError will cause
     * the test case to fail.</p>
     *
     * @return an encapsulation of the captured {@link #onError} call
     */
    public OnErrorCall expectOnError() {
        // Will throw AssertionError if anything is wrong.
        MethodCall call = expect(Method.ON_ERROR);

        return new OnErrorCall(call.args);
    }

    @Override
    public void onRemoteClose(WVA device, int port) {
        Log.i(TAG, "onRemoteClose");

        calls.add(new MethodCall(Method.ON_REMOTE_CLOSE, device, port));
    }

    /**
     * Encapsulation of a call to {@link #onRemoteClose(WVA, int) onRemoteClose}.
     * The {@code device} and {@code port} arguments of the invocation is available on the public
     * final fields {@link #device} and {@link #port}.
     */
    public static final class OnRemoteCloseCall {
        /** The {@code device} argument of this invocation. */
        public final WVA device;
        /** The {@code error} argument of this invocation. */
        public final int port;

        private OnRemoteCloseCall(Object[] args) {
            this.device = (WVA) args[0];
            this.port = (Integer) args[1];
        }
    }

    /**
     * Waits until {@link #onRemoteClose} has been called, or the wait timeout period elapses.
     *
     * <p>This method, and those like it on this class, will throw an AssertionError if any exception
     * occurs, or if the first captured method is not onRemoteClose. This AssertionError will cause
     * the test case to fail.</p>
     *
     * @return an encapsulation of the captured {@link #onRemoteClose} call
     */
    public OnRemoteCloseCall expectOnRemoteClose() {
        // Will throw AssertionError if anything is wrong.
        MethodCall call = expect(Method.ON_REMOTE_CLOSE);

        return new OnRemoteCloseCall(call.args);
    }

    @Override
    public void onFailedConnection(WVA device, int port) {
        Log.i(TAG, "onFailedConnection");

        calls.add(new MethodCall(Method.ON_FAILED_CONNECTION, device, port));
    }

    /**
     * Encapsulation of a call to {@link #onFailedConnection(WVA, int) onFailedConnection}.
     * The {@code device} and {@code port} arguments of the invocation is available on the public
     * final fields {@link #device} and {@link #port}.
     */
    public static final class OnFailedConnectionCall {
        /** The {@code device} argument of this invocation. */
        public final WVA device;
        /** The {@code error} argument of this invocation. */
        public final int port;

        private OnFailedConnectionCall(Object[] args) {
            this.device = (WVA) args[0];
            this.port = (Integer) args[1];
        }
    }

    /**
     * Waits until {@link #onFailedConnection} has been called, or the wait timeout period elapses.
     *
     * <p>This method, and those like it on this class, will throw an AssertionError if any exception
     * occurs, or if the first captured method is not onFailedConnection. This AssertionError will cause
     * the test case to fail.</p>
     *
     * @return an encapsulation of the captured {@link #onFailedConnection} call
     */
    public OnFailedConnectionCall expectOnFailedConnection() {
        // Will throw AssertionError if anything is wrong.
        MethodCall call = expect(Method.ON_FAILED_CONNECTION);

        return new OnFailedConnectionCall(call.args);
    }

    @Override
    public void onDone(WVA device) {
        Log.i(TAG, "onDone");

        calls.add(new MethodCall(Method.ON_DONE, device));
    }

    /**
     * Encapsulation of a call to {@link #onDone(WVA) onDone}.
     * The {@code device} arguments of the invocation is available on the public
     * final field {@link #device}.
     */
    public static final class OnDoneCall {
        /** The {@code device} argument of this invocation. */
        public final WVA device;

        private OnDoneCall(Object[] args) {
            this.device = (WVA) args[0];
        }
    }

    /**
     * Waits until {@link #onDone} has been called, or the wait timeout period elapses.
     *
     * <p>This method, and those like it on this class, will throw an AssertionError if any exception
     * occurs, or if the first captured method is not onFailedConnection. This AssertionError will cause
     * the test case to fail.</p>
     *
     * @return an encapsulation of the captured {@link #onDone} call
     */
    public OnDoneCall expectOnDone() {
        // Will throw AssertionError if anything is wrong.
        MethodCall call = expect(Method.ON_DONE);

        return new OnDoneCall(call.args);
    }

    /**
     * Verify that this mock listener has no more recorded invocations.
     *
     * <p>Use this method in test cases to verify that a listener has never been invoked or
     * that all invocations has been extracted (via the {@code expect*} methods).</p>
     */
    public synchronized void verifyNoInvocations() {
        int count = calls.size();
        if (count > 0) {
            String invocations = TextUtils.join(", ", calls.toArray());
            throw new AssertionError(
                    String.format("Expected no more method calls, but there were %d: [ %s ]", count, invocations)
            );
        }
    }

    @Override
    public final boolean runsOnUiThread() {
        return false;
    }
}