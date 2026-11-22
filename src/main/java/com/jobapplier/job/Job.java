package com.jobapplier.job;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Job {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    private JobSource source;
    private String externalId;
    private String title;
    private String company;
    private String location;
    private Instant postedAt;
    @Column(columnDefinition = "text")
    private String url;
    @Column(columnDefinition = "text")
    private String description;
    private String recruiterName;
    private String recruiterEmail;
    @Column(unique = true)
    private String dedupeHash;
    private double score;
    private Instant createdAt = Instant.now();
}
