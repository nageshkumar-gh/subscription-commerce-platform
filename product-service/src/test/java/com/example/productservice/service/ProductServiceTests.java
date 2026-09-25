package com.example.productservice.service;
import com.example.productservice.model.Product;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
class ProductServiceTests {
    @Test void productCarriesCatalogueDetails(){
        Product product=new Product(null,"IPHONE-18-PRO-512","iPhone 18 Pro","Pro phone","512 GB","Burgundy",new BigDecimal("899.00"),List.of("OLED display"),true);
        assertEquals("512 GB",product.getStorage());assertEquals(new BigDecimal("899.00"),product.getPrice());assertTrue(product.isActive());
    }
}
