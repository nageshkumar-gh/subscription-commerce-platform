package com.example.productservice.controller;
import com.example.productservice.model.EsimPlan;
import com.example.productservice.repository.EsimPlanRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
@RestController @RequestMapping("/api/esim-plans")
@Tag(name="eSIM plans",description="Available mobile data plans")
public class EsimPlanController {
    private final EsimPlanRepository repository;
    public EsimPlanController(EsimPlanRepository repository){this.repository=repository;}
    @GetMapping @Operation(summary="List active eSIM plans") public List<EsimPlan> list(){return repository.findByActiveTrue();}
}
