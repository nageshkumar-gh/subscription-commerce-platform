package com.example.orchestration.config;

import com.example.orchestration.workflow.*;
import io.temporal.client.*;
import io.temporal.serviceclient.*;
import io.temporal.worker.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;

@Configuration
public class TemporalConfig {
    @Bean(destroyMethod = "shutdown")
    WorkflowServiceStubs stubs(@Value("${temporal.target}") String target) {
        return WorkflowServiceStubs.newServiceStubs(WorkflowServiceStubsOptions.newBuilder().setTarget(target).build());
    }

    @Bean
    WorkflowClient client(WorkflowServiceStubs s) {
        return WorkflowClient.newInstance(s);
    }

    @Bean(destroyMethod = "shutdown")
    WorkerFactory workers(WorkflowClient c, OrderActivitiesImpl a) {
        WorkerFactory f = WorkerFactory.newInstance(c);
        Worker w = f.newWorker("order-lifecycle");
        w.registerWorkflowImplementationTypes(OrderLifecycleWorkflowImpl.class);
        w.registerActivitiesImplementations(a);
        f.start();
        return f;
    }
}
