/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva.internal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;

import com.digi.wva.test_auxiliary.JsonFactory;
import junit.framework.TestCase;

import org.json.JSONObject;

import static org.mockito.Mockito.*;

public class EventChannelTest extends TestCase {

    protected void setUp() throws Exception {
        super.setUp();
    }

    public void testStartStop() throws Exception{
        Socket sock = mock(Socket.class);
        when(sock.getInputStream()).thenReturn(
                new ByteArrayInputStream("this is a test".getBytes("UTF-8")));

        EventChannel channel = new EventChannel(null, sock);
        channel.start();
        channel.stopThread(new IOException("TEST_EXCEPTION"));
        channel.join();
        verify(sock, atLeastOnce()).close();
    }



    /**
     * Assures EventChannel.incoming queue works as expected
     * @throws Exception
     */
    public void testQueue() throws Exception {
        Socket sock = mock(Socket.class);
        EventChannel channel = new EventChannel(null, sock);

        BlockingQueue<JSONObject> q = channel.getQueue();
        JSONObject obj1 = new JSONObject();
        q.add(obj1);
        JSONObject obj2 = q.take();
        assertTrue(obj1.equals(obj2));
    }

    /**
     * Tests whether EventChannel can read and parse JSON from its socket
     * @throws Exception
     */
    public void testSocket() throws Exception {
        JSONObject dataObj = new JsonFactory().data();

        InputStream stream = new ByteArrayInputStream(dataObj.toString().getBytes("UTF-8"));
        Socket mockSocket = mock(Socket.class);
        when(mockSocket.getInputStream()).thenReturn(stream);

        EventChannel channel = new EventChannel(null, mockSocket);
        channel.setDaemon(true);
        channel.start();

        JSONObject jObj = channel.getQueue().take();
        assertTrue(jObj.getJSONObject("data").getString("uri").equals(
                dataObj.getJSONObject("data").getString("uri")));
    }

    protected void tearDown() throws Exception {
        super.tearDown();
    }
}
