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

package io.kamax.mxisd.lookup.strategy;

import io.kamax.mxisd.lookup.BulkLookupRequest;
import io.kamax.mxisd.lookup.HashLookupRequest;
import io.kamax.mxisd.lookup.SingleLookupReply;
import io.kamax.mxisd.lookup.SingleLookupRequest;
import io.kamax.mxisd.lookup.ThreePidMapping;
import io.kamax.mxisd.lookup.provider.IThreePidProvider;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface LookupStrategy {

    List<IThreePidProvider> getLocalProviders();

    Optional<SingleLookupReply> find(String medium, String address, boolean recursive);

    Optional<SingleLookupReply> findLocal(String medium, String address);

    Optional<SingleLookupReply> findRemote(String medium, String address);

    Optional<SingleLookupReply> find(SingleLookupRequest request);

    Optional<SingleLookupReply> findRecursive(SingleLookupRequest request);

    CompletableFuture<List<ThreePidMapping>> find(BulkLookupRequest requests);
}
