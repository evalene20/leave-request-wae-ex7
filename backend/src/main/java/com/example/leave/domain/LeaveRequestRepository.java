package com.example.leave.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    List<LeaveRequest> findByEmployeeEmailOrderByCreatedAtDesc(String employeeEmail);

    List<LeaveRequest> findByStatusOrderByCreatedAtAsc(LeaveStatus status);

    // Targeted updates: the request controller thread and the job workers both
    // touch the same row, so each only writes the columns it owns. This prevents
    // a late full-entity save from overwriting a status set by a worker.
    @Modifying
    @Transactional
    @Query("update LeaveRequest r set r.processInstanceKey = :key where r.id = :id")
    int updateProcessInstanceKey(@Param("id") Long id, @Param("key") Long key);

    @Modifying
    @Transactional
    @Query("update LeaveRequest r set r.status = :status, r.statusMessage = :message where r.id = :id")
    int updateStatus(@Param("id") Long id,
                     @Param("status") LeaveStatus status,
                     @Param("message") String message);
}
