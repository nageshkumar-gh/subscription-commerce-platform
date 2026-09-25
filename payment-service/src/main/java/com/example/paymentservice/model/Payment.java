package com.example.paymentservice.model;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.math.BigDecimal;
import java.time.Instant;
@Document(collection="payments")
public class Payment {
    @Id private String id;
    @Indexed(unique=true) private String orderId;
    @Indexed private String customerId;
    private BigDecimal amount;
    private String currency;
    private String paymentMethod;
    private String transactionReference;
    private PaymentStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    public Payment(){}
    public String getId(){return id;} public void setId(String v){id=v;}
    public String getOrderId(){return orderId;} public void setOrderId(String v){orderId=v;}
    public String getCustomerId(){return customerId;} public void setCustomerId(String v){customerId=v;}
    public BigDecimal getAmount(){return amount;} public void setAmount(BigDecimal v){amount=v;}
    public String getCurrency(){return currency;} public void setCurrency(String v){currency=v;}
    public String getPaymentMethod(){return paymentMethod;} public void setPaymentMethod(String v){paymentMethod=v;}
    public String getTransactionReference(){return transactionReference;} public void setTransactionReference(String v){transactionReference=v;}
    public PaymentStatus getStatus(){return status;} public void setStatus(PaymentStatus v){status=v;}
    public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant v){createdAt=v;}
    public Instant getUpdatedAt(){return updatedAt;} public void setUpdatedAt(Instant v){updatedAt=v;}
}
