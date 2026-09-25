package com.example.productservice.config;
import com.example.productservice.model.EsimPlan;
import com.example.productservice.model.Product;
import com.example.productservice.repository.EsimPlanRepository;
import com.example.productservice.repository.ProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.math.BigDecimal;
import java.util.List;
@Configuration
public class ProductCatalogSeeder {
    @Bean CommandLineRunner seedCatalog(ProductRepository products, EsimPlanRepository plans){return args->{
        if(products.count()==0){products.saveAll(List.of(
            new Product(null,"IPHONE-18-PRO-512","iPhone 18 Pro","A powerful Pro iPhone with generous storage for photos, apps, and 4K video.","512 GB","Burgundy",new BigDecimal("899.00"),List.of("6.3-inch Super Retina XDR display","48 MP Pro camera system","All-day battery"),true),
            new Product(null,"IPHONE-18-PRO-MAX-1TB","iPhone 18 Pro Max","The largest Pro iPhone with maximum storage for demanding creative work.","1 TB","Burgundy",new BigDecimal("1299.00"),List.of("6.9-inch Super Retina XDR display","48 MP Pro camera system","Up to 45 hours video playback"),true)
        ));}
        if(plans.count()==0){plans.saveAll(List.of(
            new EsimPlan(null,"LIMITED-2GB-DAY","Everyday 2 GB","2 GB of high-speed data per day",new BigDecimal("14.99"),true),
            new EsimPlan(null,"UNLIMITED","Unlimited","Unlimited data, calls, and texts",new BigDecimal("29.99"),true)
        ));}
    };}
}
