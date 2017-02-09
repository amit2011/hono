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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.asyncsql.AsyncSQLClient;
import io.vertx.ext.asyncsql.MySQLClient;

/**
 * Configuration for authentication database.
 *
 */
@Configuration
@Profile("authentication-sql")
public class AuthDbConfig {

    @Bean
    @ConfigurationProperties(prefix = "hono.authentication.db")
    DatabaseProperties getAuthDbConfiguration() {
        return new DatabaseProperties();
    }

    /**
     * Exposes a client for a MySQL DB as a Spring bean.
     * 
     * @param vertx The vert.x instance to use with the client.
     * @param dbProps The properties of the database to connect to.
     * @return The client.
     */
    @Bean
    public AsyncSQLClient getSqlClient(@Autowired final Vertx vertx, @Autowired final DatabaseProperties dbProps) {

        JsonObject config = new JsonObject()
                .put("host", dbProps.getHost())
                .put("port", dbProps.getPort())
                .put("username", dbProps.getUsername())
                .put("password", dbProps.getPassword())
                .put("database",  dbProps.getDatabase());

        return MySQLClient.createShared(vertx, config);
    }

}
