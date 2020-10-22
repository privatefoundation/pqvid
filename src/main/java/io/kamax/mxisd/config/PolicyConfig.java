package io.kamax.mxisd.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class PolicyConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolicyConfig.class);

    private Map<String, PolicyObject> policies = new HashMap<>();

    public static class TermObject {

        private String name;

        private String url;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }

    public static class PolicyObject {

        private String version;

        private Map<String, TermObject> terms;

        private List<String> regexp = new ArrayList<>();

        private transient List<Pattern> patterns = new ArrayList<>();

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public Map<String, TermObject> getTerms() {
            return terms;
        }

        public void setTerms(Map<String, TermObject> terms) {
            this.terms = terms;
        }

        public List<String> getRegexp() {
            return regexp;
        }

        public void setRegexp(List<String> regexp) {
            this.regexp = regexp;
        }

        public List<Pattern> getPatterns() {
            return patterns;
        }
    }

    public Map<String, PolicyObject> getPolicies() {
        return policies;
    }

    public void setPolicies(Map<String, PolicyObject> policies) {
        this.policies = policies;
    }

    public void build() {
        LOGGER.info("--- Policy Config ---");
        if (getPolicies().isEmpty()) {
            LOGGER.info("Empty");
        } else {
            for (Map.Entry<String, PolicyObject> policyObjectItem : getPolicies().entrySet()) {
                PolicyObject policyObject = policyObjectItem.getValue();
                StringBuilder sb = new StringBuilder();
                sb.append("Policy \"").append(policyObjectItem.getKey()).append("\"\n");
                sb.append("  version: ").append(policyObject.getVersion()).append("\n");
                for (String regexp : policyObjectItem.getValue().getRegexp()) {
                    sb.append("    - ").append(regexp).append("\n");
                    policyObjectItem.getValue().getPatterns().add(Pattern.compile(regexp));
                }
                sb.append("  terms:\n");
                if (policyObject.getTerms() != null) {
                    for (Map.Entry<String, TermObject> termItem : policyObject.getTerms().entrySet()) {
                        sb.append("    - lang: ").append(termItem.getKey()).append("\n");
                        sb.append("      name: ").append(termItem.getValue().getName()).append("\n");
                        sb.append("       url: ").append(termItem.getValue().getUrl()).append("\n");
                    }
                }
                LOGGER.info(sb.toString());
            }
        }
    }
}
