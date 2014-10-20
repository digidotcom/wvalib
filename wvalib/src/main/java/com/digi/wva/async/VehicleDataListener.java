/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva.async;

import android.os.Handler;

import com.digi.wva.internal.AbstractEventListener;

/**
 * Provides a callback for handling new vehicle data.
 */
public abstract class VehicleDataListener extends AbstractEventListener<VehicleDataEvent> {
    /**
     * Callback for a new vehicle event. Will be called when a new event
     * comes in through the WVA's TCP event channel and is parsed out.
     *
     * @param event the event being processed
     */
    public abstract void onEvent(VehicleDataEvent event);

    /**
     * Specifies whether this listener's {@link #onEvent(VehicleDataEvent)} method
     * should be executed on the application's main thread (also called the UI thread), or
     * if it should be allowed to execute on a background thread. By default, this returns <b>true</b>.
     *
     * <p>
     *     If you know this listener does not need to be run on the
     *     UI thread, override this method to return false. Here's an example:
     * </p>
     *
     * <pre>
     *     wva.subscribeToVehicleData("EngineSpeed", 10, new VehicleDataListener() {
     *         {@literal @}Override
     *         public void runsOnUiThread() {
     *             return false;
     *         }
     *
     *         public void onEvent(VehicleDataEvent event) {
     *             // ...
     *         }
     *     });
     * </pre>
     *
     * @return true if this listener should be executed on the UI thread,
     *         false otherwise
     */
    public boolean runsOnUiThread() {
        // We override this method here to make its Javadoc specific to VehicleDataListener.
        return super.runsOnUiThread();
    }

    /**
     * <b>Normal library users should not need to call this method.</b>
     * Used internally by the library to convert a normal listener into one where
     * {@link #onEvent(VehicleDataEvent) onEvent} is executed on the main thread.
     *
     * <p>
     * If the listener's {@link #runsOnUiThread()} returns true, returns a new listener
     * which will run <b>listener</b>'s onEvent method on the UI thread. Otherwise, if
     * {@link #runsOnUiThread()} returns false, this method returns <b>listener</b> directly.
     * </p>
     *
     * @param uiThread the main thread handler, to be used when wrapping the listener
     *                  to execute on the main thread
     * @param listener the listener to wrap
     * @return a wrapped listener, or <b>listener</b> if it does not need to run on the UI thread.
     *          If <b>listener</b> is null, this returns null as well
     */
    public static VehicleDataListener wrap(VehicleDataListener listener, Handler uiThread) {
        if (listener == null || !listener.runsOnUiThread()) {
            return listener;
        }

        return new UiThreadVehicleDataListener(uiThread, listener);
    }

    /**
     * Internal VehicleDataListener subclass, wrapping a VehicleDataListener so that
     * calls to onEvent get passed onto the UI thread first.
     */
    private static class UiThreadVehicleDataListener extends VehicleDataListener {
        private final Handler uiThread;
        private final VehicleDataListener wrapped;

        UiThreadVehicleDataListener(Handler uiThread, VehicleDataListener listener) {
            this.uiThread = uiThread;
            this.wrapped = listener;
        }

        @Override
        public void onEvent(final VehicleDataEvent event) {
            uiThread.post(new Runnable() {
                @Override
                public void run() {
                    wrapped.onEvent(event);
                }
            });
        }
    }
}