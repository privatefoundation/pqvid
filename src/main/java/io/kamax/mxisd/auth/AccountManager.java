package io.kamax.mxisd.auth;

import com.google.gson.JsonObject;
import io.kamax.matrix.MatrixID;
import io.kamax.matrix.json.GsonUtil;
import io.kamax.mxisd.config.AccountConfig;
import io.kamax.mxisd.config.MatrixConfig;
import io.kamax.mxisd.config.PolicyConfig;
import io.kamax.mxisd.exception.BadRequestException;
import io.kamax.mxisd.exception.InvalidCredentialsException;
import io.kamax.mxisd.exception.NotFoundException;
import io.kamax.mxisd.matrix.HomeserverFederationResolver;
import io.kamax.mxisd.matrix.HomeserverVerifier;
import io.kamax.mxisd.storage.IStorage;
import io.kamax.mxisd.storage.ormlite.dao.AccountDao;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class AccountManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountManager.class);

    private final IStorage storage;
    private final HomeserverFederationResolver resolver;
    private final AccountConfig accountConfig;
    private final MatrixConfig matrixConfig;

    public AccountManager(IStorage storage, HomeserverFederationResolver resolver, AccountConfig accountConfig, MatrixConfig matrixConfig) {
        this.storage = storage;
        this.resolver = resolver;
        this.accountConfig = accountConfig;
        this.matrixConfig = matrixConfig;
    }

    public String register(OpenIdToken openIdToken) {
        Objects.requireNonNull(openIdToken.getAccessToken(), "Missing required access_token");
        Objects.requireNonNull(openIdToken.getTokenType(), "Missing required token type");
        Objects.requireNonNull(openIdToken.getMatrixServerName(), "Missing required matrix domain");

        LOGGER.info("Registration from the server: {}", openIdToken.getMatrixServerName());
        String userId = getUserId(openIdToken);
        LOGGER.info("UserId: {}", userId);

        String token = UUID.randomUUID().toString();
        AccountDao account = new AccountDao(openIdToken.getAccessToken(), openIdToken.getTokenType(),
            openIdToken.getMatrixServerName(), openIdToken.getExpiresIn(),
            Instant.now().getEpochSecond(), userId, token);
        storage.insertToken(account);

        LOGGER.info("User {} registered", userId);

        return token;
    }

    private String getUserId(OpenIdToken openIdToken) {
        String matrixServerName = openIdToken.getMatrixServerName();
        HomeserverFederationResolver.HomeserverTarget homeserverTarget = resolver.resolve(matrixServerName);
        String homeserverURL = homeserverTarget.getUrl().toString();
        LOGGER.info("Domain resolved: {} => {}", matrixServerName, homeserverURL);
        HttpGet getUserInfo = new HttpGet(
            homeserverURL + "/_matrix/federation/v1/openid/userinfo?access_token=" + openIdToken.getAccessToken());
        String userId;
        try (CloseableHttpClient httpClient = HttpClients.custom()
            .setSSLHostnameVerifier(new HomeserverVerifier(homeserverTarget.getDomain())).build()) {
            try (CloseableHttpResponse response = httpClient.execute(getUserInfo)) {
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode == HttpStatus.SC_OK) {
                    String content = EntityUtils.toString(response.getEntity());
                    LOGGER.trace("Response: {}", content);
                    JsonObject body = GsonUtil.parseObj(content);
                    userId = GsonUtil.getStringOrThrow(body, "sub");
                } else {
                    LOGGER.error("Wrong response status: {}", statusCode);
                    throw new InvalidCredentialsException();
                }
            } catch (IOException e) {
                LOGGER.error("Unable to get user info.", e);
                throw new InvalidCredentialsException();
            }
        } catch (IOException e) {
            LOGGER.error("Unable to create a connection to host: " + homeserverURL, e);
            throw new InvalidCredentialsException();
        }

        checkMXID(userId);
        return userId;
    }

    private void checkMXID(String userId) {
        MatrixID mxid;
        try {
            mxid = MatrixID.asValid(userId);
        } catch (IllegalArgumentException e) {
            LOGGER.error("Wrong MXID: " + userId, e);
            throw new BadRequestException("Wrong MXID");
        }

        if (getAccountConfig().isAllowOnlyTrustDomains()) {
            LOGGER.info("Allow registration only for trust domain.");
            if (getMatrixConfig().getDomain().equals(mxid.getDomain())) {
                LOGGER.info("Allow user {} to registration", userId);
            } else {
                LOGGER.error("Deny user {} to registration", userId);
                throw new InvalidCredentialsException();
            }
        } else {
            LOGGER.info("Allow registration from any server.");
        }
    }

    public String getUserId(String token) {
        return storage.findAccount(token).orElseThrow(NotFoundException::new).getUserId();
    }

    public AccountDao findAccount(String token) {
        AccountDao accountDao = storage.findAccount(token).orElse(null);

        if (LOGGER.isInfoEnabled()) {
            if (accountDao != null) {
                LOGGER.info("Found account for user: {}", accountDao.getUserId());
            } else {
                LOGGER.warn("Account not found.");
            }
        }
        return accountDao;
    }

    public void logout(String token) {
        String userId = storage.findAccount(token).orElseThrow(InvalidCredentialsException::new).getUserId();
        LOGGER.info("Logout: {}", userId);
        deleteAccount(token);
    }

    public void deleteAccount(String token) {
        storage.deleteAccepts(token);
        storage.deleteToken(token);
    }

    public void acceptTerm(String token, String url) {
        storage.acceptTerm(token, url);
    }

    public boolean isTermAccepted(String token, List<PolicyConfig.PolicyObject> policies) {
        return policies.isEmpty() || storage.isTermAccepted(token, policies);
    }

    public AccountConfig getAccountConfig() {
        return accountConfig;
    }

    public MatrixConfig getMatrixConfig() {
        return matrixConfig;
    }
}
