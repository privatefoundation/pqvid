/*
 * mxisd - Matrix Identity Server Daemon
 * Copyright (C) 2019 Kamax Sàrl
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

package io.kamax.mxisd.crypto.ed25519;

import com.google.gson.JsonObject;
import io.kamax.matrix.codec.MxBase64;
import io.kamax.matrix.json.MatrixJson;
import io.kamax.mxisd.config.MxisdConfig;
import io.kamax.mxisd.config.ServerConfig;
import io.kamax.mxisd.crypto.KeyIdentifier;
import io.kamax.mxisd.crypto.Signature;
import io.kamax.mxisd.crypto.SignatureManager;
import net.i2p.crypto.eddsa.EdDSAEngine;

import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.util.Base64;

public class Ed25519SignatureManager implements SignatureManager {

    private final ServerConfig cfg;
    private final Ed25519KeyManager keyMgr;

    public Ed25519SignatureManager(MxisdConfig cfg, Ed25519KeyManager keyMgr) {
        this.cfg = cfg.getServer();
        this.keyMgr = keyMgr;
    }

    @Override
    public JsonObject signMessageGson(JsonObject message) throws IllegalArgumentException {
        return signMessageGson(cfg.getName(), message);
    }

    @Override
    public JsonObject signMessageGson(String domain, String message) {
        Signature sign = sign(message);

        JsonObject keySignature = new JsonObject();
        keySignature.addProperty(sign.getKey().getAlgorithm() + ":" + sign.getKey().getSerial(), sign.getSignature());
        JsonObject signature = new JsonObject();
        signature.add(domain, keySignature);

        return signature;
    }

    @Override
    public Signature sign(JsonObject obj) {
        return sign(MatrixJson.encodeCanonical(obj));
    }

    @Override
    public Signature sign(byte[] data) {
        try {
            KeyIdentifier signingKeyId = keyMgr.getServerSigningKey().getId();
            EdDSAEngine signEngine = new EdDSAEngine(MessageDigest.getInstance(keyMgr.getKeySpecs().getHashAlgorithm()));
            signEngine.initSign(keyMgr.getPrivateKey(signingKeyId));
            byte[] signRaw = signEngine.signOneShot(data);
            String sign = MxBase64.encode(signRaw);

            return new Signature() {
                @Override
                public KeyIdentifier getKey() {
                    return signingKeyId;
                }

                @Override
                public String getSignature() {
                    return sign;
                }
            };
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean verify(PublicKey publicKey, String signature, byte[] data) {
        try {
            EdDSAEngine signEngine = new EdDSAEngine(MessageDigest.getInstance(keyMgr.getKeySpecs().getHashAlgorithm()));
            signEngine.initVerify(publicKey);
            signEngine.update(data);
            return signEngine.verify(Base64.getDecoder().decode(signature));
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            throw new RuntimeException(e);
        }
    }
}
