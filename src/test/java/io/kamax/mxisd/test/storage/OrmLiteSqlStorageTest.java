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

package io.kamax.mxisd.test.storage;

import io.kamax.mxisd.config.SQLiteStorageConfig;
import io.kamax.mxisd.config.StorageConfig;
import io.kamax.mxisd.storage.ormlite.OrmLiteSqlStorage;
import org.junit.Test;

import java.time.Instant;

public class OrmLiteSqlStorageTest {

    @Test
    public void insertAsTxnDuplicate() {
        StorageConfig.Provider provider = new StorageConfig.Provider();
        SQLiteStorageConfig config = new SQLiteStorageConfig();
        config.setDatabase(":memory:");
        provider.setSqlite(config);
        OrmLiteSqlStorage store = new OrmLiteSqlStorage(StorageConfig.BackendEnum.sqlite, provider);
        store.insertTransactionResult("mxisd", "1", Instant.now(), "{}");
        store.insertTransactionResult("mxisd", "2", Instant.now(), "{}");
    }

    @Test(expected = RuntimeException.class)
    public void insertAsTxnSame() {
        StorageConfig.Provider provider = new StorageConfig.Provider();
        SQLiteStorageConfig config = new SQLiteStorageConfig();
        config.setDatabase(":memory:");
        provider.setSqlite(config);
        OrmLiteSqlStorage store = new OrmLiteSqlStorage(StorageConfig.BackendEnum.sqlite, provider);
        store.insertTransactionResult("mxisd", "1", Instant.now(), "{}");
        store.insertTransactionResult("mxisd", "1", Instant.now(), "{}");
    }

}
