/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva.exc;

import android.util.SparseArray;

import junit.framework.TestCase;

import java.lang.reflect.Constructor;

/**
 * Unit tests for WvaHttpException and its subclasses.
 */
@SuppressWarnings("ThrowableInstanceNeverThrown")
public class WvaHttpExceptionsTest extends TestCase {
    private static final SparseArray<Class<? extends WvaHttpException>> exceptionsByStatus =
            new SparseArray<Class<? extends WvaHttpException>>();

    static {
        exceptionsByStatus.put(400, WvaHttpException.WvaHttpBadRequest.class);
        exceptionsByStatus.put(403, WvaHttpException.WvaHttpForbidden.class);
        exceptionsByStatus.put(404, WvaHttpException.WvaHttpNotFound.class);
        exceptionsByStatus.put(414, WvaHttpException.WvaHttpRequestUriTooLong.class);
        exceptionsByStatus.put(500, WvaHttpException.WvaHttpInternalServerError.class);
        exceptionsByStatus.put(503, WvaHttpException.WvaHttpServiceUnavailable.class);
    }

    public void testWvaHttpException() {
        WvaHttpException exception = new WvaHttpException("Description", "URL", "<body>");

        assertNull(exception.getCause());
        assertEquals("WvaHttpException: Description on URL. Body:\n<body>", exception.getMessage());
        assertEquals("<body>", exception.getBody());
        assertEquals("URL", exception.getUrl());

        // Test with null body
        exception = new WvaHttpException("Description", "URL", null);

        assertNull(exception.getCause());
        assertEquals("WvaHttpException: Description on URL", exception.getMessage());
        assertNull(exception.getBody());
        assertEquals("URL", exception.getUrl());
    }

    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    public void testWvaHttpExceptionSubclasses() throws Exception {
        final String url = "<url here>",
                     body = "<body here>";

        for (int i = 0; i < exceptionsByStatus.size(); i++) {
            int code = exceptionsByStatus.keyAt(i);
            Class<? extends WvaHttpException> clazz = exceptionsByStatus.get(code);
            Constructor cons = clazz.getDeclaredConstructor(String.class, String.class);

            // Instantiate the exception, passing in the URL and body.
            WvaHttpException exception = (WvaHttpException) cons.newInstance(url, body);

            assertNull(exception.getCause());
            assertEquals("Wrong message for " + code,
                         "WvaHttpException: HTTP " + code + " on <url here>. Body:\n<body here>",
                         exception.getMessage());
            assertEquals("Wrong body for " + code, body, exception.getBody());
            assertEquals("Wrong url for " + code, url, exception.getUrl());

            // Instantiate another exception, with a null body this time
            exception = (WvaHttpException) cons.newInstance(url, null);

            assertNull(exception.getCause());
            assertEquals("Wrong message for " + code,
                         "WvaHttpException: HTTP " + code + " on <url here>",
                         exception.getMessage());
            assertNull("Wrong body for " + code, exception.getBody());
            assertEquals("Wrong url for " + code, url, exception.getUrl());
        }
    }
}
