package com.example.paymentservice.config;
import com.example.paymentservice.model.Payment;
import org.bson.Document;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;
/**
 * An order now has one checkout payment plus one payment per monthly invoice, so orderId is no longer unique.
 * Drops the unique orderId index older versions created, then keeps a plain lookup index.
 */
@Component
public class PaymentIndexes implements SmartInitializingSingleton{
    private final MongoTemplate mongo;
    public PaymentIndexes(MongoTemplate mongo){this.mongo=mongo;}
    public void afterSingletonsInstantiated(){
        var indexes=mongo.indexOps(Payment.class);
        indexes.getIndexInfo().stream().filter(info->info.isUnique()&&info.getIndexFields().size()==1&&"orderId".equals(info.getIndexFields().get(0).getKey())).forEach(info->indexes.dropIndex(info.getName()));
        indexes.createIndex(new Index().on("orderId",Sort.Direction.ASC).named("orderId_lookup"));
    }
}
