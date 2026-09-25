package com.example.orderservice.service;
import com.example.orderservice.exception.OrderNotFoundException;
import com.example.orderservice.model.CreateOrderRequest;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderStatus;
import com.example.orderservice.repository.OrderRepository;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
@Service
public class OrderService {
    private final OrderRepository repository;
    public OrderService(OrderRepository repository){this.repository=repository;}
    public Order create(CreateOrderRequest request){
        Order order=new Order();
        order.setCustomerId(request.customerId());order.setProductId(request.productId());order.setProductName(request.productName());order.setStorage(request.storage());
        order.setPlanId(request.planId());order.setPlanName(request.planName());order.setDevicePrice(request.devicePrice());order.setMonthlyPrice(request.monthlyPrice());
        order.setTotal(calculateTotal(request.devicePrice(),request.monthlyPrice()));order.setStatus(OrderStatus.PENDING_PAYMENT);
        Instant now=Instant.now();order.setCreatedAt(now);order.setUpdatedAt(now);return repository.save(order);
    }
    public List<Order> list(String customerId){return customerId==null?repository.findAll():repository.findByCustomerIdOrderByCreatedAtDesc(customerId);}
    public Order get(String id){return repository.findById(id).orElseThrow(()->new OrderNotFoundException("Order not found with ID: "+id));}
    public Order updateStatus(String id,OrderStatus status){Order order=get(id);order.setStatus(status);order.setUpdatedAt(Instant.now());return repository.save(order);}
    public void delete(String id){repository.delete(get(id));}
    public static BigDecimal calculateTotal(BigDecimal devicePrice,BigDecimal monthlyPrice){return devicePrice.add(monthlyPrice);}
}
