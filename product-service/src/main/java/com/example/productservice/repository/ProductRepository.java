package com.example.productservice.repository;
import com.example.productservice.model.Product;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;
public interface ProductRepository extends MongoRepository<Product,String>{boolean existsBySku(String sku);boolean existsBySkuAndIdNot(String sku,String id);List<Product> findByActiveTrue();Optional<Product> findByIdAndActiveTrue(String id);}
