package com.example.productservice.model;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.math.BigDecimal;
import java.util.List;
@Document(collection = "products")
public class Product {
    @Id private String id;
    @NotBlank @Size(max = 64) @Indexed(unique = true) private String sku;
    @NotBlank @Size(max = 120) private String name;
    @NotBlank @Size(max = 1000) private String description;
    @NotBlank private String storage;
    @NotBlank private String finish;
    @NotNull @DecimalMin(value = "0.0", inclusive = false) private BigDecimal price;
    @NotEmpty @Size(max = 20) private List<@NotBlank @Size(max = 200) String> features;
    private boolean active;
    public Product() {}
    public Product(String id, String sku, String name, String description, String storage, String finish, BigDecimal price, List<String> features, boolean active) {
        this.id=id; this.sku=sku; this.name=name; this.description=description; this.storage=storage; this.finish=finish; this.price=price; this.features=features; this.active=active;
    }
    public String getId(){return id;} public void setId(String id){this.id=id;}
    public String getSku(){return sku;} public void setSku(String sku){this.sku=sku;}
    public String getName(){return name;} public void setName(String name){this.name=name;}
    public String getDescription(){return description;} public void setDescription(String description){this.description=description;}
    public String getStorage(){return storage;} public void setStorage(String storage){this.storage=storage;}
    public String getFinish(){return finish;} public void setFinish(String finish){this.finish=finish;}
    public BigDecimal getPrice(){return price;} public void setPrice(BigDecimal price){this.price=price;}
    public List<String> getFeatures(){return features;} public void setFeatures(List<String> features){this.features=features;}
    public boolean isActive(){return active;} public void setActive(boolean active){this.active=active;}
}
