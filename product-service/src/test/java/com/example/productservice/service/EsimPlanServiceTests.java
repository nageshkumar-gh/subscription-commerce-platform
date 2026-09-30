package com.example.productservice.service;
import com.example.productservice.exception.EsimPlanNotFoundException;
import com.example.productservice.model.EsimPlan;
import com.example.productservice.repository.EsimPlanRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class EsimPlanServiceTests {
    private final EsimPlanRepository repository=mock(EsimPlanRepository.class);
    private final EsimPlanService service=new EsimPlanService(repository);
    private EsimPlan plan(String code){return new EsimPlan("id-1",code," Unlimited "," Unlimited data ",new BigDecimal("29.99"),true);}
    @Test void createsNormalizedPlan(){EsimPlan plan=plan(" unlimited ");when(repository.save(plan)).thenReturn(plan);EsimPlan saved=service.create(plan);assertNull(saved.getId());assertEquals("UNLIMITED",saved.getCode());assertEquals("Unlimited",saved.getName());}
    @Test void rejectsDuplicateCode(){EsimPlan plan=plan("UNLIMITED");when(repository.existsByCode("UNLIMITED")).thenReturn(true);assertThrows(IllegalArgumentException.class,()->service.create(plan));}
    @Test void rejectsUnknownUpdate(){when(repository.findById("missing")).thenReturn(Optional.empty());assertThrows(EsimPlanNotFoundException.class,()->service.update("missing",plan("UNLIMITED")));}
    @Test void listsActivePlans(){when(repository.findByActiveTrue()).thenReturn(List.of(plan("UNLIMITED")));assertEquals(1,service.getActivePlans().size());}
}
