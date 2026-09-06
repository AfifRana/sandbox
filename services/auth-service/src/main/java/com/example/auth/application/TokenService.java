package com.example.auth.application;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Issues and verifies RS256-signed JWT access tokens.
 *
 * <p>Asymmetric signing (RS256) means only auth-service holds the private key,
 * while every resource server verifies tokens with the public key exposed via
 * the JWKS endpoint — no shared secret to leak.
 */
public class TokenService {

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final String keyId;
    private final String issuer;
    private final Duration ttl;

    public TokenService(RSAPrivateKey privateKey, RSAPublicKey publicKey,
                        String keyId, String issuer, Duration ttl) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
        this.keyId = keyId;
        this.issuer = issuer;
        this.ttl = ttl;
    }

    public String issueToken(String subject, List<String> roles) {
        Instant now = Instant.now();
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(JOSEObjectType.JWT)
                .keyID(keyId)
                .build();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(subject)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(ttl)))
                .jwtID(UUID.randomUUID().toString())
                .claim("roles", roles)
                .build();
        SignedJWT jwt = new SignedJWT(header, claims);
        try {
            jwt.sign(new RSASSASigner(privateKey));
            return jwt.serialize();
        } catch (com.nimbusds.jose.JOSEException e) {
            throw new IllegalStateException("Failed to sign token", e);
        }
    }

    public SignedJWT parse(String token) throws ParseException {
        return SignedJWT.parse(token);
    }

    public RSAPublicKey publicKey() {
        return publicKey;
    }

    public String keyId() {
        return keyId;
    }
}
