package com.jobapplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.jobapplier.apply.*;
import com.jobapplier.config.AppProperties;
import com.jobapplier.job.Job;
import com.jobapplier.job.JobSource;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApplyServiceTest {

    @Mock
    private JobApplicationRepository repo;
    @Mock
    private Applicator linkedInApplicator;

    private AppProperties props;
    private ApplyService service;

    @BeforeEach
    void setUp() {
        AppProperties.Limits limits = new AppProperties.Limits(10, 10, 2, 6); // max 2 applies per day
        props = new AppProperties(null, null, null, limits, null, null, null);

        when(linkedInApplicator.source()).thenReturn(JobSource.LINKEDIN);
        service = new ApplyService(repo, List.of(linkedInApplicator), props);
    }

    @Test
    void processesOnlyApprovedApplications() {
        Job job = new Job();
        job.setSource(JobSource.LINKEDIN);

        JobApplication approvedApp = new JobApplication();
        approvedApp.setId(10L);
        approvedApp.setJob(job);
        approvedApp.setStatus(ApplicationStatus.APPROVED);

        when(repo.countByStatusAndAppliedAtAfter(eq(ApplicationStatus.APPLIED), any(Instant.class))).thenReturn(0L);
        when(repo.findByStatus(ApplicationStatus.APPROVED)).thenReturn(List.of(approvedApp));
        when(linkedInApplicator.apply(approvedApp)).thenReturn(true);

        service.applyApproved();

        assertThat(approvedApp.getStatus()).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(approvedApp.getAppliedAt()).isNotNull();
        verify(repo).save(approvedApp);
    }

    @Test
    void setsManualRequiredIfApplicatorReturnsFalse() {
        Job job = new Job();
        job.setSource(JobSource.LINKEDIN);

        JobApplication approvedApp = new JobApplication();
        approvedApp.setId(11L);
        approvedApp.setJob(job);
        approvedApp.setStatus(ApplicationStatus.APPROVED);

        when(repo.countByStatusAndAppliedAtAfter(eq(ApplicationStatus.APPLIED), any(Instant.class))).thenReturn(0L);
        when(repo.findByStatus(ApplicationStatus.APPROVED)).thenReturn(List.of(approvedApp));
        when(linkedInApplicator.apply(approvedApp)).thenReturn(false);

        service.applyApproved();

        assertThat(approvedApp.getStatus()).isEqualTo(ApplicationStatus.MANUAL_REQUIRED);
        verify(repo).save(approvedApp);
    }

    @Test
    void respectsDailyApplyCap() {
        Job job = new Job();
        job.setSource(JobSource.LINKEDIN);

        JobApplication approvedApp1 = new JobApplication();
        approvedApp1.setJob(job);
        approvedApp1.setStatus(ApplicationStatus.APPROVED);

        // Daily count is 2 (equals maxAppliesPerDay = 2)
        when(repo.countByStatusAndAppliedAtAfter(eq(ApplicationStatus.APPLIED), any(Instant.class))).thenReturn(2L);
        when(repo.findByStatus(ApplicationStatus.APPROVED)).thenReturn(List.of(approvedApp1));

        service.applyApproved();

        // Applicator should not be called
        verify(linkedInApplicator, never()).apply(any());
    }
}
