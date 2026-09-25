package com.example.orderservice.model;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.math.BigDecimal;
import java.time.Instant;
@Document(collection="orders")
public class Order {
    @Id private String id;
    @Indexed private String customerId;
    private String productId;
    private String productName;
    private String storage;
    private String planId;
    private String planName;
    private BigDecimal devicePrice;
    private BigDecimal monthlyPrice;
    private BigDecimal total;
    private OrderStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    public Order(){}
    public String getId(){return id;} public void setId(String id){this.id=id;}
    public String getCustomerId(){return customerId;} public void setCustomerId(String v){customerId=v;}
    public String getProductId(){return productId;} public void setProductId(String v){productId=v;}
    public String getProductName(){return productName;} public void setProductName(String v){productName=v;}
    public String getStorage(){return storage;} public void setStorage(String v){storage=v;}
    public String getPlanId(){return planId;} public void setPlanId(String v){planId=v;}
    public String getPlanName(){return planName;} public void setPlanName(String v){planName=v;}
    public BigDecimal getDevicePrice(){return devicePrice;} public void setDevicePrice(BigDecimal v){devicePrice=v;}
    public BigDecimal getMonthlyPrice(){return monthlyPrice;} public void setMonthlyPrice(BigDecimal v){monthlyPrice=v;}
    public BigDecimal getTotal(){return total;} public void setTotal(BigDecimal v){total=v;}
    public OrderStatus getStatus(){return status;} public void setStatus(OrderStatus v){status=v;}
    public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant v){createdAt=v;}
    public Instant getUpdatedAt(){return updatedAt;} public void setUpdatedAt(Instant v){updatedAt=v;}
}
