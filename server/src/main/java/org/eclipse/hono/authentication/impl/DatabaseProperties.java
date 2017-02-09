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

/**
 * Basic properties required for connecting to a database.
 *
 */
public class DatabaseProperties {

    private String host;
    private int port;
    private String username;
    private String password;
    private String database;

    /**
     * @return the host
     */
    public final String getHost() {
        return host;
    }
    /**
     * @param host the host to set
     */
    public final void setHost(String host) {
        this.host = host;
    }
    /**
     * @return the port
     */
    public final int getPort() {
        return port;
    }
    /**
     * @param port the port to set
     */
    public final void setPort(int port) {
        this.port = port;
    }
    /**
     * @return the username
     */
    public final String getUsername() {
        return username;
    }
    /**
     * @param username the username to set
     */
    public final void setUsername(String username) {
        this.username = username;
    }
    /**
     * @return the password
     */
    public final String getPassword() {
        return password;
    }
    /**
     * @param password the password to set
     */
    public final void setPassword(String password) {
        this.password = password;
    }
    /**
     * @return the database
     */
    public final String getDatabase() {
        return database;
    }
    /**
     * @param database the database to set
     */
    public final void setDatabase(String database) {
        this.database = database;
    }

}
