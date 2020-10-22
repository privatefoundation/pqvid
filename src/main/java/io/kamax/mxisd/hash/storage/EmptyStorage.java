package io.kamax.mxisd.hash.storage;

import io.kamax.mxisd.lookup.ThreePidMapping;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Collection;
import java.util.Collections;

public class EmptyStorage implements HashStorage {

    @Override
    public Collection<Pair<String, ThreePidMapping>> find(Iterable<String> hashes) {
        return Collections.emptyList();
    }

    @Override
    public void add(ThreePidMapping pidMapping, String hash) {

    }

    @Override
    public void clear() {

    }
}
