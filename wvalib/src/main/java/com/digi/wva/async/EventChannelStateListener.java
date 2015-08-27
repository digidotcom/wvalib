/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva.async;

import android.os.Handler;
import android.util.Log;

import com.digi.wva.WVA;
import com.digi.wva.internal.MainThreadOptional;

import java.io.IOException;

/**
 * Listener for certain events that happen within the {@link com.digi.wva.internal.EventChannel}
 * class, such as successfully connecting, stopping because of an error, and
 * the remote end of the socket closing.
 */
public abstract class EventChannelStateListener implements MainThreadOptional {
    private static final String TAG = "wvalib E.C.S.Listener";
    private boolean shouldNotReconnect;

    /**
     * Listener constructor.
     */
    protected EventChannelStateListener() {}

    /**
     * Get a EventChannelStateListener implementation which just
     * calls through to the super's methods in each callback.
     *
     * <p>This is the default EventChannelStateListener used by
     * {@link com.digi.wva.internal.EventChannel}.</p>
     *
     * @return a new {@link EventChannelStateListener} which uses only
     * the default class implementations for callbacks
     */
    public static EventChannelStateListener getDefault() {
        return new EventChannelStateListener() {};
    }

    /**
     * Set a flag internal to the listener, ensuring that, if {@link #reconnectAfter} is called,
     * we do not actually attempt to reconnect to the WVA.
     */
    public final void stopReconnects() {
        this.shouldNotReconnect = true;
    }

