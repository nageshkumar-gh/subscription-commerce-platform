package com.example.productservice.controller;

import com.example.productservice.model.Product;
import com.example.productservice.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/products")
@Tag(name = "Product administration", description = "Catalogue writes require the catalog:write OAuth scope")
public class ProductAdminController {
    private final ProductService service;
    public ProductAdminController(ProductService service){this.service=service;}
    @GetMapping @Operation(summary="List the complete catalogue") public List<Product> list(){return service.getAllProducts();}
    @PostMapping @Operation(summary="Create a product") public ResponseEntity<Product> create(@Valid @RequestBody Product product){return ResponseEntity.status(HttpStatus.CREATED).body(service.create(product));}
    @PutMapping("/{id}") @Operation(summary="Update a product") public Product update(@PathVariable String id,@Valid @RequestBody Product product){return service.update(id,product);}
    @DeleteMapping("/{id}") @Operation(summary="Delete a product") public ResponseEntity<Void> delete(@PathVariable String id){service.delete(id);return ResponseEntity.noContent().build();}
}
