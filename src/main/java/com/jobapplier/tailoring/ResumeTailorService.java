package com.jobapplier.tailoring;

import com.jobapplier.config.AppProperties;
import com.jobapplier.job.Job;
import com.jobapplier.llm.LlmClient;
import com.jobapplier.profile.CandidateProfile;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeTailorService {

    public static final String SYSTEM = """
        You tailor resumes. Rewrite the resume to emphasise what matches the job description.
        HARD RULES: use ONLY facts present in the resume text. Never invent skills, employers,
        dates, metrics or certifications. You may reorder, rephrase and adjust the summary/skills order.
        Return plain text, section by section.""";

    private final LlmClient llm;
    private final AppProperties props;
    private final ResumeFactualValidator validator;

    /** Returns path to the tailored DOCX. */
    public Path tailor(CandidateProfile profile, Job job) {
        String user = "RESUME:\n" + profile.rawText() + "\n\nJOB: " + job.getTitle() + " @ " + job.getCompany()
            + "\n" + job.getDescription();

        String tailored;
        try {
            tailored = llm.complete(SYSTEM, user);
        } catch (Exception e) {
            log.warn("LLM tailoring failed for job id={}: {}. Using original resume.", job.getId(), e.getMessage());
            tailored = profile.rawText();
        }

        // Hard Rule 2 Verification: Anti-Hallucination Guardrail
        if (!validator.validate(tailored, profile)) {
            log.warn("Fact validation failed for job id={}. Rejecting tailored output and falling back to original resume.", job.getId());
            tailored = profile.rawText();
        }

        Path out = Path.of(props.tailoredDir(), "resume_" + (job.getId() != null ? job.getId() : "preview") + ".docx");
        try {
            if (out.getParent() != null) {
                Files.createDirectories(out.getParent());
            }
            try (XWPFDocument doc = new XWPFDocument(); OutputStream os = Files.newOutputStream(out)) {
                for (String line : tailored.split("\n")) {
                    XWPFParagraph p = doc.createParagraph();
                    p.createRun().setText(line);
                }
                doc.write(os);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed writing tailored resume", e);
        }
        return out;
    }
}
