package com.fundsindia.mfcpoc.crypto;

import java.util.List;
import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jwa.AlgorithmConstraints.ConstraintType;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.JsonWebKeySet;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwx.HeaderParameterNames;
import org.jose4j.lang.JoseException;

/**
 * Detached-payload RS256 JWS with an unencoded payload (b64=false), as MFCentral's reference code does.
 * Requests are signed with our private key. Responses are verified against MFCentral's trusted public keys:
 * MFC issued more than one, so the key is picked by the {@code kid} in the response header, falling back to
 * trying each key when the header has no {@code kid}.
 */
public final class PayloadSigner {

    private final RsaJsonWebKey signingKey;
    private final List<RsaJsonWebKey> trustedKeys;

    public PayloadSigner(String privateKeyJwks, String mfCentralPublicKeysJwks) {
        List<RsaJsonWebKey> privateKeys = rsaKeys(privateKeyJwks, "private");
        this.signingKey = privateKeys.getFirst();
        if (signingKey.getPrivateKey() == null) {
            throw new IllegalArgumentException("Private key JWKS does not contain RSA private key material");
        }
        this.trustedKeys = rsaKeys(mfCentralPublicKeysJwks, "MFCentral public");
    }

    public String sign(String payload) {
        try {
            var jws = new JsonWebSignature();
            jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
            jws.setKeyIdHeaderValue(signingKey.getKeyId());
            jws.getHeaders().setObjectHeaderValue(HeaderParameterNames.BASE64URL_ENCODE_PAYLOAD, false);
            jws.setPayload(payload);
            jws.setKey(signingKey.getPrivateKey());
            return jws.getDetachedContentCompactSerialization();
        } catch (JoseException e) {
            throw new IllegalStateException("Signing request payload failed", e);
        }
    }

    /** Returns false for a malformed or non-matching signature; never throws for bad input from the wire. */
    public boolean verify(String payload, String detachedSignature) {
        try {
            String kid = parse(detachedSignature, payload).getKeyIdHeaderValue();
            List<RsaJsonWebKey> candidates = kid == null
                    ? trustedKeys
                    : trustedKeys.stream().filter(k -> kid.equals(k.getKeyId())).toList();
            for (RsaJsonWebKey key : candidates) {
                JsonWebSignature jws = parse(detachedSignature, payload);
                jws.setKey(key.getPublicKey());
                if (jws.verifySignature()) {
                    return true;
                }
            }
            return false;
        } catch (JoseException e) {
            return false;
        }
    }

    private static JsonWebSignature parse(String detachedSignature, String payload) throws JoseException {
        var jws = new JsonWebSignature();
        jws.setAlgorithmConstraints(new AlgorithmConstraints(ConstraintType.PERMIT, AlgorithmIdentifiers.RSA_USING_SHA256));
        jws.setCompactSerialization(detachedSignature);
        jws.setPayload(payload);
        return jws;
    }

    private static List<RsaJsonWebKey> rsaKeys(String jwks, String description) {
        try {
            List<JsonWebKey> keys = new JsonWebKeySet(jwks).getJsonWebKeys();
            if (keys.isEmpty()) {
                throw new IllegalArgumentException("Empty " + description + " key JWKS");
            }
            return keys.stream().map(key -> {
                if (!(key instanceof RsaJsonWebKey rsaKey)) {
                    throw new IllegalArgumentException(description + " key " + key.getKeyId() + " is not an RSA JWK");
                }
                return rsaKey;
            }).toList();
        } catch (JoseException e) {
            throw new IllegalArgumentException("Cannot parse " + description + " key JWKS", e);
        }
    }
}
