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

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * Operations relevant for using vault as a persistence layer for the {@code RegistrationService}.
 *
 */
public interface VaultOperations {

    Future<VaultResult> read(String path);

    Future<VaultResult> write(String path, JsonObject keys);

    Future<VaultResult> delete(String path);
}
