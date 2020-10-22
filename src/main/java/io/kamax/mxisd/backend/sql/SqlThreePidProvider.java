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

package io.kamax.mxisd.backend.sql;

import io.kamax.matrix.MatrixID;
import io.kamax.mxisd.config.MatrixConfig;
import io.kamax.mxisd.config.sql.SqlConfig;
import io.kamax.mxisd.lookup.SingleLookupReply;
import io.kamax.mxisd.lookup.SingleLookupRequest;
import io.kamax.mxisd.lookup.ThreePidMapping;
import io.kamax.mxisd.lookup.provider.IThreePidProvider;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public abstract class SqlThreePidProvider implements IThreePidProvider {

    private transient final Logger log = LoggerFactory.getLogger(SqlThreePidProvider.class);

    private SqlConfig cfg;
    private MatrixConfig mxCfg;

    private SqlConnectionPool pool;

    public SqlThreePidProvider(SqlConfig cfg, MatrixConfig mxCfg) {
        this.cfg = cfg;
        this.pool = new SqlConnectionPool(cfg);
        this.mxCfg = mxCfg;
    }

    @Override
    public boolean isLocal() {
        return true;
    }

    @Override
    public int getPriority() {
        return 20;
    }

    @Override
    public Optional<SingleLookupReply> find(SingleLookupRequest request) {
        log.info("SQL lookup");
        String stmtSql = StringUtils.defaultIfBlank(cfg.getIdentity().getMedium().get(request.getType()), cfg.getIdentity().getQuery());
        log.info("SQL query: {}", stmtSql);
        try (Connection conn = pool.get()) {
            try (PreparedStatement stmt = conn.prepareStatement(stmtSql)) {
                stmt.setString(1, request.getType().toLowerCase());
                stmt.setString(2, request.getThreePid().toLowerCase());

                try (ResultSet rSet = stmt.executeQuery()) {
                    while (rSet.next()) {
                        String uid = rSet.getString("uid");
                        log.info("Found match: {}", uid);
                        if (StringUtils.equals("uid", cfg.getIdentity().getType())) {
                            log.info("Resolving as localpart");
                            return Optional.of(new SingleLookupReply(request, MatrixID.asAcceptable(uid, mxCfg.getDomain())));
                        }
                        if (StringUtils.equals("mxid", cfg.getIdentity().getType())) {
                            log.info("Resolving as MXID");
                            return Optional.of(new SingleLookupReply(request, MatrixID.asAcceptable(uid)));
                        }

                        log.info("Identity type is unknown, skipping");
                    }

                    log.info("No match found in SQL");
                    return Optional.empty();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<ThreePidMapping> populate(List<ThreePidMapping> mappings) {
        return new ArrayList<>();
    }

    @Override
    public Iterable<ThreePidMapping> populateHashes() {
        String query = cfg.getLookup().getQuery();
        if (StringUtils.isBlank(query)) {
            log.warn("Lookup query not configured, skip.");
            return Collections.emptyList();
        }

        log.debug("Uses query to match users: {}", query);
        List<ThreePidMapping> result = new ArrayList<>();
        try (Connection connection = pool.get()) {
            PreparedStatement statement = connection.prepareStatement(query);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String mxid = resultSet.getString("mxid");
                    String medium = resultSet.getString("medium");
                    String address = resultSet.getString("address");
                    result.add(new ThreePidMapping(medium, address, mxid));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return result;
    }
}
