package com.example.productservice.controller;
import com.example.productservice.model.EsimPlan;
import com.example.productservice.service.EsimPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/admin/esim-plans")
@Tag(name="eSIM plan administration",description="Plan writes require the catalog:write OAuth scope")
public class EsimPlanAdminController {
    private final EsimPlanService service;
    public EsimPlanAdminController(EsimPlanService service){this.service=service;}
    @PostMapping @Operation(summary="Create an eSIM plan") public ResponseEntity<EsimPlan> create(@Valid @RequestBody EsimPlan plan){return ResponseEntity.status(HttpStatus.CREATED).body(service.create(plan));}
    @PutMapping("/{id}") @Operation(summary="Update an eSIM plan") public EsimPlan update(@PathVariable String id,@Valid @RequestBody EsimPlan plan){return service.update(id,plan);}
    @DeleteMapping("/{id}") @Operation(summary="Delete an eSIM plan") public ResponseEntity<Void> delete(@PathVariable String id){service.delete(id);return ResponseEntity.noContent().build();}
}
