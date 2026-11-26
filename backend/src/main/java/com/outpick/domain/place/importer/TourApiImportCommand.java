package com.outpick.domain.place.importer;

import com.outpick.BackendApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** 일반 서버와 분리된 일회성 로컬 명령. 웹 서버를 열지 않고 처리 후 종료한다. */
public class TourApiImportCommand {
    public static void main(String[] args) {
        var transport = new TourApiHttpTransport(System.getenv("TOUR_API_KEY"));
        boolean failed;
        try (var context = new SpringApplicationBuilder(BackendApplication.class)
                .web(WebApplicationType.NONE).run("--spring.profiles.active=local")) {
            var service = new TourApiImportService(new TourApiClient(transport), new TourApiNormalizer(),
                    context.getBean(TourImportWriter.class));
            var results = service.importPlaces(TourImportTarget.initialPlaces());
            results.forEach(result -> System.out.printf("TOUR_IMPORT contentId=%s status=%s placeId=%s reason=%s%n",
                    result.contentId(), result.status(), result.placeId(), result.reason()));
            failed = results.stream().anyMatch(result -> result.status() == TourImportResult.Status.FAILED);
        }
        if (failed) System.exit(1);
    }
}
