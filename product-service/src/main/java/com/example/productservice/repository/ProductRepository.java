package com.example.productservice.repository;
import com.example.productservice.model.Product;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
public interface ProductRepository extends MongoRepository<Product,String>{boolean existsBySku(String sku);List<Product> findByActiveTrue();}
