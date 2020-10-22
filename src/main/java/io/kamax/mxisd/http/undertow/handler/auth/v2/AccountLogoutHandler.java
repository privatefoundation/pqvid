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

package io.kamax.mxisd.http.undertow.handler.auth.v2;

import io.kamax.mxisd.auth.AccountManager;
import io.kamax.mxisd.exception.InvalidCredentialsException;
import io.kamax.mxisd.http.undertow.handler.BasicHttpHandler;
import io.undertow.server.HttpServerExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AccountLogoutHandler extends BasicHttpHandler {

    public static final String Path = "/_matrix/identity/v2/account/logout";

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountLogoutHandler.class);

    private final AccountManager accountManager;

    public AccountLogoutHandler(AccountManager accountManager) {
        this.accountManager = accountManager;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) {
        LOGGER.info("Logout.");
        String token = findAccessToken(exchange).orElseThrow(InvalidCredentialsException::new);

        accountManager.logout(token);

        respondJson(exchange, "{}");
    }
}
