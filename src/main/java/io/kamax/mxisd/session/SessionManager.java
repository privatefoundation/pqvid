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

package io.kamax.mxisd.session;

import static io.kamax.mxisd.config.SessionConfig.Policy.PolicyTemplate;

import com.google.gson.JsonObject;
import io.kamax.matrix.MatrixID;
import io.kamax.matrix.ThreePid;
import io.kamax.matrix._MatrixID;
import io.kamax.matrix.json.GsonUtil;
import io.kamax.matrix.json.MatrixJson;
import io.kamax.mxisd.config.MxisdConfig;
import io.kamax.mxisd.crypto.SignatureManager;
import io.kamax.mxisd.exception.BadRequestException;
import io.kamax.mxisd.exception.NotAllowedException;
import io.kamax.mxisd.exception.RemoteHomeServerException;
import io.kamax.mxisd.exception.SessionNotValidatedException;
import io.kamax.mxisd.exception.SessionUnknownException;
import io.kamax.mxisd.lookup.SingleLookupReply;
import io.kamax.mxisd.lookup.SingleLookupRequest;
import io.kamax.mxisd.lookup.ThreePidValidation;
import io.kamax.mxisd.matrix.HomeserverFederationResolver;
import io.kamax.mxisd.matrix.HomeserverVerifier;
import io.kamax.mxisd.notification.NotificationManager;
import io.kamax.mxisd.storage.IStorage;
import io.kamax.mxisd.storage.dao.IThreePidSessionDao;
import io.kamax.mxisd.threepid.session.ThreePidSession;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveSpec;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Calendar;
import java.util.Optional;

