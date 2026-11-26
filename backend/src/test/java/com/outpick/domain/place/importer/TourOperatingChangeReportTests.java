package com.outpick.domain.place.importer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TourOperatingChangeReportTests {
    @Test
    @DisplayName("값이 바뀐 항목만 이전 값과 출처와 함께 보고하고 빈 값과 요청 실패를 따로 기록한다")
    void reportsFieldChangesAndSeparatesMissingAndFailedResponses() {
        var client = new TourApiOperatingClient(id -> switch (id) {
            case "1" -> response(id, Map.of("usetimeleports", "10:00~18:00", "infocenterleports", "02-123"));
            case "2" -> response(id, Map.of("usetimeleports", "09:00~18:00", "restdateleports", "매주<br>월요일",
                    "infocenterleports", "02-123"));
            case "3" -> response(id, Map.of());
            default -> throw new TourApiException(TourApiException.Reason.REQUEST_FAILED);
        });

        var report = new TourOperatingChangeReport().compare(
                List.of(baseline(1), baseline(2), baseline(3), baseline(4)), client, 0);
        assertThat(report.requested()).isEqualTo(4);
        assertThat(report.unchanged()).isEqualTo(1);
        assertThat(report.noData()).isEqualTo(1);
        assertThat(report.failed()).isEqualTo(1);
        assertThat(report.candidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.placeId()).isEqualTo(1);
            assertThat(candidate.previousSourceUrl()).isEqualTo("https://example.com/previous");
            assertThat(candidate.newSourceUrl()).isEqualTo("https://www.data.go.kr/data/15101578/openapi.do");
            assertThat(candidate.changes()).containsExactly(
                    new TourOperatingChangeReport.FieldChange("operatingHours", "09:00~18:00", "10:00~18:00"));
        });
        assertThat(report.issues()).extracting(TourOperatingChangeReport.Issue::reason)
                .containsExactly("MISSING_FIELDS", "NO_DATA", "REQUEST_FAILED");
        assertThat(report.issues().getFirst().fields()).containsExactly("restDays");
    }

    @Test
    @DisplayName("요청 제한을 받으면 후속 호출을 멈추고 마지막 완료 장소를 기록한다")
    void stopsOnRateLimitWithoutSkippingTheLimitedPlaceInTheCursor() {
        var calls = new ArrayList<String>();
        var client = new TourApiOperatingClient(id -> {
            calls.add(id);
            if ("2".equals(id)) throw new TourApiException(TourApiException.Reason.RATE_LIMIT);
            return response(id, Map.of("usetimeleports", "10:00~18:00"));
        });
        var report = new TourOperatingChangeReport().compare(List.of(baseline(1), baseline(2), baseline(3)), client, 0);
        assertThat(calls).containsExactly("1", "2");
        assertThat(report.stopped()).isTrue();
        assertThat(report.failed()).isEqualTo(1);
        assertThat(report.lastCompletedPlaceId()).isEqualTo(1);
        assertThat(report.issues()).anyMatch(issue -> issue.placeId() == 2 && issue.reason().equals("RATE_LIMIT"));
    }

    private TourOperatingChangeReport.Baseline baseline(long id) {
        return new TourOperatingChangeReport.Baseline(id, "장소 " + id, "https://example.com/previous",
                new TourOperatingSnapshot(Long.toString(id), "09:00~18:00", "매주 월요일", "02-123"));
    }

    private String response(String id, Map<String, String> fields) {
        var item = new java.util.HashMap<>(fields);
        item.put("contentid", id);
        item.put("contenttypeid", "28");
        return JsonMapper.builder().build().writeValueAsString(Map.of("response", Map.of(
                "header", Map.of("resultCode", "0000"), "body", Map.of("items", Map.of("item", item)))));
    }
}
