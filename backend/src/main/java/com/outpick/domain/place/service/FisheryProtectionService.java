package com.outpick.domain.place.service;

import com.outpick.domain.place.dto.FisheryProtectionResponse;
import com.outpick.domain.place.exception.PlaceErrorCode;
import com.outpick.domain.place.repository.PlaceDetailRepository;
import com.outpick.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FisheryProtectionService {
    private final JdbcTemplate jdbc;
    private final PlaceDetailRepository places;
    private final PlaceCanonicalResolver aliases;

    private record Row(long id, String name, String category, String kind, Integer sm, Integer sd, Integer em,
                       Integer ed, BigDecimal threshold, String unit, String measure, String region,
                       String excluded, String area, String excerpt, String basis, String notice,
                       String document, String url, String status, LocalDateTime verifiedAt) {}

    public FisheryProtectionResponse get(long placeId, LocalDate visitDate) {
        placeId = aliases.resolve(placeId);
        var place = places.findPublishedPlace(placeId)
                .orElseThrow(() -> new BusinessException(PlaceErrorCode.PLACE_NOT_FOUND.getErrorCode()));
        String address = place.getAddress() == null ? "" : place.getAddress();
        String region = place.getRegion().getName() == null ? "" : place.getRegion().getName();
        String location = address + " " + region;
        List<Row> all = jdbc.query("""
                SELECT r.rule_id,f.name,f.category,r.rule_kind,r.start_month,r.start_day,r.end_month,r.end_day,
                  r.threshold_value,r.threshold_unit,r.measurement_method,r.region_name,r.excluded_region,
                  r.area_description,r.source_excerpt,r.legal_basis,r.notice_number,r.source_document,
                  r.official_url,r.verification_status,r.last_verified_at
                FROM fishery_protection_rule r JOIN fishery_resource f ON f.resource_id=r.resource_id
                ORDER BY f.name,r.rule_kind,r.rule_id
                """, (rs,n) -> new Row(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),
                    (Integer)rs.getObject(5),(Integer)rs.getObject(6),(Integer)rs.getObject(7),(Integer)rs.getObject(8),
                    rs.getBigDecimal(9),rs.getString(10),rs.getString(11),rs.getString(12),rs.getString(13),
                    rs.getString(14),rs.getString(15),rs.getString(16),rs.getString(17),rs.getString(18),
                    rs.getString(19),rs.getString(20),rs.getTimestamp(21)==null?null:rs.getTimestamp(21).toLocalDateTime()));
        // A designated sea area is never inferred from a place's postal address.
        List<Row> applicable = all.stream().filter(r -> r.area == null && regionMatches(r.region,r.excluded,location)).toList();
        // A regional rule replaces the nationwide period for the same resource and rule kind.
        Map<String,Boolean> specific = applicable.stream().collect(Collectors.toMap(
                r -> r.name + "|" + r.kind, r -> !"전국".equals(r.region), (a,b) -> a || b));
        List<FisheryProtectionResponse.Rule> confirmed = new ArrayList<>();
        List<FisheryProtectionResponse.Rule> limits = new ArrayList<>();
        List<FisheryProtectionResponse.Rule> review = new ArrayList<>();
        for (Row r : applicable) {
            if ("전국".equals(r.region) && specific.getOrDefault(r.name + "|" + r.kind,false)) continue;
            boolean matches = inSeason(visitDate,r.sm,r.sd,r.em,r.ed);
            var item = toResponse(r,matches);
            if ("VERIFIED".equals(r.status) && "CLOSED_SEASON".equals(r.kind) && matches) confirmed.add(item);
            if ("VERIFIED".equals(r.status) && ("MIN_SIZE".equals(r.kind) || "MIN_WEIGHT".equals(r.kind))) limits.add(item);
            if (!"VERIFIED".equals(r.status) && !"REJECTED".equals(r.status)) review.add(item);
        }
        // Unknown legal scope, access to a fishing village, and local restrictions are independent of dates.
        return new FisheryProtectionResponse(placeId,visitDate,"UNKNOWN",
                "금어기 밖이어도 채취가 허용되는 것은 아닙니다. 입수 가능 여부와 채취 허용 여부는 별개입니다. 마을어장·보호구역·현장 통제를 확인하고, 미확인 시 관찰만 하세요.",
                List.copyOf(confirmed),List.copyOf(limits),List.copyOf(review));
    }

    static boolean inSeason(LocalDate date,Integer sm,Integer sd,Integer em,Integer ed) {
        if (date == null || sm == null || sd == null || em == null || ed == null) return false;
        MonthDay start=MonthDay.of(sm,sd), end=MonthDay.of(em,ed), day=MonthDay.from(date);
        return start.compareTo(end)<=0 ? day.compareTo(start)>=0 && day.compareTo(end)<=0
                : day.compareTo(start)>=0 || day.compareTo(end)<=0;
    }
    static boolean prohibitedSize(BigDecimal measured,BigDecimal threshold) {
        return measured!=null && threshold!=null && measured.compareTo(threshold)<=0;
    }
    static boolean regionMatches(String ruleRegion,String excluded,String location) {
        if(ruleRegion==null || ruleRegion.isBlank() || location==null)return false;
        if(excluded!=null && !excluded.isBlank())
            for(String term:excluded.split("[·,]")) if(containsRegion(location,term.trim()))return false;
        if("전국".equals(ruleRegion))return true;
        if(ruleRegion.contains("해역") || ruleRegion.contains("동경") || ruleRegion.contains("만"))return false;
        for(String term:ruleRegion.split("[·,]"))if(containsRegion(location,term.trim()))return true;
        return false;
    }
    private static boolean containsRegion(String location,String region) {
        if(region.isEmpty())return false;
        String canonical=location.replace("특별자치도", "").replace("광역시", "").replace("특별시", "")
                .replace("경상남도","경남").replace("경상북도","경북").replace("전라남도","전남")
                .replace("전라북도","전북").replace("충청남도","충남").replace("충청북도","충북")
                .replace("강원도","강원").replace("경기도","경기");
        String key=region.replace("특별자치도", "").replace("광역시", "").replace("특별시", "")
                .replace("경상남도","경남").replace("경상북도","경북").replace("전라남도","전남")
                .replace("전라북도","전북").replace("충청남도","충남").replace("충청북도","충북")
                .replace("강원도","강원").replace("경기도","경기");
        if(key.contains(" ")) {
            for(String part:key.split("\\s+")) if(!canonical.contains(part))return false;
            return true;
        }
        return canonical.contains(key);
    }
    private static FisheryProtectionResponse.Rule toResponse(Row r,boolean matches) {
        String period=r.sm==null?null:String.format("%d월 %d일~%d월 %d일",r.sm,r.sd,r.em,r.ed);
        String threshold=r.threshold==null?null:r.measure + " " + r.threshold.stripTrailingZeros().toPlainString() + r.unit + " 이하";
        return new FisheryProtectionResponse.Rule(r.id,r.name,r.category,r.kind,period,threshold,r.region,matches,
                r.excerpt,r.basis,r.notice,r.document,r.url,r.status,r.verifiedAt);
    }
}
