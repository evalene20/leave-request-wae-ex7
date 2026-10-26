package com.example.leave.service;

import com.example.leave.api.DecisionRequest;
import com.example.leave.api.SubmitLeaveRequest;
import com.example.leave.domain.*;
import io.camunda.client.CamundaClient;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LeaveService {

    static final String PROCESS_ID = "leave-request";
    static final int DEFAULT_ENTITLEMENT = 20;
    static final long MAX_RANGE_DAYS = 366;

    private static final Logger log = LoggerFactory.getLogger(LeaveService.class);

    private final LeaveRequestRepository requests;
    private final LeaveBalanceRepository balances;
    private final CamundaClient camunda;

    public LeaveService(LeaveRequestRepository requests,
                        LeaveBalanceRepository balances,
                        CamundaClient camunda) {
        this.requests = requests;
        this.balances = balances;
        this.camunda = camunda;
    }

    // ------------------------------------------------------------------ API

    /**
     * Saves the request, then starts the BPMN process. Deliberately NOT
     * transactional: the row must be committed before the validate-leave worker
     * (running on another thread) can look it up.
     */
    public LeaveRequest submit(SubmitLeaveRequest body) {
        String email = normalize(body.employeeEmail());

        LeaveRequest request = new LeaveRequest();
        request.setEmployeeName(body.employeeName().trim());
        request.setEmployeeEmail(email);
        request.setLeaveType(body.leaveType().trim().toUpperCase(Locale.ROOT));
        request.setStartDate(body.startDate());
        request.setEndDate(body.endDate());
        request.setDays(WorkingDays.between(body.startDate(), body.endDate()));
        request.setReason(body.reason());
        request.setStatus(LeaveStatus.SUBMITTED);
        LeaveRequest saved = requests.save(request);

        balance(email); // make sure a balance row exists

        Map<String, Object> variables = new HashMap<>();
        variables.put("requestId", saved.getId());
        variables.put("employeeEmail", email);
        variables.put("leaveType", saved.getLeaveType());
        variables.put("days", saved.getDays());
        variables.put("startDate", saved.getStartDate().toString());
        variables.put("endDate", saved.getEndDate().toString());

        try {
            var instance = camunda.newCreateInstanceCommand()
                    .bpmnProcessId(PROCESS_ID)
                    .latestVersion()
                    .variables(variables)
                    .send()
                    .join();
            requests.updateProcessInstanceKey(saved.getId(), instance.getProcessInstanceKey());
            log.info("Started process instance {} for leave request {}",
                    instance.getProcessInstanceKey(), saved.getId());
        } catch (RuntimeException e) {
            log.error("Could not start process for leave request {}", saved.getId(), e);
            requests.deleteById(saved.getId());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The workflow engine is not reachable. Please try again later.");
        }
        return requests.findById(saved.getId()).orElse(saved);
    }

    public List<LeaveRequest> mine(String email) {
        return requests.findByEmployeeEmailOrderByCreatedAtDesc(normalize(email));
    }

    public List<LeaveRequest> pending() {
        return requests.findByStatusOrderByCreatedAtAsc(LeaveStatus.PENDING_APPROVAL);
    }

    public LeaveBalance balance(String email) {
        String key = normalize(email);
        return balances.findById(key)
                .orElseGet(() -> balances.save(new LeaveBalance(key, DEFAULT_ENTITLEMENT)));
    }

    /** Completes the Camunda "Manager approval" user task with the manager's decision. */
    public void decide(Long id, DecisionRequest decision) {
        LeaveRequest request = requests.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Leave request " + id + " not found"));

        if (request.getStatus() != LeaveStatus.PENDING_APPROVAL || request.getProcessInstanceKey() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Leave request " + id + " is not awaiting approval");
        }

        long processInstanceKey = request.getProcessInstanceKey();
        var tasks = camunda.newUserTaskSearchRequest()
                .filter(f -> f.processInstanceKey(processInstanceKey))
                .send()
                .join()
                .items();

        // The task search index is eventually consistent; 409 tells the UI to retry shortly.
        var task = tasks.stream()
                .filter(t -> "CREATED".equals(String.valueOf(t.getState())))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "The approval task is not ready yet, please retry"));

        Map<String, Object> variables = Map.of(
                "approved", decision.approved(),
                "managerComment", decision.comment() == null ? "" : decision.comment());

        camunda.newCompleteUserTaskCommand(task.getUserTaskKey())
                .variables(variables)
                .send()
                .join();
        log.info("Manager decision for request {}: approved={}", id, decision.approved());
    }

    // ------------------------------------------------------- worker callbacks

    /** Used by the validate-leave job worker. */
    public Map<String, Object> validate(Long requestId) {
        LeaveRequest request = requests.findById(requestId).orElseThrow(() ->
                new IllegalStateException("Leave request " + requestId + " not found"));

        String problem = null;
        if (request.getEndDate().isBefore(request.getStartDate())) {
            problem = "The end date is before the start date.";
        } else if (ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate()) > MAX_RANGE_DAYS) {
            problem = "A single request cannot span more than a year.";
        } else if (request.getDays() <= 0) {
            problem = "The selected period contains no working days.";
        } else if ("ANNUAL".equals(request.getLeaveType())) {
            int remaining = balance(request.getEmployeeEmail()).getRemainingDays();
            if (remaining < request.getDays()) {
                problem = "Insufficient annual leave balance: " + request.getDays()
                        + " day(s) requested, " + remaining + " remaining.";
            }
        }

        boolean valid = problem == null;
        if (valid) {
            requests.updateStatus(requestId, LeaveStatus.PENDING_APPROVAL, "Awaiting manager approval");
        }
        return Map.of("valid", valid, "validationMessage", valid ? "OK" : problem);
    }

    /** Used by the finalize-leave job worker. Idempotent: a retried job never double-deducts. */
    @Transactional
    public void finalizeRequest(Long requestId, boolean approved,
                                String managerComment, String validationMessage) {
        LeaveRequest request = requests.findById(requestId).orElseThrow(() ->
                new IllegalStateException("Leave request " + requestId + " not found"));

        if (request.getStatus() == LeaveStatus.APPROVED || request.getStatus() == LeaveStatus.REJECTED) {
            log.info("Request {} already finalised as {}; ignoring duplicate job", requestId, request.getStatus());
            return;
        }

        String comment = managerComment == null ? "" : managerComment.trim();

        if (approved) {
            boolean annual = "ANNUAL".equals(request.getLeaveType());
            if (annual) {
                LeaveBalance balance = balance(request.getEmployeeEmail());
                if (balance.getRemainingDays() < request.getDays()) {
                    requests.updateStatus(requestId, LeaveStatus.REJECTED,
                            "Insufficient annual leave balance at approval time.");
                    notifyEmployee(request, LeaveStatus.REJECTED);
                    return;
                }
                balance.setRemainingDays(balance.getRemainingDays() - request.getDays());
                balances.save(balance);
            }
            requests.updateStatus(requestId, LeaveStatus.APPROVED,
                    comment.isEmpty() ? "Approved" : "Approved: " + comment);
            notifyEmployee(request, LeaveStatus.APPROVED);
        } else {
            String message;
            if (!comment.isEmpty()) {
                message = "Rejected by manager: " + comment;
            } else if (validationMessage != null && !validationMessage.isBlank() && !"OK".equals(validationMessage)) {
                message = validationMessage;
            } else {
                message = "Rejected by manager";
            }
            requests.updateStatus(requestId, LeaveStatus.REJECTED, message);
            notifyEmployee(request, LeaveStatus.REJECTED);
        }
    }

    /** Notification hook: replace the log line with e-mail / chat integration. */
    private void notifyEmployee(LeaveRequest request, LeaveStatus status) {
        log.info("NOTIFY {}: leave request #{} is now {}", request.getEmployeeEmail(), request.getId(), status);
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
