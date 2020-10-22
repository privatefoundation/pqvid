/*
 * mxisd - Matrix Identity Server Daemon
 * Copyright (C) 2018 Kamax Sàrl
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

package io.kamax.mxisd.config;

import io.kamax.matrix.json.GsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class InvitationConfig {

    private static final Logger log = LoggerFactory.getLogger(InvitationConfig.class);

    public static class Expiration {

        private Boolean enabled;
        private long after = 60 * 24 * 7; // One calendar week (60min/1h * 24 = 1d * 7 = 1w)
        private String resolveTo;

        public Boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getAfter() {
            return after;
        }

        public void setAfter(long after) {
            this.after = after;
        }

        public String getResolveTo() {
            return resolveTo;
        }

        public void setResolveTo(String resolveTo) {
            this.resolveTo = resolveTo;
        }

    }

    public static class Resolution {

        private boolean recursive = true;
        private long timer = 5;

        public boolean isRecursive() {
            return recursive;
        }

        public void setRecursive(boolean recursive) {
            this.recursive = recursive;
        }

        public long getTimer() {
            return timer;
        }

        public void setTimer(long timer) {
            this.timer = timer;
        }

    }

    public static class SenderPolicy {

        private List<String> hasRole = new ArrayList<>();

        public List<String> getHasRole() {
            return hasRole;
        }

        public void setHasRole(List<String> hasRole) {
            this.hasRole = hasRole;
        }
    }

    public static class Policies {

        private SenderPolicy ifSender = new SenderPolicy();

        public SenderPolicy getIfSender() {
            return ifSender;
        }

        public void setIfSender(SenderPolicy ifSender) {
            this.ifSender = ifSender;
        }
    }

    private Expiration expiration = new Expiration();
    private Resolution resolution = new Resolution();
    private Policies policy = new Policies();

    public Expiration getExpiration() {
        return expiration;
    }

    public void setExpiration(Expiration expiration) {
        this.expiration = expiration;
    }

    public Resolution getResolution() {
        return resolution;
    }

    public void setResolution(Resolution resolution) {
        this.resolution = resolution;
    }

    public Policies getPolicy() {
        return policy;
    }

    public void setPolicy(Policies policy) {
        this.policy = policy;
    }

    public void build() {
        log.info("--- Invite config ---");
        log.info("Expiration: {}", GsonUtil.get().toJson(getExpiration()));
        log.info("Resolution: {}", GsonUtil.get().toJson(getResolution()));
        log.info("Policies: {}", GsonUtil.get().toJson(getPolicy()));
    }

}
