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

import static java.net.HttpURLConnection.*;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.hono.registration.impl.BaseRegistrationService;
import org.eclipse.hono.util.RegistrationResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

/**
 * A registration service that keeps all data in <a href="https://www.vaultproject.io">Vault</a> instance.
 *
 */
@Repository
@Profile("registration-vault")
public class VaultRegistrationService extends BaseRegistrationService {

    private static final String PATH_TEMPLATE = "registration/%s/%s";
    private VaultOperations vault;

    /**
     * Sets the client to use for interacting with the Vault server.
     * 
     * @param vaultOps The client.
     * @throws NullPointerException if vaultOps is {@code null}.
     */
    @Autowired
    public void setVaultOperations(final VaultOperations vaultOps) {
        this.vault = Objects.requireNonNull(vaultOps);
    }

    @Override
    protected void doStart(final Future<Void> startFuture) throws Exception {

        if (vault == null) {
            startFuture.fail("Vault client needs to be set before starting service");
        } else {
            startFuture.complete();;
        }
    }

    @Override
    public void getDevice(final String tenantId, final String deviceId, final Handler<AsyncResult<RegistrationResult>> resultHandler) {

        Future<RegistrationResult> result = Future.future();
        result.setHandler(resultHandler);

        vault.read(String.format(PATH_TEMPLATE, tenantId, deviceId)).compose(readResult -> {
            JsonObject data = readResult.getPayload();
            if (readResult.getStatusCode() == HTTP_OK) {
                // extract data from Vault response
                data = readResult.getPayload().getJsonObject(FIELD_DATA);
            }
            resultHandler.handle(Future.succeededFuture(
                    RegistrationResult.from(readResult.getStatusCode(), getResultPayload(deviceId, data))));
        }, result);
    }

    @Override
    public void findDevice(final String tenantId, final String key, final String value,
            final Handler<AsyncResult<RegistrationResult>> resultHandler) {

        resultHandler.handle(Future.failedFuture(new UnsupportedOperationException()));
    }

    @Override
    public void addDevice(final String tenantId, final String deviceId, final JsonObject otherKeys,
            final Handler<AsyncResult<RegistrationResult>> resultHandler) {

        Future<RegistrationResult> result = Future.future();
        result.setHandler(resultHandler);

        final String path = String.format(PATH_TEMPLATE,  tenantId, deviceId);
        vault.read(path).compose(readResult -> {
            if (readResult.getStatusCode() == HTTP_OK) {
                return Future.succeededFuture(VaultResult.result(HTTP_CONFLICT));
            } else {
                return vault.write(path, otherKeys);
            }
        }).compose(writeResult -> {
            if (writeResult.getStatusCode() == HTTP_NO_CONTENT) {
                result.complete(RegistrationResult.from(HTTP_CREATED));
            } else {
                result.complete(RegistrationResult.from(writeResult.getStatusCode(), writeResult.getPayload()));
            }
        }, result);
    }

    @Override
    public void updateDevice(String tenantId, String deviceId, JsonObject otherKeys,
            Handler<AsyncResult<RegistrationResult>> resultHandler) {

        Future<RegistrationResult> result = Future.future();
        result.setHandler(resultHandler);
        final AtomicReference<JsonObject> data = new AtomicReference<>();

        String path = String.format(PATH_TEMPLATE, tenantId, deviceId);
        vault.read(path).compose(readResult -> {
            if (readResult.getStatusCode() == HTTP_NOT_FOUND) {
                return Future.succeededFuture(VaultResult.result(HTTP_NOT_FOUND));
            } else {
                data.set(readResult.getPayload());
                return vault.write(path, otherKeys);
            }
        }).compose(writeResult -> {
            if (writeResult.getStatusCode() == HTTP_NO_CONTENT) {
                // update succeeded
                result.complete(
                        RegistrationResult.from(
                                HTTP_OK,
                                getResultPayload(deviceId, data.get().getJsonObject(FIELD_DATA))));
            } else {
                result.complete(RegistrationResult.from(writeResult.getStatusCode(), writeResult.getPayload()));
            }
        }, result);
    }

    @Override
    public void removeDevice(String tenantId, String deviceId, Handler<AsyncResult<RegistrationResult>> resultHandler) {

        Future<RegistrationResult> result = Future.future();
        result.setHandler(resultHandler);
        final AtomicReference<JsonObject> data = new AtomicReference<>();

        String path = String.format(PATH_TEMPLATE, tenantId, deviceId);
        vault.read(path).compose(readResult -> {
            if (readResult.getStatusCode() == HTTP_NOT_FOUND) {
                return Future.succeededFuture(VaultResult.result(HTTP_NOT_FOUND));
            } else {
                data.set(readResult.getPayload());
                return vault.delete(path);
            }
        }).compose(deleteResult -> {
            if (deleteResult.getStatusCode() == HTTP_NO_CONTENT) {
                result.complete(
                        RegistrationResult.from(
                                HTTP_OK,
                                getResultPayload(deviceId, data.get().getJsonObject(FIELD_DATA))));
            } else {
                result.complete(RegistrationResult.from(deleteResult.getStatusCode(), deleteResult.getPayload()));
            }
        }, result);
    }
}
