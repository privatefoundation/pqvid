package io.kamax.mxisd.hash.storage;

import io.kamax.mxisd.lookup.ThreePidMapping;
import io.kamax.mxisd.storage.IStorage;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Collection;

public class SqlHashStorage implements HashStorage {

    private final IStorage storage;

    public SqlHashStorage(IStorage storage) {
        this.storage = storage;
        Runtime.getRuntime().addShutdownHook(new Thread(storage::clearHashes));
    }

    @Override
    public Collection<Pair<String, ThreePidMapping>> find(Iterable<String> hashes) {
        return storage.findHashes(hashes);
    }

    @Override
    public void add(ThreePidMapping pidMapping, String hash) {
        storage.addHash(pidMapping.getMxid(), pidMapping.getMedium(), pidMapping.getValue(), hash);
    }

    @Override
    public void clear() {
        storage.clearHashes();
    }
}
