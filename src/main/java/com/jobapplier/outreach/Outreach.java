package com.jobapplier.outreach;

import com.jobapplier.job.Job;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Outreach {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private Job job;
    private String recipient;
    private String subject;
    @Column(columnDefinition = "text")
    private String body;
    @Enumerated(EnumType.STRING)
    private OutreachStatus status = OutreachStatus.DRAFT;
    private Instant sentAt;
    private boolean replied;
    private Instant createdAt = Instant.now();
}
