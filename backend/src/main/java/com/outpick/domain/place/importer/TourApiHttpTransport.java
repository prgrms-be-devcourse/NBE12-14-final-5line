package com.outpick.domain.place.importer;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class TourApiHttpTransport implements TourApiTransport, TourIntroTransport {
    private static final int MAX_BODY_BYTES = 2 * 1024 * 1024;
    private final String serviceKey;
    private final HttpClient client;

    public TourApiHttpTransport(String serviceKey) {
        this(serviceKey, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build());
    }

    TourApiHttpTransport(String serviceKey, HttpClient client) {
        if (serviceKey == null || serviceKey.isBlank()) throw new IllegalArgumentException("TOUR_API_KEY를 설정해 주세요.");
        this.serviceKey = serviceKey.trim();
        this.client = client;
    }

    @Override
    public String fetchCommon(String contentId) {
        return fetch(contentId, false);
    }

    @Override
    public String fetchIntro(String contentId) {
        return fetch(contentId, true);
    }

    private String fetch(String contentId, boolean intro) {
        if (contentId == null || !contentId.matches("[0-9]{1,50}"))
            throw new TourApiException(TourApiException.Reason.INVALID_DATA);
        try {
            var uri = URI.create("https://apis.data.go.kr/B551011/KorService2/" + (intro ? "detailIntro2" : "detailCommon2")
                    + "?MobileOS=ETC&MobileApp=OUTPICK&_type=json&numOfRows=1&pageNo=1&contentId=" + contentId
                    + (intro ? "&contentTypeId=28" : "")
                    + "&serviceKey=" + URLEncoder.encode(serviceKey, StandardCharsets.UTF_8));
            var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.limiting(
                    HttpResponse.BodyHandlers.ofByteArray(), MAX_BODY_BYTES));
            if (response.statusCode() == 429) throw new TourApiException(TourApiException.Reason.RATE_LIMIT);
            if (response.statusCode() != 200) throw new TourApiException(TourApiException.Reason.REQUEST_FAILED);
            return new String(response.body(), StandardCharsets.UTF_8);
        } catch (TourApiException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TourApiException(TourApiException.Reason.REQUEST_FAILED);
        } catch (Exception exception) {
            // 인증키가 든 URI가 예외 원인/메시지로 전파되지 않게 한다.
            throw new TourApiException(TourApiException.Reason.REQUEST_FAILED);
        }
    }
}
