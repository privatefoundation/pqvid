package io.kamax.mxisd.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AccountConfig {

    private final static Logger log = LoggerFactory.getLogger(DirectoryConfig.class);

    private boolean allowOnlyTrustDomains = true;

    public boolean isAllowOnlyTrustDomains() {
        return allowOnlyTrustDomains;
    }

    public void setAllowOnlyTrustDomains(boolean allowOnlyTrustDomains) {
        this.allowOnlyTrustDomains = allowOnlyTrustDomains;
    }

    public void build() {
        log.info("--- Account config ---");
        log.info("Allow registration only for trust domain: {}", isAllowOnlyTrustDomains());
    }
}
