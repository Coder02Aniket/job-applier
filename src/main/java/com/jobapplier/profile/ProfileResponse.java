package com.jobapplier.profile;

import java.util.List;

public record ProfileResponse(
    String name,
    String email,
    String phone,
    List<String> skills,
    List<String> titles,
    int yearsExperience
) {
    public static ProfileResponse from(CandidateProfile profile) {
        if (profile == null) return null;
        return new ProfileResponse(
            profile.name(),
            profile.email(),
            profile.phone(),
            profile.skills(),
            profile.titles(),
            profile.yearsExperience()
        );
    }
}
