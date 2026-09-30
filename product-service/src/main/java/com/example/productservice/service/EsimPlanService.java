package com.example.productservice.service;
import com.example.productservice.exception.EsimPlanNotFoundException;
import com.example.productservice.model.EsimPlan;
import com.example.productservice.repository.EsimPlanRepository;
import java.util.List;
import org.springframework.stereotype.Service;
@Service
public class EsimPlanService {
    private final EsimPlanRepository repository;
    public EsimPlanService(EsimPlanRepository repository){this.repository=repository;}
    public List<EsimPlan> getActivePlans(){return repository.findByActiveTrue();}
    public EsimPlan create(EsimPlan plan){normalize(plan);if(repository.existsByCode(plan.getCode()))throw new IllegalArgumentException("eSIM plan with this code already exists");plan.setId(null);return repository.save(plan);}
    public EsimPlan update(String id,EsimPlan update){normalize(update);EsimPlan plan=repository.findById(id).orElseThrow(()->new EsimPlanNotFoundException("eSIM plan not found with ID: "+id));if(repository.existsByCodeAndIdNot(update.getCode(),id))throw new IllegalArgumentException("eSIM plan with this code already exists");plan.setCode(update.getCode());plan.setName(update.getName());plan.setDescription(update.getDescription());plan.setMonthlyPrice(update.getMonthlyPrice());plan.setActive(update.isActive());return repository.save(plan);}
    public void delete(String id){EsimPlan plan=repository.findById(id).orElseThrow(()->new EsimPlanNotFoundException("eSIM plan not found with ID: "+id));repository.delete(plan);}
    private void normalize(EsimPlan plan){plan.setCode(plan.getCode().trim().toUpperCase());plan.setName(plan.getName().trim());plan.setDescription(plan.getDescription().trim());}
}
