package com.example.productservice.service;
import com.example.productservice.exception.ProductNotFoundException;
import com.example.productservice.model.Product;
import com.example.productservice.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ProductServiceTests {
    private final ProductRepository repository=mock(ProductRepository.class);
    private final ProductService service=new ProductService(repository);
    private Product product(String sku){return new Product("id-1",sku," iPhone 18 Pro "," Pro phone "," 512 GB "," Burgundy ",new BigDecimal("899.00"),List.of(" OLED display "),true);}
    @Test void createsNormalizedProductWithoutAcceptingClientId(){Product input=product(" iphone-18-pro-512 ");when(repository.save(input)).thenReturn(input);Product saved=service.create(input);assertNull(saved.getId());assertEquals("IPHONE-18-PRO-512",saved.getSku());assertEquals("iPhone 18 Pro",saved.getName());verify(repository).save(input);}
    @Test void rejectsDuplicateSku(){Product input=product("SKU-1");when(repository.existsBySku("SKU-1")).thenReturn(true);assertThrows(IllegalArgumentException.class,()->service.create(input));verify(repository,never()).save(any());}
    @Test void returnsNotFoundForUnknownId(){when(repository.findById("missing")).thenReturn(Optional.empty());assertThrows(ProductNotFoundException.class,()->service.getById("missing"));}
    @Test void doesNotExposeInactiveProductThroughPublicLookup(){when(repository.findByIdAndActiveTrue("inactive")).thenReturn(Optional.empty());assertThrows(ProductNotFoundException.class,()->service.getActiveById("inactive"));}
    @Test void preventsSkuCollisionDuringUpdate(){Product update=product("SKU-2");when(repository.findById("id-1")).thenReturn(Optional.of(product("SKU-1")));when(repository.existsBySkuAndIdNot("SKU-2","id-1")).thenReturn(true);assertThrows(IllegalArgumentException.class,()->service.update("id-1",update));verify(repository,never()).save(any());}
    @Test void listsOnlyActiveProducts(){when(repository.findByActiveTrue()).thenReturn(List.of(product("SKU-1")));assertEquals(1,service.getActiveProducts().size());verify(repository).findByActiveTrue();}
}
