package io.kamax.mxisd.http.undertow.handler.term.v2;

import com.google.gson.JsonObject;
import io.kamax.mxisd.config.PolicyConfig;
import io.kamax.mxisd.http.undertow.handler.BasicHttpHandler;
import io.undertow.server.HttpServerExchange;

import java.util.Map;

public class GetTermsHandler extends BasicHttpHandler {

    public static final String PATH = "/_matrix/identity/v2/terms";

    private final JsonObject policyResponse;

    public GetTermsHandler(PolicyConfig config) {
        policyResponse = new JsonObject();
        JsonObject policies = new JsonObject();
        for (Map.Entry<String, PolicyConfig.PolicyObject> policyItem : config.getPolicies().entrySet()) {
            JsonObject policy = new JsonObject();
            policy.addProperty("version", policyItem.getValue().getVersion());
            for (Map.Entry<String, PolicyConfig.TermObject> termEntry : policyItem.getValue().getTerms().entrySet()) {
                JsonObject term = new JsonObject();
                term.addProperty("name", termEntry.getValue().getName());
                term.addProperty("url", termEntry.getValue().getUrl());
                policy.add(termEntry.getKey(), term);
            }
            policies.add(policyItem.getKey(), policy);
        }
        policyResponse.add("policies", policies);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        respond(exchange, policyResponse);
    }
}
