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

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;

/**
 * A non-blocking client for managing secrets in <em>Vault</em>.
 *
 */
@Component
@Profile("registration-vault")
@ConfigurationProperties(prefix = "hono.registration.vault")
public class VaultClient implements VaultOperations {

    private static final String CONTENT_TYPE_JSON_UTF8 = "application/json; charset=utf-8";
    private static final String HEADER_X_VAULT_TOKEN = "X-Vault-Token";
    private static final String SECRET_PATH_TEMPLATE = "/v1/%s/%s";
    private static final Logger LOG = LoggerFactory.getLogger(VaultClient.class);
    private static final String PATH_AUTH_APPROLE_LOGIN = "/v1/auth/approle/login";
    private Vertx vertx;
    private String roleId;
    private String secretId;
    private String token;
    private String secretPathPrefix = "secret";
    private long tokenLeaseDuration = -1;
    private String host;
    private int port = 8200;
    private HttpClient httpClient;

    /**
     * @param vertx the vertx to set
     */
    @Autowired
    public final void setVertx(final Vertx vertx) {
        this.vertx = vertx;
    }

    /**
     * @param roleId the roleId to set
     */
    public final void setRoleId(final String roleId) {
        this.roleId = roleId;
    }

    /**
     * @param secretId the secretId to set
     */
    public final void setSecretId(final String secretId) {
        this.secretId = secretId;
    }

    /**
     * @param token the token to set
     */
    public final void setToken(final String token) {
        this.token = token;
    }

    /**
     * @param host the host to set
     */
    public final void setHost(final String host) {
        this.host = host;
    }

    /**
     * @param port the port to set
     */
    public final void setPort(final int port) {
        this.port = port;
    }

    /**
     * @param secretPathPrefix the secretPathPrefix to set
     */
    public final void setSecretPathPrefix(String secretPathPrefix) {
        this.secretPathPrefix = secretPathPrefix;
    }

    /**
     * Creates the HTTP client for accessing the <em>Vault</em> server.
     */
    @PostConstruct
    public void createHttpClient() {

        HttpClientOptions options = new HttpClientOptions().setDefaultHost(host).setDefaultPort(port);
        httpClient = vertx.createHttpClient(options);
    }

    private Future<String> getToken() {

        if (token != null) {
            LOG.debug("reusing existing token");
            return Future.succeededFuture(token);
        } else {
            LOG.debug("logging in using AppRole mechanism");
            Future<String> result = Future.future();

            JsonObject params = new JsonObject().put("role_id", roleId).put("secret_id", secretId);

            httpClient.post(PATH_AUTH_APPROLE_LOGIN)
                .putHeader(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE_JSON_UTF8)
                .exceptionHandler(fault -> {
                    LOG.error("could not authenticate to Vault server", fault);
                    result.fail(fault);
                }).handler(resp -> {
                    resp.bodyHandler(bodyBuffer -> {
                        try {
                            JsonObject json = bodyBuffer.toJsonObject();
                            if (resp.statusCode() == HTTP_OK) {
                                LOG.debug("AppRole login succeeded");
                                handleAuthData(json.getJsonObject("auth"));
                                result.complete(token);
                            } else {
                                LOG.error("AppRole login failed: {}", json.getString("warnings"));
                                result.fail("failed to authenticate with Vault");
                            }
                        } catch (final DecodeException e) {
                            result.fail(e);
                        }
                    });
                }).end(params.encode());
            return result;
        }
    }

    private void handleAuthData(final JsonObject auth) {

        token = auth.getString("client_token");
        tokenLeaseDuration = auth.getLong("lease_duration");
        scheduleTokenRenewal(tokenLeaseDuration);
    }

