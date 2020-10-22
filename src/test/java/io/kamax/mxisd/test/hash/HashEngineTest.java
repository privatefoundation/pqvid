package io.kamax.mxisd.test.hash;

import static org.junit.Assert.assertEquals;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.Test;

import java.util.Base64;

public class HashEngineTest {

    @Test
    public void sha256test() {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        assertEquals("rujYzy1w0JxulN_rVlErGUmkdXT5znL0sjSF_IWreko",
            encoder.encodeToString(DigestUtils.sha256("user@mail.homeserver.tld email I9x4vpcWjqp9X8iiOY4a")));
    }
}
