/*
 * mxisd - Matrix Identity Server Daemon
 * Copyright (C) 2018 Kamax Sarl
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

package io.kamax.mxisd.backend.memory;

import io.kamax.matrix.MatrixID;
import io.kamax.matrix.ThreePid;
import io.kamax.matrix._MatrixID;
import io.kamax.matrix._ThreePid;
import io.kamax.mxisd.UserIdType;
import io.kamax.mxisd.auth.provider.AuthenticatorProvider;
import io.kamax.mxisd.auth.provider.BackendAuthResult;
import io.kamax.mxisd.config.MatrixConfig;
import io.kamax.mxisd.config.memory.MemoryIdentityConfig;
import io.kamax.mxisd.config.memory.MemoryStoreConfig;
import io.kamax.mxisd.config.memory.MemoryThreePid;
import io.kamax.mxisd.directory.DirectoryProvider;
import io.kamax.mxisd.http.io.UserDirectorySearchResult;
import io.kamax.mxisd.lookup.SingleLookupReply;
import io.kamax.mxisd.lookup.SingleLookupRequest;
import io.kamax.mxisd.lookup.ThreePidMapping;
import io.kamax.mxisd.lookup.provider.IThreePidProvider;
import io.kamax.mxisd.profile.ProfileProvider;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class MemoryIdentityStore implements AuthenticatorProvider, DirectoryProvider, IThreePidProvider, ProfileProvider {

    private transient final Logger logger = LoggerFactory.getLogger(MemoryIdentityStore.class);

    private final MatrixConfig mxCfg;
    private final MemoryStoreConfig cfg;

    public MemoryIdentityStore(MatrixConfig mxCfg, MemoryStoreConfig cfg) {
        this.mxCfg = mxCfg;
        this.cfg = cfg;
    }

    public Optional<MemoryIdentityConfig> findByUsername(String username) {
        return cfg.getIdentities().stream()
                .filter(id -> StringUtils.equals(id.getUsername(), username))
                .findFirst();
    }

    @Override
    public boolean isEnabled() {
        return cfg.isEnabled();
    }

    @Override
    public Optional<String> getDisplayName(_MatrixID mxid) {
        return findByUsername(mxid.getLocalPart()).map(MemoryIdentityConfig::getDisplayName);
    }

    private UserDirectorySearchResult search(
            Predicate<MemoryIdentityConfig> predicate,
            Function<MemoryIdentityConfig, UserDirectorySearchResult.Result> mapper
    ) {
        UserDirectorySearchResult search = new UserDirectorySearchResult();
        cfg.getIdentities().stream().filter(predicate).map(mapper).forEach(search::addResult);
        return search;
    }

    @Override
    public UserDirectorySearchResult searchByDisplayName(String query) {
        return search(
                entry -> StringUtils.containsIgnoreCase(entry.getUsername(), query),
                entry -> {
                    UserDirectorySearchResult.Result result = new UserDirectorySearchResult.Result();
                    result.setUserId(MatrixID.from(entry.getUsername(), mxCfg.getDomain()).acceptable().getId());
                    result.setDisplayName(entry.getUsername());
                    return result;
                }
        );
    }

    @Override
    public UserDirectorySearchResult searchBy3pid(String query) {
        return search(
                entry -> entry.getThreepids().stream()
                        .anyMatch(tpid -> StringUtils.containsIgnoreCase(tpid.getAddress(), query)),
                entry -> {
                    UserDirectorySearchResult.Result result = new UserDirectorySearchResult.Result();
                    result.setUserId(MatrixID.from(entry.getUsername(), mxCfg.getDomain()).acceptable().getId());
                    result.setDisplayName(entry.getUsername());
                    return result;
                }
        );
    }

    @Override
    public List<_ThreePid> getThreepids(_MatrixID mxid) {
        List<_ThreePid> l = new ArrayList<>();
        findByUsername(mxid.getLocalPart()).ifPresent(c -> l.addAll(c.getThreepids()));
        return l;
    }

    @Override
    public List<String> getRoles(_MatrixID mxid) {
        List<String> l = new ArrayList<>();
        findByUsername(mxid.getLocalPart()).ifPresent(c -> l.addAll(c.getRoles()));
        return l;
    }

    @Override
    public boolean isLocal() {
        return true;
    }

    @Override
    public int getPriority() {
        return Integer.MAX_VALUE;
    }

    @Override
    public Optional<SingleLookupReply> find(SingleLookupRequest request) {
        logger.info("Performing lookup {} of type {}", request.getThreePid(), request.getType());
        ThreePid req = new ThreePid(request.getType(), request.getThreePid());
        for (MemoryIdentityConfig id : cfg.getIdentities()) {
            for (MemoryThreePid threepid : id.getThreepids()) {
                if (req.equals(new ThreePid(threepid.getMedium(), threepid.getAddress()))) {
                    return Optional.of(new SingleLookupReply(request, MatrixID.asAcceptable(id.getUsername(), mxCfg.getDomain())));
                }
            }
        }

        return Optional.empty();
    }

    @Override
    public List<ThreePidMapping> populate(List<ThreePidMapping> mappings) {
        return Collections.emptyList();
    }

    @Override
    public BackendAuthResult authenticate(_MatrixID mxid, String password) {
        return findByUsername(mxid.getLocalPart()).map(id -> {
            if (!StringUtils.equals(id.getUsername(), mxid.getLocalPart())) {
                return BackendAuthResult.failure();
            } else {
                BackendAuthResult result = new BackendAuthResult();
                id.getThreepids().forEach(tpid -> result.withThreePid(new ThreePid(tpid.getMedium(), tpid.getAddress())));
                result.succeed(mxid.getId(), UserIdType.MatrixID.getId(), "");
                return result;
            }
        }).orElseGet(BackendAuthResult::failure);
    }

    @Override
    public Iterable<ThreePidMapping> populateHashes() {
        if (!cfg.isHashEnabled()) {
            return Collections.emptyList();
        }

        return cfg.getIdentities().stream()
            .map(mic -> mic.getThreepids().stream().map(mtp -> new ThreePidMapping(mtp.getMedium(), mtp.getAddress(), mic.getUsername())))
            .flatMap(s -> s).collect(
            Collectors.toList());
    }
}
