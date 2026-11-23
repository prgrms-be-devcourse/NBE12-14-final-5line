package com.outpick.domain.place.importer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TourOperatingImportServiceTests {
    @Test
    @DisplayName("천 곳이 넘는 대상을 다음 페이지로 진행하며 기존 안내는 외부 호출 없이 건너뛴다")
    void pagesThroughMoreThanThousandPlacesWithoutFetchingExistingInfo() {
        var writer = mock(TourOperatingInfoWriter.class);
        var client = mock(TourApiOperatingClient.class);
        var ids = java.util.stream.IntStream.rangeClosed(1, 1001).mapToObj(i -> "%05d".formatted(i)).toList();
        when(writer.findContentIdsAfter(anyString(), eq(100))).thenAnswer(invocation -> {
            String cursor = invocation.getArgument(0);
            return ids.stream().filter(id -> id.compareTo(cursor) > 0).limit(100).toList();
        });
        when(writer.hasOperatingInfo(anyString())).thenReturn(true);
        var results = new ArrayList<TourOperatingImportService.Result>();
        var summary = new TourOperatingImportService(client, writer).importAll(results::add);
        assertThat(summary).isEqualTo(new TourOperatingImportService.Summary(0, 1001, 0, 0, false));
        assertThat(results).extracting(TourOperatingImportService.Result::contentId).containsExactlyElementsOf(ids);
        verifyNoInteractions(client);
        verify(writer).findContentIdsAfter("01000", 100);
        verify(writer).findContentIdsAfter("01001", 100);
    }

    @Test
    @DisplayName("저장 실패 원문을 노출하지 않고 다음 장소의 저장을 계속한다")
    void isolatesStorageFailureAndScrubsException() {
        var writer = mock(TourOperatingInfoWriter.class);
        var client = mock(TourApiOperatingClient.class);
        when(writer.findContentIdsAfter("", 100)).thenReturn(List.of("1", "2"));
        when(writer.findContentIdsAfter("2", 100)).thenReturn(List.of());
        var first = new TourOperatingSnapshot("1", null, null, "123");
        var next = new TourOperatingSnapshot("2", null, null, "456");
        when(client.getOperatingInfo("1")).thenReturn(first);
        when(client.getOperatingInfo("2")).thenReturn(next);
        when(writer.saveInitial(first)).thenThrow(new IllegalStateException("serviceKey=secret"));
        when(writer.saveInitial(next)).thenReturn("CREATED");
        var results = new ArrayList<TourOperatingImportService.Result>();
        assertThat(new TourOperatingImportService(client, writer).importAll(results::add))
                .isEqualTo(new TourOperatingImportService.Summary(1, 0, 0, 1, false));
        assertThat(results.getFirst().reason()).isEqualTo("SAVE_FAILED");
        assertThat(results.toString()).doesNotContain("secret");
    }

    @Test
    @DisplayName("작업이 중단되면 다음 페이지와 외부 호출을 시작하지 않는다")
    void stopsWhenInterrupted() {
        var writer = mock(TourOperatingInfoWriter.class);
        var client = mock(TourApiOperatingClient.class);
        try {
            Thread.currentThread().interrupt();
            var summary = new TourOperatingImportService(client, writer).importAll(result -> fail("결과가 없어야 합니다"));
            assertThat(summary.interrupted()).isTrue();
            verifyNoInteractions(client, writer);
        } finally { Thread.interrupted(); }
    }
}
