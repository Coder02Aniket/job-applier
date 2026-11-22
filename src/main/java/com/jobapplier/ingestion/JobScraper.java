package com.jobapplier.ingestion;

import com.jobapplier.job.Job;
import com.jobapplier.job.JobSource;
import com.jobapplier.profile.CandidateProfile;
import java.util.List;

public interface JobScraper {
    JobSource source();

    /** Return recent jobs matching the profile/prefs. Must be low-volume and throttled. */
    List<Job> fetch(CandidateProfile profile);
}
