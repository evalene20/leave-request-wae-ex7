package com.example.leave.worker;

import com.example.leave.service.LeaveService;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class LeaveWorkers {

    private final LeaveService service;

    public LeaveWorkers(LeaveService service) {
        this.service = service;
    }

    @JobWorker(type = "validate-leave")
    public Map<String, Object> validate(@Variable Long requestId) {
        return service.validate(requestId);
    }

    @JobWorker(type = "finalize-leave")
    public void finalizeLeave(ActivatedJob job) {
        Map<String, Object> variables = job.getVariablesAsMap();
        Long id = ((Number) variables.get("requestId")).longValue();
        boolean approved = "APPROVED".equals(variables.get("outcome"));
        // managerComment does not exist on the invalid-request path.
        service.finalizeRequest(
                id,
                approved,
                asString(variables.get("managerComment"), ""),
                asString(variables.get("validationMessage"), "OK"));
    }

    private static String asString(Object value, String fallback) {
        return value == null ? fallback : value.toString();
    }
}
