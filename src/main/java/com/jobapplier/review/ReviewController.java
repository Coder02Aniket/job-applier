package com.jobapplier.review;

import com.jobapplier.apply.ApplicationStatus;
import com.jobapplier.apply.JobApplication;
import com.jobapplier.apply.JobApplicationRepository;
import com.jobapplier.outreach.Outreach;
import com.jobapplier.outreach.OutreachRepository;
import com.jobapplier.outreach.OutreachService;
import com.jobapplier.outreach.OutreachStatus;
import com.jobapplier.pipeline.PipelineService;
import com.jobapplier.profile.ProfileResponse;
import com.jobapplier.profile.ResumeParserService;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReviewController {
    private final JobApplicationRepository applications;
    private final OutreachRepository outreachRepo;
    private final OutreachService outreachService;
    private final PipelineService pipeline;
    private final ResumeParserService resumeParser;

    @GetMapping("/profile")
    public ProfileResponse profile() {
        return ProfileResponse.from(resumeParser.getProfile());
    }

    @GetMapping("/applications")
    public List<JobApplication> list(@RequestParam(defaultValue = "PENDING_REVIEW") ApplicationStatus status) {
        return applications.findByStatusOrderByJobScoreDesc(status);
    }

    @GetMapping("/summary")
    public Map<String, Long> summary() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (ApplicationStatus status : ApplicationStatus.values()) {
            counts.put(status.name(), applications.countByStatus(status));
        }
        return counts;
    }

    @PostMapping("/applications/{id}/approve")
    public void approve(@PathVariable Long id) {
        JobApplication a = applications.findById(id).orElseThrow();
        a.setStatus(ApplicationStatus.APPROVED);
        applications.save(a);
    }

    @PostMapping("/applications/{id}/skip")
    public void skip(@PathVariable Long id) {
        JobApplication a = applications.findById(id).orElseThrow();
        a.setStatus(ApplicationStatus.SKIPPED);
        applications.save(a);
    }

    @GetMapping("/outreach")
    public List<Outreach> drafts() {
        return outreachRepo.findByStatusIn(List.of(OutreachStatus.DRAFT, OutreachStatus.APPROVED));
    }

    @PutMapping("/outreach/{id}")
    public Outreach edit(@PathVariable Long id, @RequestBody Outreach edited) {
        Outreach o = outreachRepo.findById(id).orElseThrow();
        o.setSubject(edited.getSubject());
        o.setBody(edited.getBody());
        return outreachRepo.save(o);
    }

    /** Explicit human approval of an outreach draft. Nothing is sent here. */
    @PostMapping("/outreach/{id}/approve")
    public Outreach approveDraft(@PathVariable Long id) {
        Outreach o = outreachRepo.findById(id).orElseThrow();
        if (o.getStatus() != OutreachStatus.SENT) {
            o.setStatus(OutreachStatus.APPROVED);
            outreachRepo.save(o);
        }
        return o;
    }

    @PostMapping("/outreach/{id}/send")
    public void send(@PathVariable Long id, @RequestParam String resumePath) throws Exception {
        outreachService.sendApproved(id, Path.of(resumePath));
    }

    @PostMapping("/applications/{id}/status")
    public JobApplication updateStatus(@PathVariable Long id, @RequestParam ApplicationStatus status) {
        JobApplication a = applications.findById(id).orElseThrow();
        a.setStatus(status);
        return applications.save(a);
    }

    @PostMapping("/pipeline/run")
    public void runNow() { pipeline.run(); }
}
