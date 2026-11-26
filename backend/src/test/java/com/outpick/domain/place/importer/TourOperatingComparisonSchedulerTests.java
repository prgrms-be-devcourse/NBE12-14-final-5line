package com.outpick.domain.place.importer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class TourOperatingComparisonSchedulerTests {
    private final TourOperatingChangeReader reader = mock(TourOperatingChangeReader.class);
    private final TourOperatingCandidateStore store = mock(TourOperatingCandidateStore.class);
    private final TourApiOperatingClient client = mock(TourApiOperatingClient.class);

    @Test
    @DisplayName("호출 제한 전에 찾은 후보를 저장하고 다음 실행에서 제한된 장소를 재시도한다")
    void resumesFromTheRateLimitedPlaceOnTheNextRun() {
        var scheduler = new TourOperatingComparisonScheduler(reader, store, client, 2);
        when(reader.findScheduledCursor()).thenReturn(0L, 1L);
        when(reader.findPublishedAfter(0, 3)).thenReturn(List.of(baseline(1), baseline(2)));
        when(reader.findPublishedAfter(1, 3)).thenReturn(List.of(baseline(2)));
        when(client.getOperatingInfo("1")).thenReturn(new TourOperatingSnapshot("1", "10:00", null, null));
        when(client.getOperatingInfo("2")).thenThrow(new TourApiException(TourApiException.Reason.RATE_LIMIT))
                .thenReturn(new TourOperatingSnapshot("2", "09:00", null, null));
        when(store.save(anyList())).thenReturn(new TourOperatingCandidateStore.Summary(0, 0, 0));

        scheduler.compareAndSave();
        verify(store).save(List.of(hoursChange()));
        verify(reader).updateScheduledCursor(1);

        new TourOperatingComparisonScheduler(reader, store, client, 2).compareAndSave();
        verify(reader).findPublishedAfter(1, 3);
        verify(client, times(2)).getOperatingInfo("2");
        verify(client).getOperatingInfo("1");
    }

    private TourOperatingChangeReport.Baseline baseline(long placeId) {
        return new TourOperatingChangeReport.Baseline(placeId, "시설", "https://example.com",
                new TourOperatingSnapshot(Long.toString(placeId), "09:00", null, null));
    }

    private TourOperatingChangeReport.Candidate hoursChange() {
        return new TourOperatingChangeReport.Candidate(1, "시설", "1", "https://example.com",
                "https://www.data.go.kr/data/15101578/openapi.do",
                List.of(new TourOperatingChangeReport.FieldChange("operatingHours", "09:00", "10:00")));
    }
}
