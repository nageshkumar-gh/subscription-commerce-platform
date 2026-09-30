package com.example.customerservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import java.util.Arrays;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Token check for edge proxies (nginx auth_request): 204 with the caller's id when the bearer token is valid and
 * carries the requested scope, 401 when it is missing or invalid, 403 when the scope is missing.
 */
@RestController
public class TokenVerificationController {
    @GetMapping("/api/auth/verify")
    @Operation(summary = "Verify a bearer token, optionally requiring a scope")
    public ResponseEntity<Void> verify(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String scope) {
        String granted = jwt.getClaimAsString("scope");
        boolean allowed = scope == null || scope.isBlank()
            || (granted != null && Arrays.asList(granted.split(" ")).contains(scope.trim()));
        return allowed ? ResponseEntity.noContent().header("X-Customer-Id", jwt.getSubject()).build()
            : ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
}
