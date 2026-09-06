package com.example.auth.adapter.in.jwks;

import com.example.auth.application.TokenService;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the public signing key as a JWKS document. Resource servers fetch
 * this to verify RS256 tokens — standard OAuth2 discovery flow.
 */
@RestController
public class JwksController {

    private final TokenService tokens;

    public JwksController(TokenService tokens) {
        this.tokens = tokens;
    }

    @GetMapping("/oauth2/jwks")
    public Map<String, Object> jwks() {
        RSAKey key = new RSAKey.Builder(tokens.publicKey())
                .keyID(tokens.keyId())
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .build();
        // toJSONObject() emits the standard JWK fields (kty, n, e, kid, alg);
        // returning the JWKSet POJO directly would serialize Java internals.
        return new JWKSet(List.of(key)).toJSONObject();
    }
}
