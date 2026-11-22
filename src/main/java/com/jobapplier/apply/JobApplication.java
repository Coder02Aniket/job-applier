package com.jobapplier.apply;

import com.jobapplier.job.Job;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class JobApplication {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private Job job;
    @Enumerated(EnumType.STRING)
    private ApplicationStatus status = ApplicationStatus.PENDING_REVIEW;
    private String resumePath;
    private Instant appliedAt;
    @Column(columnDefinition = "text")
    private String notes;
    private Instant createdAt = Instant.now();
}
