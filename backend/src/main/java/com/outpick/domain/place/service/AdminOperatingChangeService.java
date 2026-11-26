package com.outpick.domain.place.service;

import com.outpick.domain.member.entity.MemberRole;
import com.outpick.domain.member.service.MemberService;
import com.outpick.domain.place.dto.AdminOperatingChangePageResponse;
import com.outpick.domain.place.entity.PlacePublishStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

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

    @Transactional(readOnly = true)
    public AdminOperatingChangePageResponse pending(long memberId, int page, int size) {
        if (members.findActiveMember(memberId).getRole() != MemberRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "운영자만 이용할 수 있습니다.");
        }
        byte published = PlacePublishStatus.PUBLISHED.getCode();
        long total = jdbc.queryForObject("SELECT COUNT(*) " + PENDING_PUBLIC_CANDIDATES, Long.class, published);
        var items = jdbc.query("""
                SELECT c.candidate_id, c.place_id, p.name, c.field_name, c.previous_value,
                       c.new_value, c.previous_source_url, c.new_source_url, c.detected_at
                """ + PENDING_PUBLIC_CANDIDATES + """
                ORDER BY c.detected_at DESC, c.candidate_id DESC
                LIMIT ? OFFSET ?
                """, (row, index) -> new AdminOperatingChangePageResponse.Item(
                row.getLong("candidate_id"), row.getLong("place_id"), row.getString("name"),
                row.getString("field_name"), row.getString("previous_value"), row.getString("new_value"),
                row.getString("previous_source_url"), row.getString("new_source_url"),
                row.getObject("detected_at", LocalDateTime.class)), published, size, (long) page * size);
        return new AdminOperatingChangePageResponse(items, page, size, total);
    }
}
