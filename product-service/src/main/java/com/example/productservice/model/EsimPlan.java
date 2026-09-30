package com.example.productservice.model;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.math.BigDecimal;
@Document(collection = "esim_plans")
public class EsimPlan {
    @Id private String id;
    @NotBlank @Size(max = 64) @Indexed(unique = true) private String code;
    @NotBlank @Size(max = 120) private String name;
    @NotBlank @Size(max = 1000) private String description;
    @NotNull @DecimalMin(value = "0.0", inclusive = false) private BigDecimal monthlyPrice;
    private boolean active;
    public EsimPlan() {}
    public EsimPlan(String id,String code,String name,String description,BigDecimal monthlyPrice,boolean active){this.id=id;this.code=code;this.name=name;this.description=description;this.monthlyPrice=monthlyPrice;this.active=active;}
    public String getId(){return id;} public void setId(String id){this.id=id;}
    public String getCode(){return code;} public void setCode(String code){this.code=code;}
    public String getName(){return name;} public void setName(String name){this.name=name;}
    public String getDescription(){return description;} public void setDescription(String description){this.description=description;}
    public BigDecimal getMonthlyPrice(){return monthlyPrice;} public void setMonthlyPrice(BigDecimal monthlyPrice){this.monthlyPrice=monthlyPrice;}
    public boolean isActive(){return active;} public void setActive(boolean active){this.active=active;}
}