    /**
     * Launch a new thread, and in that thread, sleep the given length of time,
     * disconnect the data stream and reconnect.
     *
     * @param device the device to be manipulated
     * @param millis length of time to sleep the thread, in milliseconds
     * @param port port to connect to upon reconnect
     */
    public final void reconnectAfter(final WVA device, final long millis, final int port) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(millis);
                } catch (InterruptedException e) {
                    // An interruption will presumably be caused by us wanting to
                    // stop this thread... so let's abort the whole reconnection
                    // process.
                    Log.e(TAG, "Sleep before disconnect-reconnect was interrupted!", e);
                    return;
                }

                if (shouldNotReconnect) {
                    Log.i(TAG, "Listener has been directed not to reconnect.");
                    return;
                }

                device.connectEventChannel(port);
            }
        }).start();
    }

    /**
     * Callback triggered when the {@link com.digi.wva.internal.EventChannel}'s
     * socket has been successfully created (or, if the socket already existed,
     * when the thread has started up).
     *
     * <p>The default implementation of this method is to do nothing --
     * override this method to add, for example, interactivity with the UI.</p>
     *
     * @param device the {@link WVA} associated with the EventChannel which
     *               triggered this call
     */
    public void onConnected(WVA device) {
        // Default implementation does nothing. Can be overridden to
        // notify a UI, for example.
    }

    /**
     * Callback triggered when the {@link com.digi.wva.internal.EventChannel} is stopped due to any
     * {@code IOException} which occurs after the event channel socket has been created. An
     * exception during socket creation will call
     * {@link #onFailedConnection(com.digi.wva.WVA, int)}.
     *
     * @param device the {@link WVA} associated with the EventChannel which
     *               triggered this call
     * @param error the exception causing the receiver to stop
     */
    public void onError(WVA device, IOException error) {
        // Default implementation does nothing. Can be overridden to
        // notify a UI, for example.
    }

    /**
     * Callback triggered when the EventChannel detects that the WVA has closed
     * its data socket (generally because the web services process
     * on the device has restarted, or because the device is shutting down)
     *
     * <p>The default implementation calls
     * {@link #reconnectAfter(com.digi.wva.WVA, long, int)}
     * with 15000 as the second parameter (i.e. "reconnect after 15 seconds").</p>
     *
     * @param device the {@link WVA} associated with the EventChannel which
     *               triggered this call
     * @param port the port which we were connected to
     */
    public void onRemoteClose(WVA device, int port) {
        reconnectAfter(device, 15000, port);
    }

    /**
     * Callback is triggered when the EventChannel was unable to make an initial
     * connection to the device.
     *
     * @param device the {@link WVA} associated with the EventChannel which
     *               triggered this call
     * @param port the port on which we attempted to connect
     */
    public void onFailedConnection(WVA device, int port) {
        // Empty
    }

    /**
     * Callback triggered when the event channel thread has ended. No more methods on this listener
     * will be called, unless {@link #reconnectAfter(com.digi.wva.WVA, long, int) reconnectAfter} is
     * called in another method (such as {@link #onRemoteClose onRemoteClose})
     * @param device the {@link WVA} the {@link WVA} associated with the EventChannel which
     *               triggered this call
     */
    public void onDone(WVA device) {
        // Empty
    }

    /**
     * Specifies whether this listener's methods should be executed on the
     * application's main thread (also called the UI thread), or
     * if it should be executed on a background thread. By default,
     * this returns <b>true</b>.
     *
     * <p>
     *     If you know this listener's methods do not need to be run on the
     *     UI thread, override this method to return false. Here's an example:
     * </p>
     *
     * <pre>
     *     wva.connectEventChannel(5000, new EventChannelStateListener() {
     *         {@literal @}Override
     *         public void runsOnUiThread() {
     *             return false;
     *         }
     *
     *         {@literal @}Override
     *         public void onConnected(WVA device) {
     *             // ...
     *         }
     *
     *         // ...
     *     });
     * </pre>
     *
     * @return true if this listener's methods should be executed on the UI thread,
     *         false otherwise
     */
    public boolean runsOnUiThread() {
        return true;
    }

    /**
     * <b>Normal library users should not need to call this method.</b>
     * Used internally by the library to convert a normal listener into one where
     * each method is executed on the main thread.
     *
     * <p>
     * If the listeners's {@link #runsOnUiThread()} returns true, returns a new
     * EventChannelStateListener instance to run which delegates to <b>listener</b>'s methods,
     * but executes them on the UI thread. Otherwise, if {@link #runsOnUiThread()} returns false,
     * this method returns <b>listener</b> directly.
     * </p>
     *
     * @param listener the EventChannelStateListener to wrap
     * @param uiThread a handle to the UI thread
     * @return see description
     */
    public static EventChannelStateListener wrap(EventChannelStateListener listener, Handler uiThread) {
        if (listener == null) {
            return null;
        }

        if (listener.runsOnUiThread()) {
            // Wrap the EventChannelStateListener in a new one which, when any of its methods are
            // invoked, posts to the UI thread a call to the same method on the wrapped listener.
            return new UiThreadEventChannelStateListener(uiThread, listener);
        } else {
            return listener;
        }
    }

    private static class UiThreadEventChannelStateListener extends EventChannelStateListener {
        private final EventChannelStateListener wrapped;
        private final Handler uiThread;

        public UiThreadEventChannelStateListener(Handler uiThread, EventChannelStateListener wrapped) {
            this.uiThread = uiThread;
            this.wrapped = wrapped;
        }

        @Override
        public void onConnected(final WVA device) {
            uiThread.post(new Runnable() {
                @Override
                public void run() {
                    wrapped.onConnected(device);
                }
            });
        }

        @Override
        public void onError(final WVA device, final IOException error) {
            uiThread.post(new Runnable() {
                @Override
                public void run() {
                    wrapped.onError(device, error);
                }
            });
        }

        @Override
        public void onRemoteClose(final WVA device, final int port) {
            uiThread.post(new Runnable() {
                @Override
                public void run() {
                    wrapped.onRemoteClose(device, port);
                }
            });
        }

        @Override
        public void onFailedConnection(final WVA device, final int port) {
            uiThread.post(new Runnable() {
                @Override
                public void run() {
                    wrapped.onFailedConnection(device, port);
                }
            });
        }

        @Override
        public void onDone(final WVA device) {
            uiThread.post(new Runnable() {
                @Override
                public void run() {
                    wrapped.onDone(device);
                }
            });
        }
    }
}
