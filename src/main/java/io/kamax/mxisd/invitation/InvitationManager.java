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

package io.kamax.mxisd.invitation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.kamax.matrix.MatrixID;
import io.kamax.matrix.ThreePid;
import io.kamax.matrix._MatrixID;
import io.kamax.matrix.json.GsonUtil;
import io.kamax.mxisd.config.InvitationConfig;
import io.kamax.mxisd.config.MxisdConfig;
import io.kamax.mxisd.config.ServerConfig;
import io.kamax.mxisd.crypto.GenericKeyIdentifier;
import io.kamax.mxisd.crypto.KeyIdentifier;
import io.kamax.mxisd.crypto.KeyManager;
import io.kamax.mxisd.crypto.KeyType;
import io.kamax.mxisd.crypto.SignatureManager;
import io.kamax.mxisd.exception.BadRequestException;
import io.kamax.mxisd.exception.ConfigurationException;
import io.kamax.mxisd.exception.MappingAlreadyExistsException;
import io.kamax.mxisd.exception.ObjectNotFoundException;
import io.kamax.mxisd.lookup.SingleLookupReply;
import io.kamax.mxisd.lookup.ThreePidMapping;
import io.kamax.mxisd.lookup.strategy.LookupStrategy;
import io.kamax.mxisd.matrix.HomeserverFederationResolver;
import io.kamax.mxisd.matrix.HomeserverVerifier;
import io.kamax.mxisd.notification.NotificationManager;
import io.kamax.mxisd.profile.ProfileManager;
import io.kamax.mxisd.storage.IStorage;
import io.kamax.mxisd.storage.ormlite.dao.ThreePidInviteIO;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

public class InvitationManager {

    private static final Logger log = LoggerFactory.getLogger(InvitationManager.class);
    private static final String CreatedAtPropertyKey = "created_at";

    private final String defaultCreateTs = Long.toString(Instant.now().toEpochMilli());

    private InvitationConfig cfg;
    private ServerConfig srvCfg;
    private IStorage storage;
    private LookupStrategy lookupMgr;
    private KeyManager keyMgr;
    private SignatureManager signMgr;
    private HomeserverFederationResolver resolver;
    private NotificationManager notifMgr;
    private ProfileManager profileMgr;

    private Timer refreshTimer;

    private Map<String, IThreePidInviteReply> invitations = new ConcurrentHashMap<>();

