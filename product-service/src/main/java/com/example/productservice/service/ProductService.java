package com.example.productservice.service;
import com.example.productservice.exception.ProductNotFoundException;
import com.example.productservice.model.Product;
import com.example.productservice.repository.ProductRepository;
import org.springframework.stereotype.Service;
import java.util.List;
@Service
public class ProductService {
    private final ProductRepository repository;
    public ProductService(ProductRepository repository){this.repository=repository;}
    public List<Product> getActiveProducts(){return repository.findByActiveTrue();}
    public List<Product> getAllProducts(){return repository.findAll();}
    public Product getById(String id){return repository.findById(id).orElseThrow(()->new ProductNotFoundException("Product not found with ID: "+id));}
    public Product getActiveById(String id){return repository.findByIdAndActiveTrue(id).orElseThrow(()->new ProductNotFoundException("Active product not found with ID: "+id));}
    public Product create(Product product){normalize(product);if(repository.existsBySku(product.getSku()))throw new IllegalArgumentException("Product with this SKU already exists");product.setId(null);return repository.save(product);}
    public Product update(String id,Product update){normalize(update);Product product=getById(id);if(repository.existsBySkuAndIdNot(update.getSku(),id))throw new IllegalArgumentException("Product with this SKU already exists");product.setSku(update.getSku());product.setName(update.getName());product.setDescription(update.getDescription());product.setStorage(update.getStorage());product.setFinish(update.getFinish());product.setPrice(update.getPrice());product.setFeatures(List.copyOf(update.getFeatures()));product.setActive(update.isActive());return repository.save(product);}
    public void delete(String id){repository.delete(getById(id));}
    private void normalize(Product product){product.setSku(product.getSku().trim().toUpperCase());product.setName(product.getName().trim());product.setDescription(product.getDescription().trim());product.setStorage(product.getStorage().trim());product.setFinish(product.getFinish().trim());product.setFeatures(product.getFeatures().stream().map(String::trim).toList());}
}
