package com.example.customerservice;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.customerservice.controller.TokenVerificationController;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class TokenVerificationControllerTests {
    private final TokenVerificationController controller = new TokenVerificationController();

    private static Jwt token(String scope) {
        Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "HS256").subject("customer-1").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        if (scope != null) builder.claim("scope", scope);
        return builder.build();
    }

    @Test
    void acceptsTokenWithRequiredScopeAndReturnsCaller() {
        var response = controller.verify(token("catalog:write orders:operate"), "orders:operate");
        assertEquals(204, response.getStatusCode().value());
        assertEquals("customer-1", response.getHeaders().getFirst("X-Customer-Id"));
    }

    @Test
    void rejectsTokenWithoutRequiredScope() {
        assertEquals(403, controller.verify(token(null), "orders:operate").getStatusCode().value());
        assertEquals(403, controller.verify(token("catalog:write"), "orders:operate").getStatusCode().value());
    }

    @Test
    void anyValidTokenPassesWhenNoScopeIsRequired() {
        assertEquals(204, controller.verify(token(null), null).getStatusCode().value());
    }
}
