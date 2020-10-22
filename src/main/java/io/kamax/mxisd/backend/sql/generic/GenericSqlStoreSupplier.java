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

package io.kamax.mxisd.backend.sql.generic;

import io.kamax.mxisd.Mxisd;
import io.kamax.mxisd.auth.AuthProviders;
import io.kamax.mxisd.backend.IdentityStoreSupplier;
import io.kamax.mxisd.directory.DirectoryProviders;
import io.kamax.mxisd.lookup.ThreePidProviders;
import io.kamax.mxisd.profile.ProfileProviders;

public class GenericSqlStoreSupplier implements IdentityStoreSupplier {

    @Override
    public void accept(Mxisd mxisd) {
        if (mxisd.getConfig().getSql().getAuth().isEnabled()) {
            AuthProviders.register(() -> new GenericSqlAuthProvider(mxisd.getConfig().getSql(), mxisd.getInvite()));
        }

        if (mxisd.getConfig().getSql().getDirectory().isEnabled()) {
            DirectoryProviders.register(() -> new GenericSqlDirectoryProvider(mxisd.getConfig().getSql(), mxisd.getConfig().getMatrix()));
        }

        if (mxisd.getConfig().getSql().getIdentity().isEnabled()) {
            ThreePidProviders.register(() -> new GenericSqlThreePidProvider(mxisd.getConfig().getSql(), mxisd.getConfig().getMatrix()));
        }

        if (mxisd.getConfig().getSql().getProfile().isEnabled()) {
            ProfileProviders.register(() -> new GenericSqlProfileProvider(mxisd.getConfig().getSql()));
        }
    }

}
