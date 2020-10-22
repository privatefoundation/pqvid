/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.kamax.mxisd.http.undertow.handler;

import io.kamax.mxisd.http.undertow.conduit.ConduitWithDump;
import io.kamax.mxisd.http.undertow.conduit.DebuggingStreamSinkConduit;
import io.kamax.mxisd.http.undertow.conduit.DebuggingStreamSourceConduit;
import io.kamax.mxisd.http.undertow.conduit.LazyConduitWrapper;
import io.undertow.security.api.SecurityContext;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.util.ConduitFactory;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import io.undertow.util.LocaleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.conduits.StreamSinkConduit;
import org.xnio.conduits.StreamSourceConduit;

import java.util.Deque;
import java.util.Iterator;
import java.util.Map;

/**
 * Handler that dumps a exchange to a log.
 *
 * @author Stuart Douglas
 */
public class RequestDumpingHandler implements HttpHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestDumpingHandler.class);

    private final HttpHandler next;

    public RequestDumpingHandler(HttpHandler next) {
        this.next = next;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        LazyConduitWrapper<StreamSourceConduit> requestConduitWrapper = new LazyConduitWrapper<StreamSourceConduit>() {
            @Override
            protected StreamSourceConduit create(ConduitFactory<StreamSourceConduit> factory, HttpServerExchange exchange) {
                return new DebuggingStreamSourceConduit(factory.create());
            }
        };
        LazyConduitWrapper<StreamSinkConduit> responseConduitWrapper = new LazyConduitWrapper<StreamSinkConduit>() {
            @Override
            protected StreamSinkConduit create(ConduitFactory<StreamSinkConduit> factory, HttpServerExchange exchange) {
                return new DebuggingStreamSinkConduit(factory.create());
            }
        };
        exchange.addRequestWrapper(requestConduitWrapper);
        exchange.addResponseWrapper(responseConduitWrapper);

        final StringBuilder sb = new StringBuilder();
// Log pre-service information
        final SecurityContext sc = exchange.getSecurityContext();
        sb.append("\n----------------------------REQUEST---------------------------\n");
        sb.append("               URI=").append(exchange.getRequestURI()).append("\n");
        sb.append(" characterEncoding=").append(exchange.getRequestHeaders().get(Headers.CONTENT_ENCODING)).append("\n");
        sb.append("     contentLength=").append(exchange.getRequestContentLength()).append("\n");
        sb.append("       contentType=").append(exchange.getRequestHeaders().get(Headers.CONTENT_TYPE)).append("\n");
        //sb.append("       contextPath=" + exchange.getContextPath());
        if (sc != null) {
            if (sc.isAuthenticated()) {
                sb.append("          authType=").append(sc.getMechanismName()).append("\n");
                sb.append("         principle=").append(sc.getAuthenticatedAccount().getPrincipal()).append("\n");
            } else {
                sb.append("          authType=none\n");
            }
        }

        Map<String, Cookie> cookies = exchange.getRequestCookies();
        if (cookies != null) {
            for (Map.Entry<String, Cookie> entry : cookies.entrySet()) {
                Cookie cookie = entry.getValue();
                sb.append("            cookie=").append(cookie.getName()).append("=").append(cookie.getValue()).append("\n");
            }
        }
        for (HeaderValues header : exchange.getRequestHeaders()) {
            for (String value : header) {
                sb.append("            header=").append(header.getHeaderName()).append("=").append(value).append("\n");
            }
        }
        sb.append("            locale=").append(LocaleUtils.getLocalesFromHeader(exchange.getRequestHeaders().get(Headers.ACCEPT_LANGUAGE)))
            .append("\n");
        sb.append("            method=").append(exchange.getRequestMethod()).append("\n");
        Map<String, Deque<String>> pnames = exchange.getQueryParameters();
        for (Map.Entry<String, Deque<String>> entry : pnames.entrySet()) {
            String pname = entry.getKey();
            Iterator<String> pvalues = entry.getValue().iterator();
            sb.append("         parameter=");
            sb.append(pname);
            sb.append('=');
            while (pvalues.hasNext()) {
                sb.append(pvalues.next());
                if (pvalues.hasNext()) {
                    sb.append(", ");
                }
            }
            sb.append("\n");
        }
        //sb.append("          pathInfo=" + exchange.getPathInfo());
        sb.append("          protocol=").append(exchange.getProtocol()).append("\n");
        sb.append("       queryString=").append(exchange.getQueryString()).append("\n");
        sb.append("        remoteAddr=").append(exchange.getSourceAddress()).append("\n");
        sb.append("        remoteHost=").append(exchange.getSourceAddress().getHostName()).append("\n");
        //sb.append("requestedSessionId=" + exchange.getRequestedSessionId());
        sb.append("            scheme=").append(exchange.getRequestScheme()).append("\n");
        sb.append("              host=").append(exchange.getRequestHeaders().getFirst(Headers.HOST)).append("\n");
        sb.append("        serverPort=").append(exchange.getDestinationAddress().getPort()).append("\n");
        //sb.append("       servletPath=" + exchange.getServletPath());
        sb.append("          isSecure=").append(exchange.isSecure()).append("\n");

        exchange.addExchangeCompleteListener((exchange1, nextListener) -> {
            StreamSourceConduit sourceConduit = requestConduitWrapper.get();
            if (sourceConduit instanceof ConduitWithDump) {
                ConduitWithDump conduitWithDump = (ConduitWithDump) sourceConduit;
                sb.append("body=\n");
                sb.append(conduitWithDump.dump()).append("\n");
            }

            // Log post-service information
            sb.append("--------------------------RESPONSE--------------------------\n");
            if (sc != null) {
                if (sc.isAuthenticated()) {
                    sb.append("          authType=").append(sc.getMechanismName()).append("\n");
                    sb.append("         principle=").append(sc.getAuthenticatedAccount().getPrincipal()).append("\n");
                } else {
                    sb.append("          authType=none\n");
                }
            }
            sb.append("     contentLength=").append(exchange1.getResponseContentLength()).append("\n");
            sb.append("       contentType=").append(exchange1.getResponseHeaders().getFirst(Headers.CONTENT_TYPE)).append("\n");
            Map<String, Cookie> cookies1 = exchange1.getResponseCookies();
            if (cookies1 != null) {
                for (Cookie cookie : cookies1.values()) {
                    sb.append("            cookie=").append(cookie.getName()).append("=").append(cookie.getValue()).append("; domain=")
                        .append(cookie.getDomain()).append("; path=").append(cookie.getPath()).append("\n");
                }
            }
            for (HeaderValues header : exchange1.getResponseHeaders()) {
                for (String value : header) {
                    sb.append("            header=").append(header.getHeaderName()).append("=").append(value).append("\n");
                }
            }
            sb.append("            status=").append(exchange1.getStatusCode()).append("\n");
            StreamSinkConduit streamSinkConduit = responseConduitWrapper.get();
            if (streamSinkConduit instanceof ConduitWithDump) {
                ConduitWithDump conduitWithDump = (ConduitWithDump) streamSinkConduit;
                sb.append("body=\n");
                sb.append(conduitWithDump.dump());

            }

            sb.append("\n==============================================================");


            nextListener.proceed();
            LOGGER.info(sb.toString());
        });


        // Perform the exchange
        next.handleRequest(exchange);
    }
}
