package com.outpick.domain.place.importer;

import com.outpick.domain.place.entity.PlacePublishStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** 외부 조회가 끝난 뒤 장소별 트랜잭션에서 검수 후보만 저장한다. */
class TourOperatingCandidateStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final JsonMapper mapper = JsonMapper.builder().build();

    TourOperatingCandidateStore(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    record Summary(int saved, int duplicate, int stale) {}

    Summary save(List<TourOperatingChangeReport.Candidate> candidates) {
        int saved = 0, duplicate = 0, stale = 0;
        for (var candidate : candidates) {
            var result = Objects.requireNonNull(transactions.execute(transaction -> savePlace(candidate)));
            saved += result.saved();
            duplicate += result.duplicate();
            stale += result.stale();
        }
        return new Summary(saved, duplicate, stale);
    }

    private Summary savePlace(TourOperatingChangeReport.Candidate candidate) {
        var baselines = jdbc.query("""
                SELECT o.source_content_id, o.operating_hours, o.rest_days, o.contact_phone, o.opening_period, o.usage_fee
                FROM place_operating_info o
                JOIN place p ON p.place_id = o.place_id
                WHERE o.place_id = ? AND o.source_content_id = ? AND p.publish_status = ?
                FOR UPDATE
                """, (row, index) -> new TourOperatingSnapshot(row.getString("source_content_id"),
                row.getString("operating_hours"), row.getString("rest_days"), row.getString("contact_phone"),
                row.getString("opening_period"), row.getString("usage_fee")),
                candidate.placeId(), candidate.contentId(), PlacePublishStatus.PUBLISHED.getCode());
        if (baselines.isEmpty()) return new Summary(0, 0, candidate.changes().size());

        var baseline = baselines.getFirst();
        int saved = 0, duplicate = 0, stale = 0;
        for (var change : candidate.changes()) {
            String current = switch (change.field()) {
                case "operatingHours" -> baseline.operatingHours();
                case "restDays" -> baseline.restDays();
                case "contactPhone" -> baseline.contactPhone();
                case "openingPeriod" -> baseline.openingPeriod();
                case "usageFee" -> baseline.usageFee();
                default -> throw new IllegalArgumentException("지원하지 않는 운영 정보 항목입니다.");
            };
            var previous = TourOperatingChangeReport.normalize(change.previousValue());
            if (!Objects.equals(TourOperatingChangeReport.normalize(current), previous)) {
                stale++;
                continue;
            }
            byte[] hash = hashValues(previous, TourOperatingChangeReport.normalize(change.newValue()));
            var existing = jdbc.query("""
                    SELECT candidate_id FROM place_operating_change_candidate
                    WHERE place_id = ? AND field_name = ?
                      AND (pending_hash = ? OR (values_hash = ? AND decision = 'REJECTED'))
                    """, (row, index) -> row.getLong("candidate_id"), candidate.placeId(), change.field(), hash, hash);
            if (!existing.isEmpty()) {
                duplicate++;
                continue;
            }
            jdbc.update("""
                    INSERT INTO place_operating_change_candidate (
                        place_id, field_name, previous_value, new_value, previous_source_url, new_source_url, values_hash
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, candidate.placeId(), change.field(), change.previousValue(), change.newValue(),
                    candidate.previousSourceUrl(), candidate.newSourceUrl(), hash);
            saved++;
        }
        return new Summary(saved, duplicate, stale);
    }

    private byte[] hashValues(String previous, String next) {
        byte[] values = mapper.writeValueAsBytes(Arrays.asList(previous, next));
        try {
            return MessageDigest.getInstance("SHA-256").digest(values);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }
}