    private void scheduleTokenRenewal(long leaseDuration) {

        long tokenRenewalMillis = Math.round(leaseDuration * 700); // try to renew after 70% of the lease duration has expired
        LOG.debug("scheduling token renewal in {} seconds", tokenRenewalMillis);

        vertx.setTimer(tokenRenewalMillis, id -> {

            Future<JsonObject> result = Future.future();
            result.setHandler(renewalAttempt -> {
                if (renewalAttempt.succeeded()) {
                    LOG.debug("token renewal succeeded");
                    handleAuthData(renewalAttempt.result());
                } else {
                    LOG.error("token renewal failed, retiring token...", renewalAttempt.cause());
                    token = null;
                }
            });

            LOG.debug("trying to renew Vault token...");

            JsonObject params = new JsonObject().put("increment", leaseDuration);

            httpClient.post("/v1/auth/token/renew-self")
                .putHeader(HEADER_X_VAULT_TOKEN, token)
                .putHeader(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE_JSON_UTF8)
                .exceptionHandler(fault -> {
                    result.fail(fault);
                }).handler(resp -> {
                    resp.bodyHandler(bodyBuffer -> {
                        try {
                            JsonObject json = bodyBuffer.toJsonObject();
                            if (resp.statusCode() == HTTP_OK) {
                                result.complete(json.getJsonObject("auth"));
                            } else {
                                String errors = json.getString("errors");
                                result.fail(errors);
                            }
                        } catch (final DecodeException e) {
                            result.fail(e);
                        }
                    });
                }).end(params.encode());
        });
    }

    private String getPathForSecret(final String relativeSecretPath) {
        return String.format(SECRET_PATH_TEMPLATE, secretPathPrefix, relativeSecretPath);
    }

    @Override
    public Future<VaultResult> read(final String relativeSecretPath) {

        Objects.requireNonNull(relativeSecretPath);
        final String path = getPathForSecret(relativeSecretPath);
        Future<VaultResult> result = Future.future();

        getToken().compose(authToken -> {
            httpClient.get(path)
                .putHeader(HEADER_X_VAULT_TOKEN, authToken)
                .exceptionHandler(fault -> {
                    LOG.error("could not read secret from Vault", fault);
                    result.fail("could not read secret from Vault");
                }).handler(resp -> {
                    resp.bodyHandler(bodyBuffer -> {
                        try {
                            JsonObject json = bodyBuffer.toJsonObject();
                            result.complete(VaultResult.result(resp.statusCode(), json));
                        } catch (final DecodeException e) {
                            LOG.error("cannot parse response from Vault", e);
                            result.fail("cannot parse response from Vault");
                        }
                    });
                }).end();
        }, result);
        return result;
    }

    @Override
    public Future<VaultResult> write(final String relativeSecretPath, final JsonObject keys) {

        Objects.requireNonNull(relativeSecretPath);
        final String path = getPathForSecret(relativeSecretPath);

        Future<VaultResult> result = Future.future();

        getToken().compose(authToken -> {
            HttpClientRequest req = httpClient.post(path)
                .putHeader(HEADER_X_VAULT_TOKEN, authToken)
                .exceptionHandler(fault -> {
                    LOG.error("could not write secret to Vault", fault);
                    result.fail("could not write secret to Vault");
                }).handler(resp -> {
                    if (resp.statusCode() == HTTP_NO_CONTENT) {
                        // write succeeded
                        result.complete(VaultResult.result(resp.statusCode()));
                    } else {
                        resp.bodyHandler(bodyBuffer -> {
                            try {
                                JsonObject json = bodyBuffer.toJsonObject();
                                result.complete(VaultResult.result(resp.statusCode(), json));
                            } catch (final DecodeException e) {
                                LOG.error("cannot parse response from Vault", e);
                                result.fail("cannot parse response from Vault");
                            }
                        });
                    }
                });
            if (keys != null && !keys.isEmpty()) {
                req.putHeader(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE_JSON_UTF8).end(keys.encode());
            } else {
                req.end();
            }
        }, result);
        return result;
    }

    @Override
    public Future<VaultResult> delete(final String relativeSecretPath) {

        Objects.requireNonNull(relativeSecretPath);
        final String path = getPathForSecret(relativeSecretPath);

        Future<VaultResult> result = Future.future();

        getToken().compose(authToken -> {
            httpClient.delete(path)
                .putHeader(HEADER_X_VAULT_TOKEN, authToken)
                .exceptionHandler(fault -> {
                    LOG.error("could not delete secret from Vault", fault);
                    result.fail("could not delete secret from Vault");
                }).handler(resp -> {
                    if (resp.statusCode() == HTTP_NO_CONTENT) {
                        result.complete(VaultResult.result(resp.statusCode()));
                    } else {
                        resp.bodyHandler(bodyBuffer -> {
                            try {
                                JsonObject json = bodyBuffer.toJsonObject();
                                result.complete(VaultResult.result(resp.statusCode(), json));
                            } catch (final DecodeException e) {
                                LOG.error("cannot parse response from Vault", e);
                                result.fail("cannot parse response from Vault");
                            }
                        });
                    }
                }).end();
        }, result);
        return result;
    }
}
