package com.outpick.domain.place.importer;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** 수동 비교 명령. 비교 결과를 새 JSON 파일에 기록한다. */
public class TourOperatingChangeCommand {
    public static void main(String[] args) throws IOException {
        String reportPath = System.getenv("TOUR_OPERATING_REPORT_PATH");
        if (reportPath == null || reportPath.isBlank()) {
            throw new IllegalArgumentException("TOUR_OPERATING_REPORT_PATH에 새 보고서 파일 경로를 지정하세요.");
        }
        Path output = Path.of(reportPath).toAbsolutePath();
        if (Files.exists(output) || !Files.isDirectory(output.getParent())) {
            throw new IllegalArgumentException("보고서의 상위 폴더가 존재하고 파일명은 사용 중이지 않아야 합니다.");
        }
        int limit = Integer.parseInt(System.getenv().getOrDefault("TOUR_OPERATING_REPORT_LIMIT", "20"));
        long after = Long.parseLong(System.getenv().getOrDefault("TOUR_OPERATING_REPORT_AFTER_ID", "0"));
        if (limit < 1 || limit > 500 || after < 0) {
            throw new IllegalArgumentException("비교 건수는 1~500, 시작 장소 ID는 0 이상이어야 합니다.");
        }
        var client = new TourApiOperatingClient(new TourApiHttpTransport(System.getenv("TOUR_API_KEY")));
        boolean saveCandidates = Boolean.parseBoolean(System.getenv("TOUR_OPERATING_REPORT_SAVE_CANDIDATES"));
        TourOperatingChangeReport.Report report;
        try (var context = new SpringApplicationBuilder(ReportConfiguration.class)
                .web(WebApplicationType.NONE)
                .run("--spring.flyway.enabled=false", "--spring.sql.init.mode=never",
                        "--spring.jpa.hibernate.ddl-auto=none")) {
            var baselines = context.getBean(TourOperatingChangeReader.class).findPublishedAfter(after, limit);
            report = new TourOperatingChangeReport().compare(baselines, client, after);
            var json = JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValueAsString(report);
            Files.writeString(output, json + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);
            if (saveCandidates) {
                var result = context.getBean(TourOperatingCandidateStore.class).save(report.candidates());
                System.out.printf("TOUR_OPERATING_CANDIDATES saved=%d duplicate=%d stale=%d%n",
                        result.saved(), result.duplicate(), result.stale());
            }
        }
        System.out.printf("TOUR_OPERATING_COMPARE selected=%d requested=%d changed=%d unchanged=%d noData=%d failed=%d stopped=%s%n",
                report.selected(), report.requested(), report.candidates().size(), report.unchanged(), report.noData(),
                report.failed(), report.stopped());
        if (report.failed() > 0 || report.stopped()) System.exit(1);
    }

    // 웹 서버용 애플리케이션 빈을 로드하지 않고 DB 자동 설정과 비교 조회만 사용한다.
    @EnableAutoConfiguration
    @Import({TourOperatingChangeReader.class, TourOperatingCandidateStore.class})
    static class ReportConfiguration {}
}
