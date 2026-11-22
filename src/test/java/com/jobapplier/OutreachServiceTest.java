package com.jobapplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.jobapplier.config.AppProperties;
import com.jobapplier.job.Job;
import com.jobapplier.llm.LlmClient;
import com.jobapplier.outreach.Outreach;
import com.jobapplier.outreach.OutreachRepository;
import com.jobapplier.outreach.OutreachService;
import com.jobapplier.outreach.OutreachStatus;
import com.jobapplier.profile.CandidateProfile;
import jakarta.mail.internet.MimeMessage;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class OutreachServiceTest {

    @Mock
    private OutreachRepository repo;
    @Mock
    private LlmClient llm;
    @Mock
    private JavaMailSender mailSender;
    @Mock
    private MimeMessage mimeMessage;

    private AppProperties props;
    private OutreachService service;
    private CandidateProfile profile;

    @BeforeEach
    void setUp() {
        AppProperties.Limits limits = new AppProperties.Limits(10, 2, 10, 6);
        props = new AppProperties(null, null, null, limits, null, null, null);
        service = new OutreachService(repo, llm, mailSender, props);

        profile = new CandidateProfile(
            "Jane Doe",
            "jane@example.com",
            "+15550199",
            List.of("java", "spring boot"),
            List.of("Senior Software Engineer"),
            5,
            "Jane Doe\nExperienced Java developer."
        );
    }

    @Test
    void draftReturnsNullIfNoRecruiterEmail() {
        Job job = new Job();
        job.setTitle("Java Developer");
        job.setCompany("Acme Corp");
        job.setRecruiterEmail(null); // no recruiter email

        Outreach draft = service.draft(job, profile);

        assertThat(draft).isNull();
        verifyNoInteractions(llm);
        verify(repo, never()).save(any());
    }

    @Test
    void draftCreatesOutreachInDraftStatus() {
        Job job = new Job();
        job.setTitle("Java Developer");
        job.setCompany("Acme Corp");
        job.setRecruiterEmail("recruiter@acme.example.com");

        when(llm.complete(anyString(), anyString())).thenReturn("Polite email draft text.");
        when(repo.save(any(Outreach.class))).thenAnswer(i -> i.getArgument(0));

        Outreach draft = service.draft(job, profile);

        assertThat(draft).isNotNull();
        assertThat(draft.getRecipient()).isEqualTo("recruiter@acme.example.com");
        assertThat(draft.getStatus()).isEqualTo(OutreachStatus.DRAFT);
        assertThat(draft.getBody()).isEqualTo("Polite email draft text.");
    }

    @Test
    void sendApprovedFailsForDraftStatus() {
        Outreach o = new Outreach();
        o.setId(1L);
        o.setStatus(OutreachStatus.DRAFT); // not APPROVED

        when(repo.findById(1L)).thenReturn(Optional.of(o));

        assertThatThrownBy(() -> service.sendApproved(1L, Path.of("non-existent.docx")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not in APPROVED status");
    }

    @Test
    void sendApprovedSucceedsForApprovedStatus() throws Exception {
        Job job = new Job();
        job.setTitle("Java Developer");

        Outreach o = new Outreach();
        o.setId(1L);
        o.setJob(job);
        o.setRecipient("recruiter@acme.example.com");
        o.setSubject("Application: Java Developer");
        o.setBody("Email body");
        o.setStatus(OutreachStatus.APPROVED);

        when(repo.findById(1L)).thenReturn(Optional.of(o));
        when(repo.countByStatusAndSentAtAfter(eq(OutreachStatus.SENT), any(Instant.class))).thenReturn(0L);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        service.sendApproved(1L, Path.of("src/test/resources/fixtures/resume.txt"));

        verify(mailSender).send(mimeMessage);
        assertThat(o.getStatus()).isEqualTo(OutreachStatus.SENT);
        assertThat(o.getSentAt()).isNotNull();
    }

    @Test
    void sendApprovedFailsWhenDailyCapReached() {
        Outreach o = new Outreach();
        o.setId(1L);
        o.setStatus(OutreachStatus.APPROVED);

        when(repo.findById(1L)).thenReturn(Optional.of(o));
        when(repo.countByStatusAndSentAtAfter(eq(OutreachStatus.SENT), any(Instant.class))).thenReturn(2L); // cap is 2

        assertThatThrownBy(() -> service.sendApproved(1L, Path.of("src/test/resources/fixtures/resume.txt")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Daily mail limit reached");
    }
}
