package com.jobapplier.apply;

import com.jobapplier.config.AppProperties;
import com.jobapplier.job.JobSource;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ApplyService {
    private final JobApplicationRepository repo;
    private final Map<JobSource, Applicator> applicators;
    private final AppProperties props;

    public ApplyService(JobApplicationRepository repo, List<Applicator> list, AppProperties props) {
        this.repo = repo;
        this.props = props;
        this.applicators = list.stream().collect(Collectors.toMap(Applicator::source, Function.identity()));
    }

    /** Only runs for applications the user approved. */
    public void applyApproved() {
        long today = repo.countByStatusAndAppliedAtAfter(ApplicationStatus.APPLIED, Instant.now().minus(Duration.ofDays(1)));
        for (JobApplication a : repo.findByStatus(ApplicationStatus.APPROVED)) {
            if (today >= props.limits().maxAppliesPerDay()) break;
            Applicator ap = applicators.get(a.getJob().getSource());
            if (ap == null) { a.setStatus(ApplicationStatus.MANUAL_REQUIRED); repo.save(a); continue; }
            try {
                boolean ok = ap.apply(a);
                a.setStatus(ok ? ApplicationStatus.APPLIED : ApplicationStatus.MANUAL_REQUIRED);
                if (ok) { a.setAppliedAt(Instant.now()); today++; }
            } catch (Exception e) {
                a.setStatus(ApplicationStatus.FAILED);
                a.setNotes(e.getMessage());
            }
            repo.save(a);
        }
    }
}
