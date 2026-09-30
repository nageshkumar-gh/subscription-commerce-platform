package com.example.productservice.controller;
import com.example.productservice.model.Product;
import com.example.productservice.service.ProductService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
@RestController @RequestMapping("/api/products")
@Tag(name="Products",description="Manage the phone catalogue")
public class ProductController {
    private final ProductService service;
    public ProductController(ProductService service){this.service=service;}
    @GetMapping @Operation(summary="List active products") public List<Product> list(){return service.getActiveProducts();}
    @GetMapping("/{id}") @Operation(summary="Get an active product by ID") public Product get(@PathVariable String id){return service.getActiveById(id);}
}
