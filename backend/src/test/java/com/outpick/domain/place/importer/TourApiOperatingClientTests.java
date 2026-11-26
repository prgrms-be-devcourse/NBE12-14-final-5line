package com.outpick.domain.place.importer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class TourApiOperatingClientTests {
    private String response(Map<String, String> item, boolean array) {
        return JsonMapper.builder().build().writeValueAsString(Map.of("response", Map.of("header", Map.of("resultCode", "0000"),
                "body", Map.of("items", Map.of("item", array ? List.of(item) : item)))));
    }

    @Test
    @DisplayName("명시적인 결과 0건과 모든 운영 항목 미제공을 정상 미제공으로 분류한다")
    void reportsNoDataForSuccessfulEmptyResults() {
        var emptyResults = """
                {"response":{"header":{"resultCode":"0000"},"body":{"totalCount":0,"items":""}}}
                """;
        var allFieldsMissing = response(Map.of("contentid", "1", "contenttypeid", "28"), true);
        for (String body : List.of(emptyResults, allFieldsMissing)) {
            assertThatThrownBy(() -> new TourApiOperatingClient(id -> body).getOperatingInfo("1"))
                    .isInstanceOfSatisfying(TourApiException.class, error ->
                            assertThat(error.getReason()).isEqualTo(TourApiException.Reason.NO_DATA));
        }
    }

    @Test
    @DisplayName("객체와 배열 응답을 읽고 운영 안내 HTML을 일반 텍스트로 변환한다")
    void readsBothItemShapesAndConvertsHtmlToText() {
        for (boolean array : List.of(true, false)) {
            var item = Map.of("contentid", "1", "contenttypeid", "28", "usetimeleports", "평일 10:00<br>주말 09:00",
                    "restdateleports", "월요일", "infocenterleports", "1855-3946");
            var data = new TourApiOperatingClient(id -> response(item, array)).getOperatingInfo("1");
            assertThat(data.operatingHours()).isEqualTo("평일 10:00\n주말 09:00");
            assertThat(data.restDays()).isEqualTo("월요일");
            assertThat(data.contactPhone()).isEqualTo("1855-3946");
        }
    }

    @Test
    @DisplayName("응답에 없는 운영 정보는 추정하지 않고 null로 유지한다")
    void keepsAbsentFieldsNullInsteadOfInventingYearRoundOperation() {
        var data = new TourApiOperatingClient(id -> response(Map.of("contentid", "1", "contenttypeid", "28",
                "infocenterleports", "1855-3946"), true)).getOperatingInfo("1");
        assertThat(data.operatingHours()).isNull();
        assertThat(data.restDays()).isNull();
    }

    @Test
    @DisplayName("비어 있거나 대상 시설과 유형이 다른 응답을 원문 노출 없이 거절한다")
    void rejectsEmptyWrongFacilityAndWrongTypeWithoutLeakingResponse() {
        for (var item : List.of(Map.of("contentid", "2", "contenttypeid", "28", "infocenterleports", "secret"),
                Map.of("contentid", "1", "contenttypeid", "32", "infocenterleports", "secret"))) {
            assertThatThrownBy(() -> new TourApiOperatingClient(id -> response(item, true)).getOperatingInfo("1"))
                    .isInstanceOf(TourApiException.class).hasNoCause().hasMessageNotContaining("secret");
        }
    }

    @Test
    @DisplayName("제공기관 오류와 길이 제한을 초과한 운영 정보를 거절한다")
    void rejectsProviderErrorAndOversizedField() {
        assertThatThrownBy(() -> new TourApiOperatingClient(id -> "<xml>secret</xml>").getOperatingInfo("1"))
                .isInstanceOf(TourApiException.class).hasNoCause().hasMessageNotContaining("secret");
        assertThatThrownBy(() -> new TourApiOperatingClient(id -> response(Map.of("contentid", "1", "contenttypeid", "28",
                "infocenterleports", "1".repeat(101)), true)).getOperatingInfo("1"))
                .isInstanceOf(TourApiException.class);
    }
}
