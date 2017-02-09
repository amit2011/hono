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

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

import java.sql.SQLException;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.asyncsql.AsyncSQLClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;

/**
 * Test cases verifying behavior of {@code SqlBasedPlainAuthenticationService}.
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class SqlBasedPlainAuthenticationServiceTest {

    @Mock
    AsyncSQLClient sqlClient;

    SqlBasedPlainAuthenticationService authService;

    @Before
    public void setUp() {
        authService = new SqlBasedPlainAuthenticationService();
        authService.setSqlClient(sqlClient);
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void testVerifyFailsIfSqlClientCannotConnectToDb() {

        setConnection(null, sqlClient);

        authService.verify(null, "user", "pwd", res -> {
            assertTrue(res.failed());
        });
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void testVerifyFailsIfUserDoesNotExist() {

        ResultSet result = new ResultSet().setResults(Collections.emptyList());

        SQLConnection con = mock(SQLConnection.class);
        setConnection(con, sqlClient);
        setResult(result, con);

        authService.verify(null, "user@REALM", "pwd", res -> {
            assertTrue(res.failed());
        });
        verify(con).close();
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void testVerifySucceedsForMatchingPassword() {

        ResultSet result = new ResultSet().setResults(Collections.singletonList(new JsonArray().add("pwd")));

        SQLConnection con = mock(SQLConnection.class);
        setConnection(con, sqlClient);
        setResult(result, con);

        authService.verify(null, "user@REALM", "pwd", res -> {
            assertTrue(res.succeeded());
        });
        verify(con).close();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void setConnection(final SQLConnection con, final AsyncSQLClient client) {

        doAnswer(invocation -> {
            Handler resultHandler = invocation.getArgumentAt(0, Handler.class);
            if (con == null) {
                resultHandler.handle(Future.failedFuture("no connection"));
            } else {
                resultHandler.handle(Future.succeededFuture(con));
            }
            return null;
        }).when(client).getConnection(any(Handler.class));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void setResult(final ResultSet result, final SQLConnection connection) {

        doAnswer(invocation -> {
            Handler resultHandler = invocation.getArgumentAt(2, Handler.class);
            if (result == null) {
                resultHandler.handle(Future.failedFuture(new SQLException("query failed")));
            } else {
                resultHandler.handle(Future.succeededFuture(result));
            }
            return null;
        }).when(connection).queryWithParams(anyString(), any(JsonArray.class), any(Handler.class));
    }
}
