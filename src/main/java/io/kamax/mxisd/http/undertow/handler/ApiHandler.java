package io.kamax.mxisd.http.undertow.handler;

import io.kamax.mxisd.http.IsAPIv1;
import io.kamax.mxisd.http.IsAPIv2;
import io.kamax.mxisd.matrix.IdentityServiceAPI;
import io.undertow.server.HttpHandler;

public interface ApiHandler extends HttpHandler {

    default String getPath(IdentityServiceAPI api) {
        switch (api) {
            case V2:
                return IsAPIv2.Base + getHandlerPath();
            case V1:
                return IsAPIv1.Base + getHandlerPath();
            default:
                throw new IllegalArgumentException("Unknown api version: " + api);
        }
    }

    String getHandlerPath();
}
