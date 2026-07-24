package com.techpulse.techradar.features.kgreview.adapters.input;

import com.techpulse.techradar.features.kgreview.domain.CompanyDuplicateGroup;
import com.techpulse.techradar.features.kgreview.domain.TechAliasReviewItem;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response shapes for {@link KgReviewAdminController}, kept out of the controller itself —
 * matching the convention used by {@code AdminSocialDtos} for the moderation queue.
 */
public class KgReviewDtos {

    private KgReviewDtos() {
    }

    @Value
    @Builder
    public static class TechAliasReviewView {
        long id;
        String nameA;
        String nameB;
        String llmReasoning;
        String status;
        LocalDateTime createdAt;

        public static TechAliasReviewView from(TechAliasReviewItem item) {
            return TechAliasReviewView.builder()
                    .id(item.id())
                    .nameA(item.nameA())
                    .nameB(item.nameB())
                    .llmReasoning(item.llmReasoning())
                    .status(item.status())
                    .createdAt(item.createdAt())
                    .build();
        }
    }

    @Value
    @Builder
    public static class CompanyDuplicateCandidateView {
        String id;
        String name;

        public static CompanyDuplicateCandidateView from(CompanyDuplicateGroup.Candidate c) {
            return CompanyDuplicateCandidateView.builder().id(c.id()).name(c.name()).build();
        }
    }

    @Value
    @Builder
    public static class CompanyDuplicateGroupView {
        String normalizedCore;
        List<CompanyDuplicateCandidateView> companies;

        public static CompanyDuplicateGroupView from(CompanyDuplicateGroup group) {
            return CompanyDuplicateGroupView.builder()
                    .normalizedCore(group.normalizedCore())
                    .companies(group.companies().stream().map(CompanyDuplicateCandidateView::from).toList())
                    .build();
        }
    }
}