public class SessionManager {

    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);

    private MxisdConfig cfg;
    private IStorage storage;
    private NotificationManager notifMgr;
    private HomeserverFederationResolver resolver;
    private SignatureManager signatureManager;

    public SessionManager(
        MxisdConfig cfg,
        IStorage storage,
        NotificationManager notifMgr,
        HomeserverFederationResolver resolver,
        SignatureManager signatureManager
    ) {
        this.cfg = cfg;
        this.storage = storage;
        this.notifMgr = notifMgr;
        this.resolver = resolver;
        this.signatureManager = signatureManager;
    }

    private ThreePidSession getSession(String sid, String secret) {
        Optional<IThreePidSessionDao> dao = storage.getThreePidSession(sid);
        if (!dao.isPresent() || !StringUtils.equals(dao.get().getSecret(), secret)) {
            throw new SessionUnknownException();
        }

        return new ThreePidSession(dao.get());
    }

    private ThreePidSession getSessionIfValidated(String sid, String secret) {
        ThreePidSession session = getSession(sid, secret);
        if (!session.isValidated()) {
            throw new SessionNotValidatedException();
        }
        return session;
    }

    public String create(String server, ThreePid tpid, String secret, int attempt, String nextLink) {
        PolicyTemplate policy = cfg.getSession().getPolicy().getValidation();
        if (!policy.isEnabled()) {
            throw new NotAllowedException("Validating 3PID is disabled");
        }

        synchronized (this) {
            log.info("Server {} is asking to create session for {} (Attempt #{}) - Next link: {}", server, tpid, attempt, nextLink);
            Optional<IThreePidSessionDao> dao = storage.findThreePidSession(tpid, secret);
            if (dao.isPresent()) {
                ThreePidSession session = new ThreePidSession(dao.get());
                log.info("We already have a session for {}: {}", tpid, session.getId());
                if (session.getAttempt() < attempt) {
                    log.info("Received attempt {} is greater than stored attempt {}, sending validation communication", attempt,
                        session.getAttempt());
                    notifMgr.sendForValidation(session);
                    log.info("Sent validation notification to {}", tpid);
                    session.increaseAttempt();
                    storage.updateThreePidSession(session.getDao());
                }

                return session.getId();
            } else {
                log.info("No existing session for {}", tpid);

                String sessionId;
                do {
                    sessionId = Long.toString(System.currentTimeMillis());
                } while (storage.getThreePidSession(sessionId).isPresent());

                String token = RandomStringUtils.randomNumeric(6);
                ThreePidSession session = new ThreePidSession(sessionId, server, tpid, secret, attempt, nextLink, token);
                log.info("Generated new session {} to validate {} from server {}", sessionId, tpid, server);

                storage.insertThreePidSession(session.getDao());
                log.info("Stored session {}", sessionId);

                log.info("Session {} for {}: sending validation notification", sessionId, tpid);
                notifMgr.sendForValidation(session);

                return sessionId;
            }
        }
    }

    public ValidationResult validate(String sid, String secret, String token) {
        log.info("Validating session {}", sid);
        ThreePidSession session = getSession(sid, secret);
        log.info("Session {} is from {}", session.getId(), session.getServer());

        session.validate(token);
        storage.updateThreePidSession(session.getDao());
        log.info("Session {} has been validated", session.getId());

        ValidationResult r = new ValidationResult(session);
        session.getNextLink().ifPresent(r::setNextUrl);
        return r;
    }

    public ThreePidValidation getValidated(String sid, String secret) {
        ThreePidSession session = getSessionIfValidated(sid, secret);
        return new ThreePidValidation(session.getThreePid(), session.getValidationTime());
    }

    public SingleLookupReply bind(String sid, String secret, String mxidRaw) {
        // We make sure we have an acceptable User ID
        if (StringUtils.isEmpty(mxidRaw)) {
            throw new IllegalArgumentException("No Matrix User ID provided");
        }

        // We ensure the session was validated
        ThreePidSession session = getSessionIfValidated(sid, secret);

        // We parse the Matrix ID as acceptable
        _MatrixID mxid = MatrixID.asAcceptable(mxidRaw);

        // Only accept binds if the domain matches our own
        final String domain = cfg.getMatrix().getDomain();
        if (!StringUtils.equalsIgnoreCase(domain, mxid.getDomain())) {
            throw new NotAllowedException("Only Matrix IDs from domain " + domain + " can be bound");
        }

        log.info("Session {}: Binding of {}:{} to Matrix ID {} is accepted",
            session.getId(), session.getThreePid().getMedium(), session.getThreePid().getAddress(), mxid.getId());

        SingleLookupRequest request = new SingleLookupRequest();
        request.setType(session.getThreePid().getMedium());
        request.setThreePid(session.getThreePid().getAddress());
        return new SingleLookupReply(request, mxid);
    }

    public void unbind(String auth, JsonObject reqData) {
        if (!cfg.getSession().getPolicy().getUnbind().getEnabled()) {
            log.error("Unbind disabled.");
            throw new NotAllowedException("Unbinding 3PID is disabled");
        }

        _MatrixID mxid;
        try {
            mxid = MatrixID.asAcceptable(GsonUtil.getStringOrThrow(reqData, "mxid"));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }

        String sid = GsonUtil.getStringOrNull(reqData, "sid");
        String secret = GsonUtil.getStringOrNull(reqData, "client_secret");
        ThreePid tpid = GsonUtil.get().fromJson(GsonUtil.getObj(reqData, "threepid"), ThreePid.class);

        if (tpid == null || StringUtils.isBlank(tpid.getAddress()) || StringUtils.isBlank(tpid.getMedium())) {
            throw new BadRequestException("Missing required 3PID");
        }

        // We only allow unbind for the domain we manage, mirroring bind
        final CharSequence domain = cfg.getMatrix().getDomain();
        if (!StringUtils.equalsIgnoreCase(domain, mxid.getDomain())) {
            throw new NotAllowedException("Only Matrix IDs from domain " + domain + " can be unbound");
        }

        log.info("Request was authorized.");
        if (StringUtils.isNotBlank(sid) && StringUtils.isNotBlank(secret)) {
            checkSession(sid, secret, tpid);
        } else if (StringUtils.isNotBlank(auth)) {
            checkAuthorization(auth, reqData);
        } else {
            throw new NotAllowedException("Unable to validate request");
        }

        log.info("Unbinding of {} {} to {} is accepted", tpid.getMedium(), tpid.getAddress(), mxid.getId());
        if (cfg.getSession().getPolicy().getUnbind().shouldNotify()) {
            notifMgr.sendForUnbind(tpid);
        }
    }

    private String getDomain(String publicUrl) {
        URL url;
        try {
            url = new URL(publicUrl);
        } catch (MalformedURLException e) {
            log.error("Malformed public url, use as is");
            return publicUrl;
        }
        int port = url.getPort();
        return url.getHost() + (port != -1 ? ":" + url.getPort() : "");
    }

    private void checkAuthorization(String auth, JsonObject reqData) {
        if (!auth.startsWith("X-Matrix ")) {
            throw new NotAllowedException("Wrong authorization header");
        }

        String domain = getDomain(cfg.getServer().getPublicUrl());

        if (StringUtils.isBlank(domain)) {
            throw new NotAllowedException("Unable to verify request, missing `server.publicUrl` property");
        }

        String[] params = auth.substring("X-Matrix ".length()).split(",");

        String origin = null;
        String key = null;
        String sig = null;
        for (String param : params) {
            String[] paramItems = param.split("=");
            String paramKey = paramItems[0];
            String paramValue = paramItems[1];
            switch (paramKey) {
                case "origin":
                    origin = removeQuotes(paramValue);
                    break;
                case "key":
                    key = removeQuotes(paramValue);
                    break;
                case "sig":
                    sig = removeQuotes(paramValue);
                    break;
                default:
                    log.error("Unknown parameter: {}", param);
                    throw new BadRequestException("Authorization with unknown parameter");
            }
        }

        if (StringUtils.isBlank(origin) || StringUtils.isBlank(key) || StringUtils.isBlank(sig)) {
            log.error("Missing required parameters");
            throw new BadRequestException("Missing required header parameters");
        }

        if (!cfg.getMatrix().getDomain().equalsIgnoreCase(origin)) {
            throw new NotAllowedException("Only Matrix IDs from domain " + origin + " can be unbound");
        }

        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("method", "POST");
        jsonObject.addProperty("uri", "/_matrix/identity/api/v1/3pid/unbind");
        jsonObject.addProperty("origin", origin);
        jsonObject.addProperty("destination_is", domain);
        jsonObject.add("content", reqData);

        String canonical = MatrixJson.encodeCanonical(jsonObject);

        HomeserverFederationResolver.HomeserverTarget homeserverTarget = resolver.resolve(origin);

        validateServerKey(key, sig, canonical, homeserverTarget);
    }

    private String removeQuotes(String origin) {
        return origin.startsWith("\"") && origin.endsWith("\"") ? origin.substring(1, origin.length() - 1) : origin;
    }

    private void validateServerKey(String key, String signature, String canonical,
                                   HomeserverFederationResolver.HomeserverTarget homeserverTarget) {
        String originUrl = homeserverTarget.getUrl().toString();
        HttpGet request = new HttpGet(originUrl + "/_matrix/key/v2/server");
        log.info("Get keys from the server {}", request.getURI());
        try (CloseableHttpClient httpClient = HttpClients.custom()
            .setSSLHostnameVerifier(new HomeserverVerifier(homeserverTarget.getDomain())).build()) {
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                int statusCode = response.getStatusLine().getStatusCode();
                log.info("Answer code: {}", statusCode);
                if (statusCode == 200) {
                    verifyKey(key, signature, canonical, response);
                } else {
                    throw new RemoteHomeServerException("Unable to fetch server keys.");
                }
            } catch (IOException e) {
                String message = "Unable to get server keys: " + originUrl;
                log.error(message, e);
                throw new IllegalArgumentException(message);
            }
        } catch (IOException e) {
            String message = "Unable to get server keys: " + originUrl;
            log.error(message, e);
            throw new IllegalArgumentException(message);
        }
    }

    private void verifyKey(String key, String signature, String canonical, CloseableHttpResponse response) throws IOException {
        final String content = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
        log.info("Answer body: {}", content);
        final JsonObject responseObject = GsonUtil.parseObj(content);
        final long validUntilTs = GsonUtil.getLong(responseObject, "valid_until_ts");

        final Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(validUntilTs);
        if (calendar.before(Calendar.getInstance())) {
            final String msg = "Key is expired";
            log.error(msg);
            throw new RemoteHomeServerException(msg);
        }

        final JsonObject verifyKeys = GsonUtil.getObj(responseObject, "verify_keys");
        final JsonObject keyObject = GsonUtil.getObj(verifyKeys, key);
        final String publicKey = GsonUtil.getStringOrNull(keyObject, "key");

        if (StringUtils.isBlank(publicKey)) {
            throw new RemoteHomeServerException("Missing server key.");
        }

        EdDSANamedCurveSpec ed25519CurveSpec = EdDSANamedCurveTable.ED_25519_CURVE_SPEC;
        EdDSAPublicKeySpec publicKeySpec = new EdDSAPublicKeySpec(Base64.getDecoder().decode(publicKey), ed25519CurveSpec);
        EdDSAPublicKey dsaPublicKey = new EdDSAPublicKey(publicKeySpec);

        final boolean verificationResult = signatureManager.verify(dsaPublicKey, signature, canonical.getBytes(StandardCharsets.UTF_8));
        log.info("Verification result: {}", verificationResult);
        if (!verificationResult) {
            throw new RemoteHomeServerException("Unable to verify request.");
        }

        log.info("Request was authorized.");
    }

    private void checkSession(String sid, String secret, ThreePid tpid) {
        // We ensure the session was validated
        ThreePidSession session = getSessionIfValidated(sid, secret);

        // As per spec, we can only allow if the provided 3PID matches the validated session
        if (!session.getThreePid().equals(tpid)) {
            throw new BadRequestException("3PID to unbind does not match the one from the validated session");
        }
    }
}
