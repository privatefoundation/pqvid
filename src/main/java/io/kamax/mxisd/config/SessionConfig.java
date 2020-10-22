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

package io.kamax.mxisd.config;

import io.kamax.matrix.json.GsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SessionConfig {

    private transient final Logger log = LoggerFactory.getLogger(SessionConfig.class);

    public static class Policy {

        public static class PolicyTemplate {

            private boolean enabled;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

        }

        public static class PolicyUnbind {

            private boolean enabled = true;
            
            private boolean notifications = true;

            public boolean getEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }
            
            public boolean shouldNotify() {
                return notifications;
            }
            
            public void setNotifications(boolean notifications) {
                this.notifications = notifications;
            }
        }

        public Policy() {
            validation.enabled = true;
            unbind.enabled = true;
            unbind.notifications = true;
        }

        private PolicyTemplate validation = new PolicyTemplate();
        private PolicyUnbind unbind = new PolicyUnbind();

        public PolicyTemplate getValidation() {
            return validation;
        }

        public void setValidation(PolicyTemplate validation) {
            this.validation = validation;
        }

        public PolicyUnbind getUnbind() {
            return unbind;
        }

        public void setUnbind(PolicyUnbind unbind) {
            this.unbind = unbind;
        }

    }

    private Policy policy = new Policy();

    public Policy getPolicy() {
        return policy;
    }

    public void setPolicy(Policy policy) {
        this.policy = policy;
    }

    public void build() {
        log.info("--- Session config ---");
        log.info("Global Policy: {}", GsonUtil.get().toJson(policy));
    }

}
