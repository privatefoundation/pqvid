package io.kamax.mxisd.lookup;

import java.util.List;

public class HashLookupRequest extends ALookupRequest {

    private List<String> hashes;

    public List<String> getHashes() {
        return hashes;
    }

    public void setHashes(List<String> hashes) {
        this.hashes = hashes;
    }
}
