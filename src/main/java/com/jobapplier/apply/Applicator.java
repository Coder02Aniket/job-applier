package com.jobapplier.apply;

import com.jobapplier.job.JobSource;

public interface Applicator {
    JobSource source();

    /** @return true if applied; false if it needs manual handling (custom questions, external ATS, CAPTCHA). */
    boolean apply(JobApplication application);
}
