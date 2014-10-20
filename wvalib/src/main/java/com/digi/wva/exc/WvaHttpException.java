/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva.exc;

import android.text.TextUtils;

import org.w3c.dom.Text;

/**
 * Base class for exceptions wrapping unsuccessful HTTP requests with the WVA.
 */
public class WvaHttpException extends Exception {
    private final String description, body, url;

    /**
     * Constructor for WvaHttpException.
     * @param description a short description of the error
     * @param url the URL that was requested
     * @param body the response body, if any
     */
    public WvaHttpException(String description, String url, String body) {
        super();
        this.description = description;
        this.url = url;
        this.body = body;
    }

    /**
     * Returns the response body which was provided when this
     * {@code WvaHttpException} was created.
     */
    public final String getBody() {
        return this.body;
    }

    /**
     * Returns the request URL which was provided when this
     * {@code WvaHttpException} was created.
     */
    public final String getUrl() {
        return this.url;
    }

    /**
     * Returns a human-readable message describing this exception. This method is intended to be used
     * when outputting exceptions to the logs.
     *
     * @return {@code "WvaHttpException: %s on %s. Body:%n%s" % description, url, body}
     */
    @Override
    public final String getMessage() {
        if (TextUtils.isEmpty(this.body)) {
            return String.format("WvaHttpException: %s on %s", this.description, this.url);
        } else {
            return String.format("WvaHttpException: %s on %s. Body:%n%s", this.description,
                    this.url, this.body);
        }
    }

    /**
     * Exception passed through the library when an HTTP request to the WVA is met
     * with an HTTP 400 response.
     */
    public static class WvaHttpBadRequest extends WvaHttpException {
        /**
         * Constructor for a WvaHttpBadRequest exception.
         * @param url the URL that was requested
         * @param body the response body, if any
         */
        public WvaHttpBadRequest(String url, String body) {
            super("HTTP 400", url, body);
        }
    }

    /**
     * Exception passed through the library when an HTTP request to the WVA is met
     * with an HTTP 403 response.
     */
    public static class WvaHttpForbidden extends WvaHttpException {
        /**
         * Constructor for a WvaHttpForbidden exception.
         * @param url the URL that was requested
         * @param body the response body, if any
         */
        public WvaHttpForbidden(String url, String body) {
            super("HTTP 403", url, body);
        }
    }

    /**
     * Exception passed through the library when an HTTP request to the WVA is met
     * with an HTTP 404 response.
     */
    public static class WvaHttpNotFound extends WvaHttpException {
        /**
         * Constructor for a WvaHttpNotFound exception.
         * @param url the URL that was requested
         * @param body the response body, if any
         */
        public WvaHttpNotFound(String url, String body) {
            super("HTTP 404", url, body);
        }
    }

    // 405 and 406 errors (method not allowed, not acceptable) should not happen with our library.

    /**
     * Exception passed through the library when an HTTP request to the WVA is met
     * with an HTTP 414 response.
     */
    public static class WvaHttpRequestUriTooLong extends WvaHttpException {
        /**
         * Constructor for a WvaHttpRequestUriTooLong exception.
         * @param url the URL that was requested
         * @param body the response body, if any
         */
        public WvaHttpRequestUriTooLong(String url, String body) {
            super("HTTP 414", url, body);
        }
    }

    // 415 errors (unsupported media type) also should not happen.

    /**
     * Exception passed through the library when an HTTP request to the WVA is met
     * with an HTTP 500 response.
     */
    public static class WvaHttpInternalServerError extends WvaHttpException {
        /**
         * Constructor for a WvaHttpInternalServerError exception.
         * @param url the URL that was requested
         * @param body the response body, if any
         */
        public WvaHttpInternalServerError(String url, String body) {
            super("HTTP 500", url, body);
        }
    }

    /**
     * Exception passed through the library when an HTTP request to the WVA is met
     * with an HTTP 503 response.
     */
    public static class WvaHttpServiceUnavailable extends WvaHttpException {
        /**
         * Constructor for a WvaHttpServiceUnavailable exception.
         * @param url the URL that was requested
         * @param body the response body, if any
         */
        public WvaHttpServiceUnavailable(String url, String body) {
            super("HTTP 503", url, body);
        }
    }
}
