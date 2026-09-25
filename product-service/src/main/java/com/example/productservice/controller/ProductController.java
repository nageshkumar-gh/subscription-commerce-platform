package com.example.productservice.controller;
import com.example.productservice.model.Product;
import com.example.productservice.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
@RestController @RequestMapping("/api/products")
@Tag(name="Products",description="Manage the phone catalogue")
public class ProductController {
    private final ProductService service;
    public ProductController(ProductService service){this.service=service;}
    @GetMapping @Operation(summary="List products",description="Returns active products by default; use activeOnly=false for the complete catalogue.") public List<Product> list(@RequestParam(defaultValue="true") boolean activeOnly){return activeOnly?service.getActiveProducts():service.getAllProducts();}
    @GetMapping("/{id}") @Operation(summary="Get a product by ID") public Product get(@PathVariable String id){return service.getById(id);}
    @PostMapping @Operation(summary="Create a product") public ResponseEntity<Product> create(@Valid @RequestBody Product product){return ResponseEntity.status(HttpStatus.CREATED).body(service.create(product));}
    @PutMapping("/{id}") @Operation(summary="Update a product") public Product update(@PathVariable String id,@Valid @RequestBody Product product){return service.update(id,product);}
    @DeleteMapping("/{id}") @Operation(summary="Delete a product") public ResponseEntity<Void> delete(@PathVariable String id){service.delete(id);return ResponseEntity.noContent().build();}
}
