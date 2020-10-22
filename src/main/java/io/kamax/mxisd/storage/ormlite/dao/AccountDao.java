/*
 * mxisd - Matrix Identity Server Daemon
 * Copyright (C) 2018 Kamax Sarl
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

package io.kamax.mxisd.storage.ormlite.dao;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "account")
public class AccountDao {

    @DatabaseField(canBeNull = false, id = true)
    private String token;

    @DatabaseField(canBeNull = false)
    private String accessToken;

    @DatabaseField(canBeNull = false)
    private String tokenType;

    @DatabaseField(canBeNull = false)
    private String matrixServerName;

    @DatabaseField(canBeNull = false)
    private long expiresIn;

    @DatabaseField(canBeNull = false)
    private long createdAt;

    @DatabaseField(canBeNull = false)
    private String userId;

    public AccountDao() {
        // Needed for ORMLite
    }

    public AccountDao(String accessToken, String tokenType, String matrixServerName, long expiresIn, long createdAt, String userId, String token) {
        this.accessToken = accessToken;
        this.tokenType = tokenType;
        this.matrixServerName = matrixServerName;
        this.expiresIn = expiresIn;
        this.createdAt = createdAt;
        this.userId = userId;
        this.token = token;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public String getMatrixServerName() {
        return matrixServerName;
    }

    public void setMatrixServerName(String matrixServerName) {
        this.matrixServerName = matrixServerName;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
