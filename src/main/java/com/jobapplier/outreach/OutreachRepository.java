package com.jobapplier.outreach;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutreachRepository extends JpaRepository<Outreach, Long> {
    List<Outreach> findByStatus(OutreachStatus status);

    List<Outreach> findByStatusIn(List<OutreachStatus> statuses);

    long countByStatusAndSentAtAfter(OutreachStatus status, Instant after);
}
