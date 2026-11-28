package com.outpick.domain.place.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** A closed season is a restriction, never a statement that harvesting is otherwise permitted. */
public record FisheryProtectionResponse(long placeId, LocalDate visitDate, String harvestPermission,
        String advisory, List<Rule> confirmedClosed, List<Rule> confirmedLimits, List<Rule> reviewCandidates) {
    public record Rule(long ruleId, String resourceName, String category, String ruleKind,
            String period, String threshold, String region, boolean dateMatches,
            String sourceExcerpt, String legalBasis, String noticeNumber, String sourceDocument,
            String officialUrl, String verificationStatus, LocalDateTime lastVerifiedAt) {}
}
