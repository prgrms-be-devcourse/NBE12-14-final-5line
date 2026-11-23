package com.outpick.domain.place.importer;

import com.outpick.BackendApplication;

/** DB의 TourAPI 수집 장소 전체에 운영 안내를 최초 연결하는 명시적 명령. */
public class TourOperatingInfoCommand {
    public static void main(String[] args) {
        var transport = new TourApiHttpTransport(System.getenv("TOUR_API_KEY"));
        boolean failed;
        try (var context = TourImportApplication.builder(BackendApplication.class).run()) {
            var service = new TourOperatingImportService(new TourApiOperatingClient(transport),
                    context.getBean(TourOperatingInfoWriter.class));
            var summary = service.importAll(result -> System.out.printf("TOUR_OPERATING contentId=%s status=%s reason=%s%n",
                    result.contentId(), result.status(), result.reason()));
            System.out.printf("TOUR_OPERATING_SUMMARY created=%d skipped=%d noData=%d failed=%d interrupted=%s%n",
                    summary.created(), summary.skipped(), summary.noData(), summary.failed(), summary.interrupted());
            failed = summary.failed() > 0 || summary.interrupted();
        }
        if (failed) System.exit(1);
    }
}