    public InvitationManager(
            MxisdConfig mxisdCfg,
            IStorage storage,
            LookupStrategy lookupMgr,
            KeyManager keyMgr,
            SignatureManager signMgr,
            HomeserverFederationResolver resolver,
            NotificationManager notifMgr,
            ProfileManager profileMgr
    ) {
        this.cfg = requireValid(mxisdCfg);
        this.srvCfg = mxisdCfg.getServer();
        this.storage = storage;
        this.lookupMgr = lookupMgr;
        this.keyMgr = keyMgr;
        this.signMgr = signMgr;
        this.resolver = resolver;
        this.notifMgr = notifMgr;
        this.profileMgr = profileMgr;

        log.debug("Loading saved invites");
        Collection<ThreePidInviteIO> ioList = storage.getInvites();
        ioList.forEach(io -> {
            io.getProperties().putIfAbsent(CreatedAtPropertyKey, defaultCreateTs);
            log.debug("Processing invite {}", GsonUtil.get().toJson(io));
            ThreePidInvite invite = new ThreePidInvite(
                    MatrixID.asAcceptable(io.getSender()),
                    io.getMedium(),
                    io.getAddress(),
                    io.getRoomId(),
                    io.getProperties()
            );

            ThreePidInviteReply reply = new ThreePidInviteReply(io.getId(), invite, io.getToken(), "", Collections.emptyList());
            invitations.put(reply.getId(), reply);
        });
        log.info("Loaded saved invites");

        log.info("Setting up invitation mapping refresh timer");
        refreshTimer = new Timer();

        // We add a shutdown hook to cancel the hook and wait for pending resolutions
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            refreshTimer.cancel();
            ForkJoinPool.commonPool().awaitQuiescence(1, TimeUnit.MINUTES);
        }));

        // We set the refresh timer for background tasks
        refreshTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    doMaintenance();
                } catch (Throwable t) {
                    log.error("Error when running background maintenance", t);
                }
            }
        }, 5000L, TimeUnit.MILLISECONDS.convert(cfg.getResolution().getTimer(), TimeUnit.MINUTES));
    }

    private InvitationConfig requireValid(MxisdConfig cfg) {
        // This is not configured, we'll apply a default configuration
        if (Objects.isNull(cfg.getInvite().getExpiration().isEnabled())) {
            // We compute our own user, so it can be used if we bridge as well
            String mxId = MatrixID.asAcceptable("_mxisd-expired_invite", cfg.getMatrix().getDomain()).getId();

            // Enabled by default
            cfg.getInvite().getExpiration().setEnabled(true);
        }

        if (cfg.getInvite().getExpiration().isEnabled()) {
            if (cfg.getInvite().getExpiration().getAfter() < 1) {
                throw new ConfigurationException("Invitation expiration delay must be greater or equal to 1");
            }

            if (StringUtils.isBlank(cfg.getInvite().getExpiration().getResolveTo())) {
                String localpart = cfg.getAppsvc().getUser().getInviteExpired();
                if (StringUtils.isBlank(localpart)) {
                    throw new ConfigurationException("Could not compute the Invitation expiration resolution target from App service user: not set");
                }

                cfg.getInvite().getExpiration().setResolveTo(MatrixID.asAcceptable(localpart, cfg.getMatrix().getDomain()).getId());
            }

            try {
                MatrixID.asAcceptable(cfg.getInvite().getExpiration().getResolveTo());
            } catch (IllegalArgumentException e) {
                throw new ConfigurationException("Invitation expiration resolution target is not a valid Matrix ID: " + e.getMessage());
            }
        }

        return cfg.getInvite();
    }

    private String computeId(IThreePidInvite invite) {
        String rawId = invite.getSender().getDomain().toLowerCase() + invite.getMedium().toLowerCase() + invite.getAddress().toLowerCase();
        return Base64.encodeBase64URLSafeString(rawId.getBytes(StandardCharsets.UTF_8));
    }

    private String getIdForLog(IThreePidInviteReply reply) {
        return reply.getInvite().getSender().getId() + ":" + reply.getInvite().getRoomId() + ":" + reply.getInvite().getMedium() + ":" + reply.getInvite().getAddress();
    }

    private Optional<SingleLookupReply> lookup3pid(String medium, String address) {
        if (!cfg.getResolution().isRecursive()) {
            log.warn("/!\\ /!\\ --- RECURSIVE INVITE RESOLUTION HAS BEEN DISABLED --- /!\\ /!\\");
        }

        return lookupMgr.find(medium, address, cfg.getResolution().isRecursive());
    }

    public List<IThreePidInviteReply> listInvites() {
        return new ArrayList<>(invitations.values());
    }

    public IThreePidInviteReply getInvite(String id) {
        IThreePidInviteReply v = invitations.get(id);
        if (Objects.isNull(v)) {
            throw new ObjectNotFoundException("Invite", id);
        }

        return v;
    }

    public boolean canInvite(_MatrixID sender, JsonObject request) {
        if (!request.has("medium")) {
            log.info("Not a 3PID invite, allowing");
            return true;
        }
        log.info("3PID invite detected, checking policies...");

        List<String> allowedRoles = cfg.getPolicy().getIfSender().getHasRole();
        if (Objects.isNull(allowedRoles)) {
            log.info("No allowed role configured for sender, allowing");
            return true;
        }

        List<String> userRoles = profileMgr.getRoles(sender);
        if (Collections.disjoint(userRoles, allowedRoles)) {
            log.info("Sender does not have any of the required roles, denying");
            return false;
        }
        log.info("Sender has at least one of the required roles");

        log.info("Sender pass all policies to invite, allowing");
        return true;
    }

    public synchronized IThreePidInviteReply storeInvite(IThreePidInvite invitation) { // TODO better sync
        if (!notifMgr.isMediumSupported(invitation.getMedium())) {
            throw new BadRequestException("Medium type " + invitation.getMedium() + " is not supported");
        }

        String invId = computeId(invitation);
        log.info("Handling invite for {}:{} from {} in room {}", invitation.getMedium(), invitation.getAddress(), invitation.getSender(), invitation.getRoomId());
        IThreePidInviteReply reply = invitations.get(invId);
        if (reply != null) {
            log.info("Invite is already pending for {}:{}, returning data", invitation.getMedium(), invitation.getAddress());
            if (!StringUtils.equals(invitation.getRoomId(), reply.getInvite().getRoomId())) {
                log.info("Sending new notification as new invite room {} is different from the original {}", invitation.getRoomId(), reply.getInvite().getRoomId());
                notifMgr.sendForReply(new ThreePidInviteReply(reply.getId(), invitation, reply.getToken(), reply.getDisplayName(), reply.getPublicKeys()));
            } else {
                // FIXME we should check attempt and send if bigger
            }
            return reply;
        }

        Optional<SingleLookupReply> result = lookup3pid(invitation.getMedium(), invitation.getAddress());
        if (result.isPresent()) {
            log.info("Mapping for {}:{} already exists, refusing to store invite", invitation.getMedium(), invitation.getAddress());
            throw new MappingAlreadyExistsException();
        }

        String token = RandomStringUtils.randomAlphanumeric(64);
        String displayName = invitation.getAddress().substring(0, 3) + "...";
        KeyIdentifier pKeyId = keyMgr.getServerSigningKey().getId();
        KeyIdentifier eKeyId = keyMgr.generateKey(KeyType.Ephemeral);

        String pPubKey = keyMgr.getPublicKeyBase64(pKeyId);
        String ePubKey = keyMgr.getPublicKeyBase64(eKeyId);

        invitation.getProperties().put(CreatedAtPropertyKey, Long.toString(Instant.now().toEpochMilli()));
        invitation.getProperties().put("p_key_algo", pKeyId.getAlgorithm());
        invitation.getProperties().put("p_key_serial", pKeyId.getSerial());
        invitation.getProperties().put("p_key_public", pPubKey);
        invitation.getProperties().put("e_key_algo", eKeyId.getAlgorithm());
        invitation.getProperties().put("e_key_serial", eKeyId.getSerial());
        invitation.getProperties().put("e_key_public", ePubKey);

        reply = new ThreePidInviteReply(invId, invitation, token, displayName, Arrays.asList(pPubKey, ePubKey));

        log.info("Performing invite to {}:{}", invitation.getMedium(), invitation.getAddress());
        notifMgr.sendForReply(reply);

        log.info("Storing invite under ID {}", invId);
        storage.insertInvite(reply);
        invitations.put(invId, reply);
        log.info("A new invite has been created for {}:{} on HS {}", invitation.getMedium(), invitation.getAddress(), invitation.getSender().getDomain());

        return reply;
    }

    public boolean hasInvite(ThreePid tpid) {
        for (IThreePidInviteReply reply : invitations.values()) {
            if (!StringUtils.equals(tpid.getMedium(), reply.getInvite().getMedium())) {
                continue;
            }

            if (!StringUtils.equals(tpid.getAddress(), reply.getInvite().getAddress())) {
                continue;
            }

            return true;
        }

        return false;
    }

    private void removeInvite(IThreePidInviteReply reply) {
        invitations.remove(reply.getId());
        storage.deleteInvite(reply.getId());
    }

    /**
     * Trigger the periodic maintenance tasks
     */
    public void doMaintenance() {
        lookupMappingsForInvites();
        expireInvites();
    }

    public void expireInvites() {
        log.debug("Invite expiration: started");

        if (!cfg.getExpiration().isEnabled()) {
            log.debug("Invite expiration is disabled, skipping");
            return;
        }

        if (invitations.isEmpty()) {
            log.debug("No invite to expired, skipping");
            return;
        }

        String targetMxid = cfg.getExpiration().getResolveTo();
        for (IThreePidInviteReply reply : invitations.values()) {
            log.debug("Processing invite {}", reply.getId());

            String tsRaw = reply.getInvite().getProperties().computeIfAbsent(CreatedAtPropertyKey, k -> defaultCreateTs);
            try {
                Instant ts = Instant.ofEpochMilli(Long.parseLong(tsRaw));
                Instant targetTs = ts.plusSeconds(cfg.getExpiration().getAfter() * 60);
                Instant now = Instant.now();
                log.debug("Invite {} - Created at {} - Expires at {} - Current time is {}", reply.getId(), ts, targetTs, now);
                if (targetTs.isAfter(now)) {
                    log.debug("Invite {} has not expired yet, skipping", reply.getId());
                    continue;
                }

                log.info("Invite {} has expired at TS {} - Expiring and resolving to {}", reply.getId(), targetTs, targetMxid);
                publishMapping(reply, targetMxid);
            } catch (NumberFormatException | DateTimeException e) {
                log.warn("Invite {} has an invalid creation TS, setting to default value of {}", reply.getId(), defaultCreateTs);
                reply.getInvite().getProperties().put(CreatedAtPropertyKey, defaultCreateTs);
            }
        }

        log.debug("Invite expiration: finished");
    }

    public void expireInvite(String id) {
        publishMapping(getInvite(id), cfg.getExpiration().getResolveTo());
    }

    public void lookupMappingsForInvites() {
        if (!invitations.isEmpty()) {
            log.info("Checking for existing mapping for pending invites");
            for (IThreePidInviteReply reply : invitations.values()) {
                log.info("Processing invite {}", getIdForLog(reply));
                ForkJoinPool.commonPool().submit(new MappingChecker(reply));
            }
        }
    }

    public void publishMappingIfInvited(ThreePidMapping threePid) {
        log.info("Looking up possible pending invites for {}:{}", threePid.getMedium(), threePid.getValue());
        for (IThreePidInviteReply reply : invitations.values()) {
            if (StringUtils.equalsIgnoreCase(reply.getInvite().getMedium(), threePid.getMedium()) && StringUtils.equalsIgnoreCase(reply.getInvite().getAddress(), threePid.getValue())) {
                log.info("{}:{} has an invite pending on HS {}, publishing mapping", threePid.getMedium(), threePid.getValue(), reply.getInvite().getSender().getDomain());
                publishMapping(reply, threePid.getMxid());
            }
        }
    }

    public IThreePidInviteReply getInvite(String token, String privKey) {
        for (IThreePidInviteReply reply : invitations.values()) {
            if (StringUtils.equals(reply.getToken(), token)) {
                String algo = reply.getInvite().getProperties().get("e_key_algo");
                String serial = reply.getInvite().getProperties().get("e_key_serial");

                if (StringUtils.isAnyBlank(algo, serial)) {
                    continue;
                }

                String storedPrivKey = keyMgr.getKey(new GenericKeyIdentifier(KeyType.Ephemeral, algo, serial)).getPrivateKeyBase64();
                if (!StringUtils.equals(storedPrivKey, privKey)) {
                    continue;
                }

                return reply;
            }
        }

        throw new ObjectNotFoundException("No invite with such token and/or private key");
    }

    private void publishMapping(IThreePidInviteReply reply, String mxid) {
        String medium = reply.getInvite().getMedium();
        String address = reply.getInvite().getAddress();
        String domain = reply.getInvite().getSender().getDomain();
        log.info("Discovering HS for domain {}", domain);
        HomeserverFederationResolver.HomeserverTarget hsUrlOpt = resolver.resolve(domain);

        // TODO this is needed as this will block if called during authentication cycle due to synapse implementation
        new Thread(() -> { // FIXME need to make this retry-able and within a general background working pool
            HttpPost req = new HttpPost(hsUrlOpt.getUrl().toString() + "/_matrix/federation/v1/3pid/onbind");
            // Expected body: https://matrix.to/#/!HUeDbmFUsWAhxHHvFG:matrix.org/$150469846739DCLWc:matrix.trancendances.fr
            JsonObject obj = new JsonObject();
            obj.addProperty("mxid", mxid);
            obj.addProperty("token", reply.getToken());
            obj.add("signatures", signMgr.signMessageGson(srvCfg.getName(), obj.toString()));

            JsonObject objUp = new JsonObject();
            objUp.addProperty("mxid", mxid);
            objUp.addProperty("medium", medium);
            objUp.addProperty("address", address);
            objUp.addProperty("sender", reply.getInvite().getSender().getId());
            objUp.addProperty("room_id", reply.getInvite().getRoomId());
            objUp.add("signed", obj);

            JsonObject content = new JsonObject();
            JsonArray invites = new JsonArray();
            invites.add(objUp);
            content.add("invites", invites);
            content.addProperty("medium", medium);
            content.addProperty("address", address);
            content.addProperty("mxid", mxid);

            content.add("signatures", signMgr.signMessageGson(srvCfg.getName(), content.toString()));

            StringEntity entity = new StringEntity(content.toString(), StandardCharsets.UTF_8);
            entity.setContentType("application/json");
            req.setEntity(entity);

            Instant resolvedAt = Instant.now();
            boolean couldPublish = false;
            boolean shouldArchive = true;
            try (CloseableHttpClient httpClient = HttpClients.custom().setSSLHostnameVerifier(new HomeserverVerifier(hsUrlOpt.getDomain()))
                .build()) {
                try {
                    log.info("Posting onBind event to {}", req.getURI());
                    CloseableHttpResponse response = httpClient.execute(req);
                    int statusCode = response.getStatusLine().getStatusCode();
                    log.info("Answer code: {}", statusCode);
                    if (statusCode >= 300 && statusCode != 403) {
                        log.info("Answer body: {}", IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8));
                        log.warn("HS returned an error.");

                        shouldArchive = statusCode != 502;
                        if (shouldArchive) {
                            log.info("Invite can be found in historical storage for manual re-processing");
                        }
                    } else {
                        couldPublish = true;
                        if (statusCode == 403) {
                            log.info("Invite is obsolete or no longer under our control");
                        }
                    }
                    response.close();
                } catch (IOException e) {
                    log.warn("Unable to tell HS {} about invite being mapped", domain, e);
                } finally {
                    if (shouldArchive) {
                        synchronized (this) {
                            storage.insertHistoricalInvite(reply, mxid, resolvedAt, couldPublish);
                            removeInvite(reply);
                            log.info("Moved invite {} to historical table", reply.getId());
                        }
                    }
                }
            } catch (IOException e) {
                log.error("Unable to create client to the " + hsUrlOpt.getUrl().toString(), e);
            }
        }).start();
    }

    private class MappingChecker implements Runnable {

        private IThreePidInviteReply reply;

        MappingChecker(IThreePidInviteReply reply) {
            this.reply = reply;
        }

        @Override
        public void run() {
            try {
                log.info("Searching for mapping created after invite {} was created", getIdForLog(reply));
                Optional<SingleLookupReply> result = lookup3pid(reply.getInvite().getMedium(), reply.getInvite().getAddress());
                if (result.isPresent()) {
                    SingleLookupReply lookup = result.get();
                    log.info("Found mapping for pending invite {}", getIdForLog(reply));
                    publishMapping(reply, lookup.getMxid().getId());
                } else {
                    log.info("No mapping for pending invite {}", getIdForLog(reply));
                    if (lookupMgr.getLocalProviders().isEmpty()) {
                        log.warn("No Identity store has been configured, this invite may never resolve");
                    }
                }
            } catch (Throwable t) {
                log.error("Unable to process invite", t);
            }
        }
    }

}
