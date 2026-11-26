package com.outpick.domain.place.importer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
class TourOperatingComparisonScheduler {
    private final TourOperatingChangeReader reader;
    private final TourOperatingCandidateStore store;
    private final TourApiOperatingClient client;
    private final int maxPlaces;

    TourOperatingComparisonScheduler(TourOperatingChangeReader reader, TourOperatingCandidateStore store,
                                     TourApiOperatingClient client, int maxPlaces) {
        if (maxPlaces < 1 || maxPlaces > 500) {
            throw new IllegalArgumentException("운영 정보 정기 비교 건수는 1~500이어야 합니다.");
        }
        this.reader = reader;
        this.store = store;
        this.client = client;
        this.maxPlaces = maxPlaces;
    }

    @Scheduled(cron = "${outpick.tour-operating.cron}", zone = "Asia/Seoul",
            scheduler = "tourOperatingTaskScheduler")
    public void compareAndSave() {
        long afterPlaceId = reader.findScheduledCursor();
        var baselines = reader.findPublishedAfter(afterPlaceId, maxPlaces + 1);
        if (baselines.isEmpty() && afterPlaceId != 0) {
            afterPlaceId = 0;
            baselines = reader.findPublishedAfter(afterPlaceId, maxPlaces + 1);
        }
        boolean hasMore = baselines.size() > maxPlaces;
        var batch = hasMore ? baselines.subList(0, maxPlaces) : baselines;
        var report = new TourOperatingChangeReport().compare(batch, client, afterPlaceId);
        var result = store.save(report.candidates());
        long nextAfterPlaceId = hasMore || report.stopped() ? report.lastCompletedPlaceId() : 0;
        reader.updateScheduledCursor(nextAfterPlaceId);

        log.info("TourAPI 운영 정보 정기 비교 selected={} requested={} saved={} duplicate={} stale={} noData={} failed={} stopped={} nextAfterPlaceId={}",
                report.selected(), report.requested(), result.saved(), result.duplicate(), result.stale(),
                report.noData(), report.failed(), report.stopped(), nextAfterPlaceId);
        if (report.failed() > 0 || report.stopped()) {
            log.warn("TourAPI 운영 정보 정기 비교에서 일부 요청을 완료하지 못했습니다. errors={}",
                    report.issues().stream().filter(issue -> !"MISSING_FIELDS".equals(issue.reason())
                            && !"NO_DATA".equals(issue.reason())).map(TourOperatingChangeReport.Issue::reason)
                            .distinct().toList());
        }
    }
}
