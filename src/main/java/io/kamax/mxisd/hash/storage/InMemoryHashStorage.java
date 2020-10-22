package io.kamax.mxisd.hash.storage;

import io.kamax.mxisd.lookup.ThreePidMapping;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryHashStorage implements HashStorage {

    private final Map<String, ThreePidMapping> mapping = new ConcurrentHashMap<>();

    @Override
    public Collection<Pair<String, ThreePidMapping>> find(Iterable<String> hashes) {
        List<Pair<String, ThreePidMapping>> result = new ArrayList<>();
        for (String hash : hashes) {
            ThreePidMapping pidMapping = mapping.get(hash);
            if (pidMapping != null) {
                result.add(Pair.of(hash, pidMapping));
            }
        }
        return result;
    }

    @Override
    public void add(ThreePidMapping pidMapping, String hash) {
        mapping.put(hash, pidMapping);
    }

    @Override
    public void clear() {
        mapping.clear();
    }
}
