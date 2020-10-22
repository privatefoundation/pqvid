package io.kamax.mxisd.matrix;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.cert.Certificate;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;

public class HomeserverVerifier implements HostnameVerifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(HomeserverVerifier.class);
    private static final String ALT_DNS_NAME_TYPE = "2";
    private static final String ALT_IP_ADDRESS_TYPE = "7";

    private final String matrixHostname;

    public HomeserverVerifier(String matrixHostname) {
        this.matrixHostname = matrixHostname;
    }

    @Override
    public boolean verify(String hostname, SSLSession session) {
        try {
            Certificate peerCertificate = session.getPeerCertificates()[0];
            if (peerCertificate instanceof X509Certificate) {
                X509Certificate x509Certificate = (X509Certificate) peerCertificate;
                if (x509Certificate.getSubjectAlternativeNames() == null) {
                    return false;
                }
                for (String altSubjectName : getAltSubjectNames(x509Certificate)) {
                    if (match(altSubjectName)) {
                        return true;
                    }
                }
            }
        } catch (SSLPeerUnverifiedException | CertificateParsingException e) {
            LOGGER.error("Unable to check remote host", e);
            return false;
        }

        return false;
    }

    private List<String> getAltSubjectNames(X509Certificate x509Certificate) {
        List<String> subjectNames = new ArrayList<>();
        try {
            for (List<?> subjectAlternativeNames : x509Certificate.getSubjectAlternativeNames()) {
                if (subjectAlternativeNames == null
                    || subjectAlternativeNames.size() < 2
                    || subjectAlternativeNames.get(0) == null
                    || subjectAlternativeNames.get(1) == null) {
                    continue;
                }
                String subjectType = subjectAlternativeNames.get(0).toString();
                switch (subjectType) {
                    case ALT_DNS_NAME_TYPE:
                    case ALT_IP_ADDRESS_TYPE:
                        subjectNames.add(subjectAlternativeNames.get(1).toString());
                        break;
                    default:
                        LOGGER.trace("Unusable subject type: " + subjectType);
                }
            }
        } catch (CertificateParsingException e) {
            LOGGER.error("Unable to parse the certificate", e);
            return Collections.emptyList();
        }
        return subjectNames;
    }

    private boolean match(String altSubjectName) {
        if (altSubjectName.startsWith("*.")) {
            String subjectNameWithoutMask = altSubjectName.substring(1); // remove wildcard
            return matrixHostname.toLowerCase().endsWith(subjectNameWithoutMask.toLowerCase());
        } else {
            return matrixHostname.equalsIgnoreCase(altSubjectName);
        }
    }
}
