package com.jobapplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jobapplier.job.JobNormalizer;
import org.junit.jupiter.api.Test;

class JobNormalizerTest {
    @Test
    void sameJobDifferentFormattingHasSameHash() {
        assertEquals(JobNormalizer.dedupeHash("Acme Inc.", "Java Developer", "Pune"),
                     JobNormalizer.dedupeHash("acme inc", "Java  Developer!", "pune"));
    }
}
