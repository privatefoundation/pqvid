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

package io.kamax.mxisd.http.undertow.handler;

import io.kamax.mxisd.auth.AccountManager;
import io.kamax.mxisd.config.PolicyConfig;
import io.kamax.mxisd.exception.InvalidCredentialsException;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class CheckTermsHandler extends BasicHttpHandler {

    private static final Logger log = LoggerFactory.getLogger(CheckTermsHandler.class);

    private final AccountManager accountManager;

    private final HttpHandler child;

    private final List<PolicyConfig.PolicyObject> policies;

    public static CheckTermsHandler around(AccountManager accountManager, HttpHandler child, List<PolicyConfig.PolicyObject> policies) {
        return new CheckTermsHandler(accountManager, child, policies);
    }

    private CheckTermsHandler(AccountManager accountManager, HttpHandler child,
                              List<PolicyConfig.PolicyObject> policies) {
        this.accountManager = accountManager;
        this.child = child;
        this.policies = policies;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (policies == null || policies.isEmpty()) {
            child.handleRequest(exchange);
            return;
        }

        String token = findAccessToken(exchange).orElse(null);
        if (token == null) {
            log.error("Unauthorized request from: {}", exchange.getHostAndPort());
            throw new InvalidCredentialsException();
        }

        if (!accountManager.isTermAccepted(token, policies)) {
            log.error("Non accepting request from: {}", exchange.getHostAndPort());
            throw new InvalidCredentialsException();
        }
        log.trace("Access granted");
        child.handleRequest(exchange);
    }
}
