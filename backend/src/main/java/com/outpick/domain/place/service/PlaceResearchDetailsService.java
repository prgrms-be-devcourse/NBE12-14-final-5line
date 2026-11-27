package com.outpick.domain.place.service;

import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class PlaceResearchDetailsService {
    private final PlaceDetailService places;
    private final JdbcTemplate jdbc;
    private final JsonMapper json=JsonMapper.builder().build();
    public record Evidence(String label,String text,String sourceUrl,String checkedAt,String status) { }
    public List<Evidence> get(long id) {
        places.getDetail(id);
        Map<String,Evidence> result=new LinkedHashMap<>();
        for(String raw:jdbc.queryForList("SELECT raw_evidence FROM place_import_candidate WHERE place_id=? ORDER BY candidate_id",String.class,id)) {
            JsonNode root=json.readTree(raw);
            for(JsonNode patch:root.path("officialPatches")) {
                var properties=patch.path("values").properties();
                for(var property:properties) {
                    String key=property.getKey();
                    if(Set.of("adult","youth","child","skateRental","equipmentSetPrice","lifejacketPrice","maskPrice").contains(key))
                        add(result,label(key),property.getValue().asString("")+"원",patch.path("sourceUrl").asString(""),patch.path("checkedAt").asString(""));
                    else if(LABELS.containsKey(key)||EXTRA_LABELS.containsKey(key))add(result,label(key),text(property.getValue()),patch.path("sourceUrl").asString(""),patch.path("checkedAt").asString(""));
                }
            }
            for(JsonNode fee:root.path("apiPriceEvidence"))add(result,fee.path("field").asString("요금 원문"),fee.path("text").asString(""),"https://www.data.go.kr/data/15101578/openapi.do","");
            JsonNode api=root.path("apiSource");
            for(JsonNode intro:api.path("intro"))extractIntro(result,intro);
            for(JsonNode intro:api.path("detailIntro"))extractIntro(result,intro);
            for(JsonNode info:api.path("details"))add(result,info.path("infoname").asString("추가 안내"),info.path("infotext").asString(""),"https://www.data.go.kr/data/15101578/openapi.do","");
        }
        for(var row:jdbc.queryForList("SELECT field_key,value_json,source_url,checked_at,verification_status FROM place_detail_fact WHERE place_id=? AND verification_status='NEEDS_REVIEW'",id)) {
            String key=Objects.toString(row.get("field_key"),"");
            if(LABELS.containsKey(key))add(result,label(key),text(json.readTree(row.get("value_json").toString())),Objects.toString(row.get("source_url"),""),Objects.toString(row.get("checked_at"),""));
        }
        for(var row:jdbc.queryForList("SELECT ticket_name,price_kind,age_band,amount_krw,raw_price_text,source_url,checked_at,valid_from,valid_until FROM place_price WHERE place_id=? AND verification_status='NEEDS_REVIEW' AND price_basis='NORMAL'",id)) {
            String amount=row.get("amount_krw")==null?"금액 미확인":row.get("amount_krw")+"원";
            String detail=Objects.toString(row.get("raw_price_text"),amount)+" · 적용 기간 "+Objects.toString(row.get("valid_from"),"미확인")+" ~ "+Objects.toString(row.get("valid_until"),"미확인");
            add(result,Objects.toString(row.get("ticket_name"),Objects.toString(row.get("price_kind"),"요금")),detail,Objects.toString(row.get("source_url"),""),Objects.toString(row.get("checked_at"),""));
        }
        return List.copyOf(result.values());
    }
    private void extractIntro(Map<String,Evidence> result,JsonNode intro){
        for(var item:Map.of("usetime","운영시간","usetimeleports","운영시간","restdate","휴무일","restdateleports","휴무일","usefeeleports","이용요금 원문","expagerange","이용 제한","reservation","예약 안내","parking","주차 안내").entrySet())
            add(result,item.getValue(),intro.path(item.getKey()).asString(""),"https://www.data.go.kr/data/15101578/openapi.do","");
    }
    private static final Map<String,String> LABELS=Map.ofEntries(
        Map.entry("address","주소"),Map.entry("season","운영 시즌"),Map.entry("operatingHours","운영시간"),Map.entry("restDays","휴무일"),Map.entry("booking","예약 안내"),Map.entry("preparation","준비물"),Map.entry("phone","문의전화"),Map.entry("facilities","편의시설"),Map.entry("restriction","이용 제한"),Map.entry("openingDate","개장일"),Map.entry("closingDate","폐장일"),Map.entry("freeSkatingHours","자유 스케이팅 시간"),Map.entry("resurfacingTimes","정빙 시간"),Map.entry("adult","성인 요금 자료"),Map.entry("youth","청소년 요금 자료"),Map.entry("child","어린이 요금 자료"),Map.entry("skateRental","스케이트 대여 자료"),Map.entry("equipmentSetPrice","장비 세트 대여 자료"),Map.entry("lifejacketPrice","구명조끼 대여 자료"),Map.entry("maskPrice","마스크 대여 자료"));
    private static final Map<String,String> EXTRA_LABELS=Map.ofEntries(
        Map.entry("price","금액(원)"),Map.entry("ageUnder","미만 연령"),Map.entry("ageFrom","이상 연령"),Map.entry("adult","성인"),Map.entry("child","어린이"),Map.entry("weekday","평일"),Map.entry("weekend","주말"),Map.entry("duration","이용 시간"),Map.entry("count","이용 횟수"),Map.entry("persons","탑승 인원"),Map.entry("hours","운영시간"),Map.entry("breaks","휴식·정비 시간"),Map.entry("closed","휴무일"),Map.entry("openDate","개장일"),Map.entry("closeDate","폐장일"),Map.entry("normalPrices","정상 요금 조사 자료"),Map.entry("liftPrices","리프트 요금 조사 자료"),Map.entry("observedOperatingHours","운영시간 조사 자료"),Map.entry("equipmentDailyPrice","장비 일일 대여료 자료"),Map.entry("departures","출발 시간"),Map.entry("infantRule","유아 이용 조건"),Map.entry("peakExtraDeparture","성수기 추가 회차"),Map.entry("minimumHeightCm","최소 신장(cm)"),Map.entry("otherRestrictions","이용 제한"),Map.entry("weatherRestriction","기상에 따른 이용 제한"),Map.entry("chairliftIncluded","리프트 포함"),Map.entry("helmetRentalIncluded","헬멧 대여 포함"));
    private static String label(String key){return LABELS.getOrDefault(key,EXTRA_LABELS.getOrDefault(key,key));}
    private static String text(JsonNode node){if(node.isObject()){List<String> parts=new ArrayList<>();for(var entry:node.properties())parts.add(label(entry.getKey())+": "+text(entry.getValue()));return String.join(" / ",parts);}if(node.isBoolean())return node.asBoolean()?"해당":"해당 없음";if(node.isArray()){List<String> values=new ArrayList<>();node.forEach(v->values.add(text(v)));return String.join(" · ",values);}return node.isValueNode()?node.asString(""):node.toString();}
    private static void add(Map<String,Evidence> result,String label,String text,String url,String checked){
        text=text.replaceAll("<[^>]*>"," ").replace("&nbsp;"," ").trim();
        if(text.isBlank()||text.matches("(?s).*(할인|쿠폰|제휴|특가|프로모션).*"))return;
        result.putIfAbsent(label+"|"+text,new Evidence(label,text,url,checked,"NEEDS_REVIEW"));
    }
}
