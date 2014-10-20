/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva.exc;

import junit.framework.TestCase;

/**
 * Unit tests of the very basic exceptions found in the com.digi.wva.exc package.
 */
@SuppressWarnings("ThrowableInstanceNeverThrown")
public class BasicExceptionsTest extends TestCase {
    public void testDisconnectedException() {
        DisconnectedException exc1 = new DisconnectedException();
        DisconnectedException exc2 = new DisconnectedException("Message goes here");

        // exc1 has no message
        assertNull(exc1.getMessage());
        assertNull(exc1.getCause());

        // exc2 has a message
        assertEquals("Message goes here", exc2.getMessage());
        assertNull(exc2.getCause());
    }

    public void testNotListeningToEcuException() {
        NotListeningToECUException exc = new NotListeningToECUException("Message here");

        assertEquals("Message here", exc.getMessage());
        assertNull(exc.getCause());
    }

    public void testWvaException() {
        WvaException exception;

        // No message, no cause
        exception = new WvaException();
        assertNull(exception.getMessage());
        assertNull(exception.getCause());

        // Message, no cause
        exception = new WvaException("Message here");
        assertEquals("Message here", exception.getMessage());
        assertNull(exception.getCause());

        Exception cause = new Exception("foo");

        // No message, cause
        exception = new WvaException(cause);
        //assertNull(exception.getMessage());
        assertEquals(cause.toString(), exception.getMessage());
        assertEquals(cause, exception.getCause());

        // Message and cause
        exception = new WvaException("Message!", cause);
        assertEquals("Message!", exception.getMessage());
        assertEquals(cause, exception.getCause());
    }
}
