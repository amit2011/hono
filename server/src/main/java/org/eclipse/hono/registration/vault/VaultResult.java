/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.registration.vault;

import io.vertx.core.json.JsonObject;

/**
 * A wrapper around an HTTP status code and a JSON payload.
 *
 */
public class VaultResult {

    private int statusCode;
    private JsonObject payload;

    private VaultResult(final int statusCode, final JsonObject body) {
        this.statusCode = statusCode;
        this.payload = body;
    }

    public static VaultResult result(final int statusCode) {
        return result(statusCode, null);
    }

    public static VaultResult result(final int statusCode, final JsonObject body) {
        return new VaultResult(statusCode, body);
    }

    /**
     * @return the statusCode
     */
    public final int getStatusCode() {
        return statusCode;
    }

    /**
     * @return the payload
     */
    public final JsonObject getPayload() {
        return payload;
    }
}
