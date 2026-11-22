package com.jobapplier.apply;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {
    List<JobApplication> findByStatus(ApplicationStatus status);

    List<JobApplication> findByStatusOrderByJobScoreDesc(ApplicationStatus status);

    long countByStatus(ApplicationStatus status);

    List<JobApplication> findByStatusAndAppliedAtBefore(ApplicationStatus status, Instant before);

    boolean existsByJobId(Long jobId);

    long countByStatusAndAppliedAtAfter(ApplicationStatus status, Instant after);
}
