package com.example.productservice;

import com.example.productservice.model.Product;
import com.example.productservice.repository.EsimPlanRepository;
import com.example.productservice.repository.ProductRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.data.mongodb.auto-index-creation=false")
@AutoConfigureMockMvc
class ProductSecurityIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired SecretKey jwtSecretKey;
    @MockitoBean ProductRepository products;
    @MockitoBean EsimPlanRepository plans;

    @BeforeEach
    void setUp() {
        reset(products, plans);
        when(products.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void publicCatalogueDoesNotRequireAuthentication() throws Exception {
        when(products.findByActiveTrue()).thenReturn(List.of());
        mvc.perform(get("/api/products")).andExpect(status().isOk());
    }

    @Test
    void adminWriteRejectsMissingOrCustomerOnlyToken() throws Exception {
        mvc.perform(post("/api/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson()))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/admin/products")
                        .header("Authorization", "Bearer " + token(null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    void catalogScopeCanExerciseAdminWrite() throws Exception {
        mvc.perform(post("/api/admin/products")
                        .header("Authorization", "Bearer " + token("catalog:write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson()))
                .andExpect(status().isCreated());
    }

    private String token(String scope) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("customer-service")
                .subject("customer-1")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300));
        if (scope != null) claims.claim("scope", scope);
        return NimbusJwtEncoder.withSecretKey(jwtSecretKey).build()
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
                .getTokenValue();
    }

    private String productJson() {
        return """
                {"sku":"PHONE-1","name":"Phone","description":"A phone","storage":"128 GB",
                 "finish":"Black","price":499.00,"features":["5G"],"active":true}
                """;
    }
}
