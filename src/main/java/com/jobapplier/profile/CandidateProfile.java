package com.jobapplier.profile;

import java.util.List;

public record CandidateProfile(
    String name,
    String email,
    String phone,
    List<String> skills,
    List<String> titles,
    int yearsExperience,
    String rawText
) {
    public CandidateProfile(String name, String email, List<String> skills, List<String> titles,
                            int yearsExperience, String rawText) {
        this(name, email, null, skills, titles, yearsExperience, rawText);
    }
}
