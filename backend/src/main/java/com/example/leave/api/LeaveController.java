package com.example.leave.api;

import com.example.leave.domain.LeaveRequest;
import com.example.leave.service.LeaveService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/leaves")
public class LeaveController {

    private final LeaveService service;

    public LeaveController(LeaveService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LeaveRequest submit(@Valid @RequestBody SubmitLeaveRequest body) {
        return service.submit(body);
    }

    @GetMapping
    public List<LeaveRequest> mine(@RequestParam String employeeEmail) {
        return service.mine(employeeEmail);
    }

    @GetMapping("/pending")
    public List<LeaveRequest> pending() {
        return service.pending();
    }

    @PostMapping("/{id}/decision")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decide(@PathVariable Long id, @RequestBody DecisionRequest body) {
        service.decide(id, body);
    }

    @GetMapping("/balance")
    public Map<String, Integer> balance(@RequestParam String employeeEmail) {
        return Map.of("remainingDays", service.balance(employeeEmail).getRemainingDays());
    }
}
