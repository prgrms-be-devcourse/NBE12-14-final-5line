package com.outpick.domain.place.importer;

import java.util.function.Consumer;

/** DB 수집 이력을 100건씩 읽고, HTTP는 트랜잭션 밖에서 순차 호출한다. */
public class TourOperatingImportService {
    private final TourApiOperatingClient client;
    private final TourOperatingInfoWriter writer;

    public TourOperatingImportService(TourApiOperatingClient client, TourOperatingInfoWriter writer) {
        this.client = client;
        this.writer = writer;
    }

    public record Result(String contentId, String status, String reason) { }
    public record Summary(int created, int skipped, int noData, int failed, boolean interrupted) { }

    public Summary importAll(Consumer<Result> output) {
        String cursor = "";
        int created = 0, skipped = 0, noData = 0, failed = 0;
        while (!Thread.currentThread().isInterrupted()) {
            var ids = writer.findContentIdsAfter(cursor, 100);
            if (ids.isEmpty()) return new Summary(created, skipped, noData, failed, false);
            for (var id : ids) {
                if (Thread.currentThread().isInterrupted()) return new Summary(created, skipped, noData, failed, true);
                var result = importOne(id);
                output.accept(result);
                switch (result.status()) {
                    case "CREATED" -> created++;
                    case "SKIPPED" -> skipped++;
                    case "NO_DATA" -> noData++;
                    default -> failed++;
                }
                cursor = id;
            }
        }
        return new Summary(created, skipped, noData, failed, true);
    }

    private Result importOne(String id) {
        try {
            // 재실행 시 HTTP 호출도 생략하고 기존 검수 결과를 유지한다.
            if (writer.hasOperatingInfo(id)) return new Result(id, "SKIPPED", null);
            // 대량 대상에서도 요청이 집중되지 않도록 외부 호출 전에만 간격을 둔다.
            Thread.sleep(250);
            var snapshot = client.getOperatingInfo(id);
            if (Thread.currentThread().isInterrupted()) return new Result(id, "FAILED", "INTERRUPTED");
            return new Result(id, writer.saveInitial(snapshot), null);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new Result(id, "FAILED", "INTERRUPTED");
        } catch (TourApiException exception) {
            if (exception.getReason() == TourApiException.Reason.NO_DATA) return new Result(id, "NO_DATA", null);
            return new Result(id, "FAILED", exception.getReason().name());
        } catch (RuntimeException exception) {
            // 원본 예외에는 응답/인증키 등이 있을 수 있어 출력하지 않는다.
            return new Result(id, "FAILED", "SAVE_FAILED");
        }
    }
}
