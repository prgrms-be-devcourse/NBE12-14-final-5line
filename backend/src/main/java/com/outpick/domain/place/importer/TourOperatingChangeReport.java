package com.outpick.domain.place.importer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** 수집한 최신 응답을 기존 안내와 대조해 사람 검수용 보고서로 만든다. */
final class TourOperatingChangeReport {
    private static final String TOUR_SOURCE = "https://www.data.go.kr/data/15101578/openapi.do";
    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    record Baseline(long placeId, String placeName, String sourceUrl, TourOperatingSnapshot snapshot) {}
    record FieldChange(String field, String previousValue, String newValue) {}
    record Candidate(long placeId, String placeName, String contentId, String previousSourceUrl,
                     String newSourceUrl, List<FieldChange> changes) {}
    record Issue(long placeId, String contentId, String reason, List<String> fields, String sourceUrl) {}
    record Report(String startedAt, int selected, int requested, int unchanged, int noData, int failed,
                  long lastCompletedPlaceId, boolean stopped, List<Candidate> candidates, List<Issue> issues) {}

    Report compare(List<Baseline> baselines, TourApiOperatingClient client, long afterPlaceId) {
        var startedAt = Instant.now().toString();
        var candidates = new ArrayList<Candidate>();
        var issues = new ArrayList<Issue>();
        int requested = 0, unchanged = 0, noData = 0, failed = 0;
        long lastCompleted = afterPlaceId;
        boolean stopped = false;
        for (var baseline : baselines) {
            try {
                if (requested > 0) Thread.sleep(250);
                if (Thread.currentThread().isInterrupted()) {
                    stopped = true;
                    break;
                }
                requested++;
                var previous = baseline.snapshot();
                var next = client.getOperatingInfo(previous.contentId());
                var changes = new ArrayList<FieldChange>();
                var missing = new ArrayList<String>();
                compareField("operatingHours", previous.operatingHours(), next.operatingHours(), changes, missing);
                compareField("restDays", previous.restDays(), next.restDays(), changes, missing);
                compareField("contactPhone", previous.contactPhone(), next.contactPhone(), changes, missing);
                compareField("openingPeriod", previous.openingPeriod(), next.openingPeriod(), changes, missing);
                compareField("usageFee", previous.usageFee(), next.usageFee(), changes, missing);
                if (!missing.isEmpty()) issues.add(issue(baseline, "MISSING_FIELDS", List.copyOf(missing)));
                if (!changes.isEmpty()) {
                    candidates.add(new Candidate(baseline.placeId(), baseline.placeName(), previous.contentId(),
                            baseline.sourceUrl(), TOUR_SOURCE, List.copyOf(changes)));
                } else if (missing.isEmpty()) {
                    unchanged++;
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                stopped = true;
                break;
            } catch (TourApiException exception) {
                var reason = exception.getReason();
                issues.add(issue(baseline, reason.name(), List.of()));
                if (reason == TourApiException.Reason.NO_DATA) noData++;
                else failed++;
                if (reason == TourApiException.Reason.RATE_LIMIT || Thread.currentThread().isInterrupted()) {
                    stopped = true;
                    break;
                }
            }
            lastCompleted = baseline.placeId();
        }
        return new Report(startedAt, baselines.size(), requested, unchanged, noData, failed, lastCompleted, stopped,
                List.copyOf(candidates), List.copyOf(issues));
    }

    private static void compareField(String field, String previous, String next,
                                     List<FieldChange> changes, List<String> missing) {
        if (next == null || next.isBlank()) {
            if (previous != null && !previous.isBlank()) missing.add(field);
        } else if (!Objects.equals(normalize(previous), normalize(next))) {
            changes.add(new FieldChange(field, previous, next));
        }
    }

    static String normalize(String value) {
        return value == null ? null : WHITESPACE.matcher(value).replaceAll(" ").strip();
    }

    private static Issue issue(Baseline baseline, String reason, List<String> fields) {
        return new Issue(baseline.placeId(), baseline.snapshot().contentId(), reason, fields, TOUR_SOURCE);
    }
}
