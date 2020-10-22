package io.kamax.mxisd.hash.storage;

import io.kamax.mxisd.lookup.ThreePidMapping;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Collection;

public interface HashStorage {

    Collection<Pair<String, ThreePidMapping>> find(Iterable<String> hashes);

    void add(ThreePidMapping pidMapping, String hash);

    void clear();
}
