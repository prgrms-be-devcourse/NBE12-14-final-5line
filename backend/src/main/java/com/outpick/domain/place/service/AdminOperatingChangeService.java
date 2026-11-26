package com.outpick.domain.place.service;

import com.outpick.domain.member.entity.MemberRole;
import com.outpick.domain.member.service.MemberService;
import com.outpick.domain.place.dto.AdminOperatingChangePageResponse;
import com.outpick.domain.place.dto.AdminOperatingChangeResolutionRequest;
import com.outpick.domain.place.dto.AdminOperatingChangeResolutionRequest.Decision;
import com.outpick.domain.place.entity.PlacePublishStatus;
import com.outpick.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

import static com.outpick.domain.place.exception.PlaceErrorCode.*;

@Service
@RequiredArgsConstructor
public class AdminOperatingChangeService {
    private static final String PENDING_PUBLIC_CANDIDATES = """
            FROM place_operating_change_candidate c
            JOIN place p ON p.place_id = c.place_id
            WHERE c.resolved_at IS NULL AND p.publish_status = ?
            """;

    private final MemberService members;
    private final JdbcTemplate jdbc;
    private final Clock placeClock;

    @Transactional(readOnly = true)
    public AdminOperatingChangePageResponse pending(long memberId, int page, int size) {
        requireAdmin(memberId);
        byte published = PlacePublishStatus.PUBLISHED.getCode();
        long total = jdbc.queryForObject("SELECT COUNT(DISTINCT c.place_id) " + PENDING_PUBLIC_CANDIDATES,
                Long.class, published);
        var groups = new LinkedHashMap<ReviewPlace, List<AdminOperatingChangePageResponse.Candidate>>();
        jdbc.query("""
                WITH page_places AS (
                    SELECT c.place_id, MAX(c.detected_at) AS latest_detected_at
                """ + PENDING_PUBLIC_CANDIDATES + """
                    GROUP BY c.place_id
                    ORDER BY latest_detected_at DESC, c.place_id DESC
                    LIMIT ? OFFSET ?
                )
                SELECT c.candidate_id, c.place_id, p.name, c.field_name, c.previous_value,
                       c.new_value, c.previous_source_url, c.new_source_url, c.detected_at
                FROM page_places selected
                JOIN place p ON p.place_id = selected.place_id
                JOIN place_operating_change_candidate c ON c.place_id = selected.place_id
                WHERE c.resolved_at IS NULL
                ORDER BY selected.latest_detected_at DESC, selected.place_id DESC, c.detected_at DESC, c.candidate_id DESC
                """, row -> {
            var place = new ReviewPlace(row.getLong("place_id"), row.getString("name"));
            var candidate = new AdminOperatingChangePageResponse.Candidate(
                    row.getLong("candidate_id"), row.getString("field_name"), row.getString("previous_value"),
                    row.getString("new_value"), row.getString("previous_source_url"), row.getString("new_source_url"),
                    row.getObject("detected_at", LocalDateTime.class));
            groups.computeIfAbsent(place, key -> new ArrayList<>()).add(candidate);
        }, published, size, (long) page * size);
        var items = groups.entrySet().stream()
                .map(group -> new AdminOperatingChangePageResponse.Item(
                        group.getKey().id(), group.getKey().name(), group.getValue()))
                .toList();
        return new AdminOperatingChangePageResponse(items, page, size, total);
    }

    private record ReviewPlace(long id, String name) {}

    @Transactional
    public void resolve(long memberId, long candidateId, AdminOperatingChangeResolutionRequest request) {
        requireAdmin(memberId);
        String reason = request.reason() == null || request.reason().isBlank() ? null : request.reason().strip();
        if (request.decision() == Decision.REJECTED && reason == null) {
            throw new BusinessException(OPERATING_CHANGE_REJECTION_REASON_REQUIRED.getErrorCode());
        }

        // 수집 명령과 동일하게 장소와 운영 정보를 잠근 뒤 후보를 잠근다.
        var places = jdbc.query("""
                SELECT p.place_id, p.publish_status
                FROM place_operating_info o
                JOIN place p ON p.place_id = o.place_id
                WHERE o.place_id = (SELECT place_id FROM place_operating_change_candidate WHERE candidate_id = ?)
                FOR UPDATE
                """, (row, index) -> new Target(row.getLong("place_id"), row.getByte("publish_status")), candidateId);
        if (places.isEmpty()) throw new BusinessException(OPERATING_CHANGE_NOT_FOUND.getErrorCode());
        var target = places.getFirst();
        var candidates = jdbc.query("""
                SELECT field_name, previous_value, new_value, resolved_at
                FROM place_operating_change_candidate WHERE candidate_id = ? FOR UPDATE
                """, (row, index) -> new Candidate(row.getString("field_name"), row.getString("previous_value"),
                row.getString("new_value"), row.getObject("resolved_at", LocalDateTime.class)), candidateId);
        if (candidates.isEmpty()) throw new BusinessException(OPERATING_CHANGE_NOT_FOUND.getErrorCode());
        var candidate = candidates.getFirst();
        if (candidate.resolvedAt() != null) throw new BusinessException(OPERATING_CHANGE_ALREADY_RESOLVED.getErrorCode());

        if (request.decision() == Decision.APPROVED) {
            applyChange(target, candidate);
        }
        jdbc.update("""
                UPDATE place_operating_change_candidate
                SET decision = ?, resolved_at = ?, resolved_by = ?, resolution_reason = ?
                WHERE candidate_id = ?
                """, request.decision().name(), LocalDateTime.now(placeClock), memberId, reason, candidateId);
    }

    private void applyChange(Target target, Candidate candidate) {
        if (target.publishStatus() != PlacePublishStatus.PUBLISHED.getCode()) {
            throw new BusinessException(OPERATING_CHANGE_NOT_PUBLIC.getErrorCode());
        }
        String column = switch (candidate.field()) {
            case "operatingHours" -> "operating_hours";
            case "restDays" -> "rest_days";
            case "contactPhone" -> "contact_phone";
            case "openingPeriod" -> "opening_period";
            case "usageFee" -> "usage_fee";
            default -> throw new IllegalStateException("지원하지 않는 운영 정보 항목입니다.");
        };
        String current = jdbc.queryForObject("SELECT " + column + " FROM place_operating_info WHERE place_id = ? FOR UPDATE",
                String.class, target.placeId());
        if (!Objects.equals(current, candidate.previousValue())) {
            throw new BusinessException(OPERATING_CHANGE_STALE.getErrorCode());
        }
        jdbc.update("UPDATE place_operating_info SET " + column + " = ? WHERE place_id = ?",
                candidate.newValue(), target.placeId());
    }

    private void requireAdmin(long memberId) {
        if (members.findActiveMember(memberId).getRole() != MemberRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "운영자만 이용할 수 있습니다.");
        }
    }

    private record Target(long placeId, byte publishStatus) {}

    private record Candidate(String field, String previousValue, String newValue, LocalDateTime resolvedAt) {}
}
