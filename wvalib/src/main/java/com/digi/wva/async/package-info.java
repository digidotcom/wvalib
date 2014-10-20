/**
 * <h2>Callbacks on UI thread</h2>
 *
 * <p> To ease use of the WVA
 * Android library, all asynchronous methods in the WVA class will by
 * default execute their callbacks on the application UI thread (or
 * main thread). Additionally, any "listeners" used to subscribe for
 * data or alarms, or TCP stream connection status, will also have
 * methods executed on the main thread by default.</p>
 *
 * See the documentation for the following methods for more
 * information on this functionality, and how to force callbacks
 * to execute in a background thread instead, if this is useful
 * for your application:
 *
 * <ul>
 *     <li>{@link com.digi.wva.async.WvaCallback#runsOnUiThread() WvaCallback.runsOnUiThread}</li>
 *     <li>{@link com.digi.wva.async.EventChannelStateListener#runsOnUiThread() EventChannelStateListener.runsOnUiThread}</li>
 *     <li>{@link com.digi.wva.async.VehicleDataListener#runsOnUiThread() VehicleDataListener
 *     .runsOnUiThread}</li>
 *     <li>{@link com.digi.wva.async.FaultCodeListener#runsOnUiThread() FaultCodeListener
 *     .runsOnUiThread}</li>
 * </ul>
 *
 *     <p>These are some of the interactions which must be run from
 *     the main thread in Android:</p>
 *
 *     <ul>
 *         <li>
 *             Interacting with or updating views (such as changing
 *             text, changing icons, calling {@code
 *             notifyDataSetChanged()} on an adapter, etc.)
 *         </li>
 *         <li>
 *             Creating new {@code Toast} notifications
 *         </li>
 *         <li>
 *             Manually drawing/painting on screen
 *         </li>
 *     </ul>
 *
 *     <p>The following tasks should <i>not</i> be done on the UI thread:</p>
 *
 *     <ul>
 *         <li>Loading data from the network</li>
 *         <li>Performing long-running calculations</li>
 *         <li>Accessing a file or database</li>
 *     </ul>
 *
 * <p>If the UI thread is blocked for more than a few seconds, the user
 * may encounter an "application not responding" dialog. You should
 * make sure to be careful in deciding which callbacks and listeners
 * should be executed on the UI thread, and which can be handled on a
 * background thread. (You always have the option of posting a {@link
 * java.lang.Runnable} to the main thread yourself.)  </p>
 *
 */
package com.digi.wva.async;