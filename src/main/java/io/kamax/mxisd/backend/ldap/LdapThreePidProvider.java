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

package io.kamax.mxisd.backend.ldap;

import io.kamax.mxisd.config.MatrixConfig;
import io.kamax.mxisd.config.ldap.LdapConfig;
import io.kamax.mxisd.exception.InternalServerError;
import io.kamax.mxisd.lookup.SingleLookupReply;
import io.kamax.mxisd.lookup.SingleLookupRequest;
import io.kamax.mxisd.lookup.ThreePidMapping;
import io.kamax.mxisd.lookup.provider.IThreePidProvider;
import io.kamax.mxisd.util.GsonUtil;
import org.apache.directory.api.ldap.model.cursor.CursorException;
import org.apache.directory.api.ldap.model.cursor.CursorLdapReferralException;
import org.apache.directory.api.ldap.model.cursor.EntryCursor;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.message.SearchScope;
import org.apache.directory.ldap.client.api.LdapConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class LdapThreePidProvider extends LdapBackend implements IThreePidProvider {

    private transient final Logger log = LoggerFactory.getLogger(LdapThreePidProvider.class);

    public LdapThreePidProvider(LdapConfig cfg, MatrixConfig mxCfg) {
        super(cfg, mxCfg);
    }

    @Override
    public boolean isLocal() {
        return true;
    }

    @Override
    public int getPriority() {
        return 20;
    }

    private Optional<String> lookup(LdapConnection conn, String medium, String value) {
        Optional<String> tPidQueryOpt = getCfg().getIdentity().getQuery(medium);
        if (!tPidQueryOpt.isPresent()) {
            log.warn("{} is not a configured 3PID type for LDAP lookup", medium);
            return Optional.empty();
        }

        // we merge 3PID specific query with global/specific filter, if one exists.
        String tPidQuery = tPidQueryOpt.get().replaceAll(getCfg().getIdentity().getToken(), value);
        String searchQuery = buildWithFilter(tPidQuery, getCfg().getIdentity().getFilter());
        log.debug("Query: {}", searchQuery);
        log.debug("Attributes: {}", GsonUtil.build().toJson(getUidAtt()));

        for (String baseDN : getBaseDNs()) {
            log.debug("Base DN: {}", baseDN);

            try (EntryCursor cursor = conn.search(baseDN, searchQuery, SearchScope.SUBTREE, getUidAtt())) {
                while (cursor.next()) {
                    Entry entry = cursor.get();
                    log.info("Found possible match, DN: {}", entry.getDn().getName());

                    Optional<String> data = getAttribute(entry, getUidAtt());
                    if (!data.isPresent()) {
                        continue;
                    }

                    log.info("DN {} is a valid match", entry.getDn().getName());
                    return Optional.of(buildMatrixIdFromUid(data.get()));
                }
            } catch (CursorLdapReferralException e) {
                log.warn("3PID {} is only available via referral, skipping", value);
            } catch (IOException | LdapException | CursorException e) {
                throw new InternalServerError(e);
            }
        }

        return Optional.empty();
    }

    @Override
    public Optional<SingleLookupReply> find(SingleLookupRequest request) {
        log.info("Performing LDAP lookup {} of type {}", request.getThreePid(), request.getType());

        try (LdapConnection conn = getConn()) {
            bind(conn);
            return lookup(conn, request.getType(), request.getThreePid()).map(id -> new SingleLookupReply(request, id));
        } catch (LdapException | IOException e) {
            throw new InternalServerError(e);
        }
    }

    @Override
    public List<ThreePidMapping> populate(List<ThreePidMapping> mappings) {
        log.info("Looking up {} mappings", mappings.size());
        List<ThreePidMapping> mappingsFound = new ArrayList<>();

        try (LdapConnection conn = getConn()) {
            bind(conn);

            for (ThreePidMapping mapping : mappings) {
                try {
                    lookup(conn, mapping.getMedium(), mapping.getValue()).ifPresent(id -> {
                        mapping.setMxid(id);
                        mappingsFound.add(mapping);
                    });
                } catch (IllegalArgumentException e) {
                    log.warn("{} is not a supported 3PID type for LDAP lookup", mapping.getMedium());
                }
            }
        } catch (LdapException | IOException e) {
            throw new InternalServerError(e);
        }

        return mappingsFound;
    }

    private List<String> getAttributes() {
        final List<String> attributes = getCfg().getAttribute().getThreepid().values().stream().flatMap(List::stream)
            .collect(Collectors.toList());
        attributes.add(getUidAtt());
        return attributes;
    }

    private Optional<String> getAttributeValue(Entry entry, List<String> attributes) {
        return attributes.stream()
            .map(attr -> getAttribute(entry, attr))
            .filter(Objects::nonNull)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .findFirst();
    }

    @Override
    public Iterable<ThreePidMapping> populateHashes() {
        List<ThreePidMapping> result = new ArrayList<>();
        if (!getCfg().getIdentity().isLookup()) {
            return result;
        }

        String filter = getCfg().getIdentity().getFilter();

        try (LdapConnection conn = getConn()) {
            bind(conn);

            log.debug("Query: {}", filter);
            List<String> attributes = getAttributes();
            log.debug("Attributes: {}", GsonUtil.build().toJson(attributes));

            for (String baseDN : getBaseDNs()) {
                log.debug("Base DN: {}", baseDN);

                try (EntryCursor cursor = conn.search(baseDN, filter, SearchScope.SUBTREE, attributes.toArray(new String[0]))) {
                    while (cursor.next()) {
                        Entry entry = cursor.get();
                        log.info("Found possible match, DN: {}", entry.getDn().getName());
                        Optional<String> mxid = getAttribute(entry, getUidAtt());
                        if (!mxid.isPresent()) {
                            continue;
                        }

                        for (Map.Entry<String, List<String>> attributeEntry : getCfg().getAttribute().getThreepid().entrySet()) {
                            String medium = attributeEntry.getKey();
                            getAttributeValue(entry, attributeEntry.getValue())
                                .ifPresent(s -> result.add(new ThreePidMapping(medium, s, buildMatrixIdFromUid(mxid.get()))));
                        }
                    }
                } catch (CursorLdapReferralException e) {
                    log.warn("3PID is only available via referral, skipping", e);
                } catch (IOException | LdapException | CursorException e) {
                    log.error("Unable to fetch 3PID mappings", e);
                }
            }
        } catch (LdapException | IOException e) {
            log.error("Unable to fetch 3PID mappings", e);
        }
        return result;
    }
}
