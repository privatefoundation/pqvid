package io.kamax.mxisd.http.undertow.handler.term.v2;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.kamax.mxisd.auth.AccountManager;
import io.kamax.mxisd.exception.InvalidCredentialsException;
import io.kamax.mxisd.http.undertow.handler.BasicHttpHandler;
import io.kamax.mxisd.storage.ormlite.dao.AccountDao;
import io.undertow.server.HttpServerExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AcceptTermsHandler extends BasicHttpHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AcceptTermsHandler.class);

    public static final String PATH = "/_matrix/identity/v2/terms";

    private final AccountManager accountManager;

    public AcceptTermsHandler(AccountManager accountManager) {
        this.accountManager = accountManager;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        String token = getAccessToken(exchange);

        JsonObject request = parseJsonObject(exchange);
        JsonElement accepts = request.get("user_accepts");
        AccountDao account = accountManager.findAccount(token);

        if (account == null) {
            throw new InvalidCredentialsException();
        }

        if (accepts == null || accepts.isJsonNull()) {
            respondJson(exchange, "{}");
            return;
        }

        if (accepts.isJsonArray()) {
            for (JsonElement acceptItem : accepts.getAsJsonArray()) {
                String termUrl = acceptItem.getAsString();
                LOGGER.info("User {} accepts the term: {}", account.getUserId(), termUrl);
                accountManager.acceptTerm(token, termUrl);
            }
        } else {
            String termUrl = accepts.getAsString();
            LOGGER.info("User {} accepts the term: {}", account.getUserId(), termUrl);
            accountManager.acceptTerm(token, termUrl);
        }

        respondJson(exchange, "{}");
    }
}
