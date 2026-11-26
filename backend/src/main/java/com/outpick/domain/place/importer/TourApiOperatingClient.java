package com.outpick.domain.place.importer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public class TourApiOperatingClient {
    private final TourIntroTransport transport;
    private final TourApiNormalizer normalizer = new TourApiNormalizer();
    private final JsonMapper mapper = JsonMapper.builder().build();

    public TourApiOperatingClient(TourIntroTransport transport) { this.transport = transport; }

    public TourOperatingSnapshot getOperatingInfo(String contentId) {
        String raw = transport.fetchIntro(contentId);
        try {
            var response = mapper.readTree(raw).path("response");
            String code = response.path("header").path("resultCode").asString("");
            if (!"0000".equals(code) && !"00".equals(code)) throw invalid();
            var body = response.path("body");
            var items = body.path("items");
            if ("0".equals(body.path("totalCount").asString(""))
                    && ("".equals(items.asString(null)) || (items.isArray() && items.size() == 0)))
                throw new TourApiException(TourApiException.Reason.NO_DATA);
            var item = items.path("item");
            if (item.isArray()) {
                if (item.size() != 1) throw invalid();
                item = item.path(0);
            }
            if (!item.isObject() || !contentId.equals(item.path("contentid").asString(""))
                    || !"28".equals(item.path("contenttypeid").asString(""))) throw invalid();
            String hours = text(item, "usetimeleports", 10000);
            String rest = text(item, "restdateleports", 10000);
            String phone = text(item, "infocenterleports", 100);
            String openingPeriod = text(item, "openperiod", 10000);
            String usageFee = text(item, "usefeeleports", 10000);
            if (hours == null && rest == null && phone == null && openingPeriod == null && usageFee == null)
                throw new TourApiException(TourApiException.Reason.NO_DATA);
            return new TourOperatingSnapshot(contentId, hours, rest, phone, openingPeriod, usageFee);
        } catch (TourApiException exception) { throw exception; }
        catch (RuntimeException exception) { throw invalid(); }
    }

    private String text(JsonNode item, String name, int max) {
        String value = normalizer.text(item.path(name).asString(""));
        if (value.length() > max) throw invalid();
        return value.isBlank() ? null : value;
    }

    private TourApiException invalid() { return new TourApiException(TourApiException.Reason.INVALID_RESPONSE); }
}
