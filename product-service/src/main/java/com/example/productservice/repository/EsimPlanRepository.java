package com.example.productservice.repository;
import com.example.productservice.model.EsimPlan;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
public interface EsimPlanRepository extends MongoRepository<EsimPlan,String>{List<EsimPlan> findByActiveTrue();}
