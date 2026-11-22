package com.jobapplier.job;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRepository extends JpaRepository<Job, Long> {
    Optional<Job> findByDedupeHash(String hash);

    List<Job> findByPostedAtAfterAndScoreGreaterThanOrderByScoreDesc(Instant after, double minScore, Pageable page);
}
