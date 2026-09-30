package com.example.orderservice.service;
import com.example.orderservice.exception.OrderNotFoundException;
import com.example.orderservice.exception.OrderConflictException;
import com.example.orderservice.model.CreateOrderRequest;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderStatus;
import com.example.orderservice.repository.OrderRepository;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
@Service
public class OrderService {
    /** The happy path; an order only moves forward along it, possibly skipping steps the workflow did not observe. */
    private static final List<OrderStatus> FLOW=List.of(OrderStatus.PENDING_PAYMENT,OrderStatus.PAID,OrderStatus.DISPATCHED,OrderStatus.DELIVERED,OrderStatus.ACTIVATED,OrderStatus.COMPLETED);
    private final OrderRepository repository;
    public OrderService(OrderRepository repository){this.repository=repository;}
    public Order create(CreateOrderRequest request){
        Order order=new Order();
        order.setCustomerId(request.customerId());order.setProductId(request.productId());order.setProductName(request.productName());order.setStorage(request.storage());
        order.setPlanId(request.planId());order.setPlanName(request.planName());order.setDevicePrice(request.devicePrice());order.setMonthlyPrice(request.monthlyPrice());
        order.setTotal(calculateTotal(request.devicePrice(),request.monthlyPrice()));order.setStatus(OrderStatus.PENDING_PAYMENT);
        Instant now=Instant.now();order.setCreatedAt(now);order.setUpdatedAt(now);return repository.save(order);
    }
    public List<Order> list(String customerId){return customerId==null||customerId.isBlank()?repository.findAll():repository.findByCustomerIdOrderByCreatedAtDesc(customerId.trim());}
    public Order get(String id){return repository.findById(id).orElseThrow(()->new OrderNotFoundException("Order not found with ID: "+id));}
    public Order updateStatus(String id,OrderStatus status){Order order=get(id);if(order.getStatus()==status)return order;if(!isTransitionAllowed(order.getStatus(),status))throw new OrderConflictException("Cannot transition order from "+order.getStatus()+" to "+status);order.setStatus(status);order.setUpdatedAt(Instant.now());return repository.save(order);}
    public void delete(String id){Order order=get(id);if(order.getStatus()!=OrderStatus.PENDING_PAYMENT&&order.getStatus()!=OrderStatus.CANCELLED)throw new OrderConflictException("Only pending or cancelled orders can be deleted");repository.delete(order);}
    public static BigDecimal calculateTotal(BigDecimal devicePrice,BigDecimal monthlyPrice){if(devicePrice==null||monthlyPrice==null)throw new IllegalArgumentException("Prices are required");return devicePrice.add(monthlyPrice);}
    static boolean isTransitionAllowed(OrderStatus current,OrderStatus next){
        if(current==OrderStatus.COMPLETED||current==OrderStatus.CANCELLED)return false;
        if(current==OrderStatus.FAILED)return next==OrderStatus.CANCELLED;
        if(next==OrderStatus.CANCELLED||next==OrderStatus.FAILED)return true;
        // Nothing moves on until payment is confirmed; after that, steps the workflow did not observe may be skipped.
        if(current==OrderStatus.PENDING_PAYMENT)return next==OrderStatus.PAID;
        return FLOW.indexOf(next)>FLOW.indexOf(current);
    }
}
