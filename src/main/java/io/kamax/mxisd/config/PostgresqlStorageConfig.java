/*
 * mxisd - Matrix Identity Server Daemon
 * Copyright (C) 2017 Kamax Sarl
 *
 * https://www.kamax.io/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.kamax.mxisd.config;

public class PostgresqlStorageConfig implements DatabaseStorageConfig {

    private String database;

    private String username;

    private String password;

    private boolean pool;

    private int maxConnectionsFree = 1;

    private long maxConnectionAgeMillis = 60 * 60 * 1000;

    private long checkConnectionsEveryMillis = 30 * 1000;

    private boolean testBeforeGetFromPool = false;

    @Override
    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isPool() {
        return pool;
    }

    public void setPool(boolean pool) {
        this.pool = pool;
    }

    public int getMaxConnectionsFree() {
        return maxConnectionsFree;
    }

    public void setMaxConnectionsFree(int maxConnectionsFree) {
        this.maxConnectionsFree = maxConnectionsFree;
    }

    public long getMaxConnectionAgeMillis() {
        return maxConnectionAgeMillis;
    }

    public void setMaxConnectionAgeMillis(long maxConnectionAgeMillis) {
        this.maxConnectionAgeMillis = maxConnectionAgeMillis;
    }

    public long getCheckConnectionsEveryMillis() {
        return checkConnectionsEveryMillis;
    }

    public void setCheckConnectionsEveryMillis(long checkConnectionsEveryMillis) {
        this.checkConnectionsEveryMillis = checkConnectionsEveryMillis;
    }

    public boolean isTestBeforeGetFromPool() {
        return testBeforeGetFromPool;
    }

    public void setTestBeforeGetFromPool(boolean testBeforeGetFromPool) {
        this.testBeforeGetFromPool = testBeforeGetFromPool;
    }
}
