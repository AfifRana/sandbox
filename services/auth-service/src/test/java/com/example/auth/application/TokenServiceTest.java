package com.example.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenServiceTest {

    private TokenService tokens;
    private RSAPublicKey publicKey;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        publicKey = (RSAPublicKey) pair.getPublic();
        tokens = new TokenService(
                (RSAPrivateKey) pair.getPrivate(), publicKey, "test-key", "http://auth", Duration.ofMinutes(5));
    }

    @Test
    void issuedTokenIsVerifiableWithPublicKeyAndCarriesClaims() throws Exception {
        String token = tokens.issueToken("alice", List.of("CUSTOMER"));

        SignedJWT parsed = tokens.parse(token);

        assertThat(parsed.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        assertThat(parsed.getHeader().getKeyID()).isEqualTo("test-key");
        assertThat(parsed.verify(new RSASSAVerifier(publicKey))).isTrue();

        JWTClaimsSet claims = parsed.getJWTClaimsSet();
        assertThat(claims.getSubject()).isEqualTo("alice");
        assertThat(claims.getIssuer()).isEqualTo("http://auth");
        assertThat(claims.getStringListClaim("roles")).containsExactly("CUSTOMER");
        assertThat(claims.getExpirationTime()).isAfter(claims.getIssueTime());
    }

    @Test
    void eachTokenGetsUniqueJti() throws Exception {
        String first = tokens.issueToken("alice", List.of("CUSTOMER"));
        String second = tokens.issueToken("alice", List.of("CUSTOMER"));

        String firstJti = tokens.parse(first).getJWTClaimsSet().getJWTID();
        String secondJti = tokens.parse(second).getJWTClaimsSet().getJWTID();

        assertThat(firstJti).isNotEqualTo(secondJti);
    }
}
