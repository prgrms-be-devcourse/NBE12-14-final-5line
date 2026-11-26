package com.outpick.domain.place.importer;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Map;
import java.util.regex.Pattern;

public class TourApiNormalizer {
    private static final DateTimeFormatter MODIFIED = DateTimeFormatter.ofPattern("uuuuMMddHHmmss").withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern URL_IN_TEXT = Pattern.compile("(?i)(https?://[^\\s<>\"']+|www\\.[^\\s<>\"']+)");
    private static final Map<String, String> REGIONS = Map.ofEntries(
            Map.entry("11", "서울"), Map.entry("12", "전남광주통합특별시"),
            Map.entry("26", "부산"), Map.entry("27", "대구"), Map.entry("28", "인천"),
            Map.entry("29", "광주"), Map.entry("30", "대전"), Map.entry("31", "울산"), Map.entry("36", "세종"),
            Map.entry("41", "경기"), Map.entry("43", "충북"), Map.entry("44", "충남"), Map.entry("46", "전남"),
            Map.entry("47", "경북"), Map.entry("48", "경남"), Map.entry("50", "제주"), Map.entry("51", "강원"), Map.entry("52", "전북"));

    public TourImportCandidate normalize(TourImportTarget target, TourApiPlace input) {
        try {
            String name = text(input.title());
            if (!target.contentId().equals(input.contentId()) || !"28".equals(input.contentTypeId())
                    || !name.replaceAll("\\s+", "").equals(target.expectedName().replaceAll("\\s+", ""))) throw invalid();
            requiredLength(name, 50);
            String address = (text(input.address()) + " " + text(input.addressDetail())).trim();
            requiredLength(address, 100);
            String description = text(input.overview());
            if (description.getBytes(StandardCharsets.UTF_8).length > 60000) throw invalid();
            var latitude = coordinate(input.latitude(), 90);
            var longitude = coordinate(input.longitude(), 180);
            if (latitude.signum() == 0 && longitude.signum() == 0) throw invalid();
            var region = normalizeRegion(input, address);
            String homepage = homepage(input.homepage());
            if (homepage != null && homepage.length() > 100) throw invalid();
            var modifiedAt = input.modifiedAt().isBlank() ? null : LocalDateTime.parse(input.modifiedAt(), MODIFIED);
            return new TourImportCandidate(target, name, description.isBlank() ? null : description, address,
                    latitude, longitude, homepage, region.code(), region.name(),
                    region.districtCode(), region.districtName(), modifiedAt, input.imageCopyrightCode());
        } catch (Exception exception) {
            throw invalid();
        }
    }

    /** TourAPI의 시·도와 시·군·구를 서비스의 상위 지역과 하위 지역으로 변환한다. */
    private RegionFields normalizeRegion(TourApiPlace input, String address) {
        // 세종 상세 응답은 두 지역 코드에 모두 36110을 쓰고 주소에서 시·군·구 이름을 생략한다.
        if ("36110".equals(input.regionCode()) && "36110".equals(input.districtCode())) {
            return new RegionFields("KR-36", REGIONS.get("36"), "KR-36110", "세종시");
        }
        String name = REGIONS.get(input.regionCode());
        if (name == null || !input.districtCode().matches("[0-9]{3}")) throw invalid();
        String[] addressParts = address.split("\\s+");
        if (addressParts.length < 2 || !addressParts[1].matches(".*[시군구]$")) throw invalid();
        String districtName = addressParts[1];
        if (addressParts.length > 2 && addressParts[2].endsWith("구")) districtName += " " + addressParts[2];
        String code = "KR-" + input.regionCode();
        return new RegionFields(code, name, code + input.districtCode(), districtName);
    }

    private record RegionFields(String code, String name, String districtCode, String districtName) {}

    private BigDecimal coordinate(String value, int limit) {
        if (value == null || !value.matches("[+-]?[0-9]{1,3}(\\.[0-9]{1,20})?")) throw invalid();
        var result = new BigDecimal(value);
        if (result.abs().compareTo(BigDecimal.valueOf(limit)) > 0) throw invalid();
        return result.setScale(7, RoundingMode.HALF_UP);
    }

    private void requiredLength(String value, int max) {
        if (value.isBlank() || value.codePointCount(0, value.length()) > max) throw invalid();
    }

    public String text(String input) {
        if (input == null || input.isBlank()) return "";
        var text = new StringBuilder();
        parse(input, new HTMLEditorKit.ParserCallback() {
            private int ignored;
            @Override public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
                if (tag == HTML.Tag.SCRIPT || tag == HTML.Tag.STYLE) ignored++;
                if (tag == HTML.Tag.P || tag == HTML.Tag.DIV) text.append('\n');
            }
            @Override public void handleEndTag(HTML.Tag tag, int position) {
                if ((tag == HTML.Tag.SCRIPT || tag == HTML.Tag.STYLE) && ignored > 0) ignored--;
                if (tag == HTML.Tag.P || tag == HTML.Tag.DIV) text.append('\n');
            }
            @Override public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
                if (tag == HTML.Tag.BR) text.append('\n');
            }
            @Override public void handleText(char[] data, int position) {
                if (ignored == 0) text.append(data).append(' ');
            }
        });
        return text.toString().replace('\u00a0', ' ').replaceAll("[ \\t]+", " ").replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n").trim();
    }

    public String homepage(String input) {
        if (input == null || input.isBlank()) return null;
        String[] link = {null};
        parse(input, new HTMLEditorKit.ParserCallback() {
            @Override public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
                if (tag == HTML.Tag.A && link[0] == null) {
                    Object value = attributes.getAttribute(HTML.Attribute.HREF);
                    if (value != null) link[0] = validUrl(value.toString());
                }
            }
        });
        if (link[0] != null) return link[0];
        var matcher = URL_IN_TEXT.matcher(text(input));
        while (matcher.find()) {
            String candidate = validUrl(matcher.group().replaceAll("[),.;]+$", ""));
            if (candidate != null) return candidate;
        }
        return null;
    }

    private String validUrl(String value) {
        try {
            var uri = URI.create(value.startsWith("www.") ? "https://" + value : value);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null ? uri.toASCIIString() : null;
        } catch (Exception exception) { return null; }
    }

    private void parse(String value, HTMLEditorKit.ParserCallback callback) {
        try { new ParserDelegator().parse(new StringReader(value), callback, true); }
        catch (Exception exception) { throw invalid(); }
    }

    private TourApiException invalid() { return new TourApiException(TourApiException.Reason.INVALID_DATA); }
}
