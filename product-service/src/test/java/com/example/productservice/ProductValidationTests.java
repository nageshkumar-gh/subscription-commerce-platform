package com.example.productservice;

import com.example.productservice.model.EsimPlan;
import com.example.productservice.model.Product;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductValidationTests {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void productPriceIsRequired() {
        Product product = new Product(null, "PHONE-1", "Phone", "Description", "128 GB", "Black", null, List.of("5G"), true);
        assertTrue(validator.validate(product).stream().anyMatch(error -> error.getPropertyPath().toString().equals("price")));
    }

    @Test
    void esimMonthlyPriceIsRequired() {
        EsimPlan plan = new EsimPlan(null, "PLAN-1", "Plan", "Description", null, true);
        assertTrue(validator.validate(plan).stream().anyMatch(error -> error.getPropertyPath().toString().equals("monthlyPrice")));
    }
}
