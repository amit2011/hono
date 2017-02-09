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

package org.eclipse.hono.authentication.impl;

import java.util.Objects;

import javax.security.sasl.SaslException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.asyncsql.AsyncSQLClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;

/**
 * An authentication service that verifies username/password credentials against an SQL database.
 *
 */
@Service
@Profile("authentication-sql")
public final class SqlBasedPlainAuthenticationService extends AbstractPlainAuthenticationService {

    private static final String PWD_QUERY = "SELECT userPassword FROM hono.hono_users WHERE username = ? AND realm = ?";

    private AsyncSQLClient sqlClient;

    /**
     * Sets the SQL client to use for retrieving user credentials from the user database.
     * 
     * @param sqlClient The SQL client.
     */
    @Autowired
    public void setSqlClient(final AsyncSQLClient sqlClient) {
        this.sqlClient = Objects.requireNonNull(sqlClient);
    }

    @Override
    void verify(String authzid, String authcid, String password,
            Handler<AsyncResult<String>> authenticationResultHandler) {

        sqlClient.getConnection(conAttempt -> {
            if (conAttempt.succeeded()) {
                SQLConnection con = conAttempt.result();
                isPasswordCorrect(con, authcid, password, verificationAttempt -> {
                    con.close();
                    if (verificationAttempt.succeeded()) {
                        authenticationResultHandler.handle(Future.succeededFuture(authcid));
                    } else {
                        authenticationResultHandler.handle(Future.failedFuture(verificationAttempt.cause()));
                    }
                });
            } else {
                log.warn("cannot access authentication DB", conAttempt.cause());
                authenticationResultHandler.handle(Future.failedFuture(new SaslException("cannot verify PLAIN credentials")));
            }
        });
    }

    private void isPasswordCorrect(final SQLConnection con, final String authcid, final String password, final Handler<AsyncResult<Void>> verificationHandler) {

        String[] username = authcid.split("@", 2);
        if (username.length != 2) {
            verificationHandler.handle(Future.failedFuture(new SaslException("authentication ID must contain realm")));
        } else {
            JsonArray params = new JsonArray().add(username[0]).add(username[1]);
            con.queryWithParams(PWD_QUERY, params, query -> {
                if (query.succeeded()) {
                    ResultSet result = query.result();
                    if (result.getNumRows() != 1) {
                        verificationHandler.handle(Future.failedFuture(new SaslException("no such user")));
                    } else {
                        String pwd = result.getResults().get(0).getString(0);
                        if (pwd.equals(password)) {
                            verificationHandler.handle(Future.succeededFuture());
                        } else {
                            verificationHandler.handle(Future.failedFuture(new SaslException("wrong password")));
                        }
                    }
                }
            });
        }
    }
}
