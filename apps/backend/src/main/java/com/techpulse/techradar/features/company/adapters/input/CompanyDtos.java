package com.techpulse.techradar.features.company.adapters.input;

import com.techpulse.techradar.features.company.domain.CompanyMention;
import com.techpulse.techradar.features.company.domain.CompanyProfile;
import com.techpulse.techradar.features.company.domain.SimilarCompany;
import lombok.Builder;
import lombok.Value;

import java.util.List;

public class CompanyDtos {

    @Value
    @Builder
    public static class CompanyProfileResponse {
        String id;
        String name;
        String location;
        List<String> techStack;
        int jobCount;
        String industry;
        String size;

        public static CompanyProfileResponse from(CompanyProfile p) {
            return CompanyProfileResponse.builder()
                    .id(p.id())
                    .name(p.name())
                    .location(p.location())
                    .techStack(p.techStack())
                    .jobCount(p.jobCount())
                    .industry(p.industry())
                    .size(p.size())
                    .build();
        }
    }

    @Value
    @Builder
    public static class CompanyMentionResponse {
        String id;
        String title;
        String url;
        String publishDate;
        String sourcePlatform;

        public static CompanyMentionResponse from(CompanyMention m) {
            return CompanyMentionResponse.builder()
                    .id(m.id())
                    .title(m.title())
                    .url(m.url())
                    .publishDate(m.publishDate())
                    .sourcePlatform(m.sourcePlatform())
                    .build();
        }
    }

    @Value
    @Builder
    public static class SimilarCompanyResponse {
        String id;
        String name;
        String location;
        List<String> sharedTechs;
        double score;

        public static SimilarCompanyResponse from(SimilarCompany s) {
            return SimilarCompanyResponse.builder()
                    .id(s.id())
                    .name(s.name())
                    .location(s.location())
                    .sharedTechs(s.sharedTechs())
                    .score(s.score())
                    .build();
        }
    }
}
