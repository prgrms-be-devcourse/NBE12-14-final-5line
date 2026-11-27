package com.outpick.domain.place.service;

import com.outpick.domain.place.dto.SnorkelingSafetyResponse;
import com.outpick.domain.place.exception.PlaceErrorCode;
import com.outpick.domain.place.repository.PlaceDetailRepository;
import com.outpick.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SnorkelingSafetyService {
    private static final Set<String> SITE_TYPES=Set.of("DESIGNATED_BEACH","NATURAL_BEACH","ROCKY_COAST",
            "PORT","BREAKWATER","FISHING_VILLAGE","EXPERIENCE_CENTER","GUIDED_TOUR");
    private static final Set<String> ENTRY_STATUSES=Set.of("ALLOWED","RESTRICTED","PROHIBITED","UNKNOWN");
    private final JdbcTemplate jdbc;
    private final PlaceDetailRepository places;
    private final PlaceCanonicalResolver aliases;
    private final Clock placeClock;
    private final JsonMapper json=JsonMapper.builder().build();

    private record Evidence(JsonNode value,String source,LocalDateTime checked,LocalDateTime verified) {}

    public SnorkelingSafetyResponse get(long id) {
        id=aliases.resolve(id);
        places.findPublishedPlace(id).orElseThrow(()->new BusinessException(PlaceErrorCode.PLACE_NOT_FOUND.getErrorCode()));
        LocalDate today=LocalDate.now(placeClock);
        List<Evidence> type=evidence(id,"snorkelingSiteType",today);
        String siteType=type.isEmpty()?"UNKNOWN":type.getFirst().value().asString("UNKNOWN");
        if(!SITE_TYPES.contains(siteType))siteType="UNKNOWN";
        List<Evidence> safety=evidence(id,"snorkelingSafety",today);
        Evidence selected=null;
        String entryStatus="UNKNOWN";
        for(Evidence item:safety){
            String status=item.value().path("entryStatus").asString("UNKNOWN");
            if(!ENTRY_STATUSES.contains(status))status="UNKNOWN";
            if(selected==null || rank(status)>rank(entryStatus)){selected=item;entryStatus=status;}
        }
        List<String> common=new ArrayList<>(List.of("구명조끼 착용 권장", "단독 입수 금지 권고", "음주 후 입수 금지",
                "기상 및 해양 상태 확인", "입수 통제 안내 준수", "위급 시 119 신고"));
        switch(siteType){
            case "NATURAL_BEACH", "ROCKY_COAST" -> common.addAll(List.of("미끄러운 암반·급격한 수심 변화에 주의",
                    "안전요원과 구조시설 유무, 입출수 경로 확인", "조류와 높은 파도 확인"));
            case "PORT" -> common.addAll(List.of("선박 통행·어업 작업 구역 입수 금지", "허용된 구역과 현장 통제 안내 확인"));
            case "BREAKWATER" -> common.addAll(List.of("테트라포드 접근·등반 금지", "방파제 다이빙 금지",
                    "높은 파도·월파 및 출입 제한 구역 주의", "허용된 입수 구역이 확인되지 않으면 입수 금지"));
            case "EXPERIENCE_CENTER", "GUIDED_TOUR" -> common.addAll(List.of("운영기관 안전교육·필수 장비·가이드 지시 준수",
                    "연령과 건강 관련 이용 조건 확인"));
            default -> { }
        }
        List<String> confirmed=new ArrayList<>();
        if(selected!=null && selected.value().path("confirmedWarnings").isArray())
            for(JsonNode warning:selected.value().path("confirmedWarnings"))
                if(warning.isTextual())confirmed.add(warning.asString());
        Long eligible=jdbc.queryForObject("SELECT COUNT(*) FROM snorkeling_allowed_place WHERE place_id=?",Long.class,id);
        Long snorkelingActivity=jdbc.queryForObject("""
                SELECT COUNT(*) FROM place_activity pa JOIN activity_type at ON at.activity_type_id=pa.activity_type_id
                JOIN activity_category ac ON ac.activity_category_id=at.activity_category_id
                WHERE pa.place_id=? AND ac.name='스노클링'
                """,Long.class,id);
        boolean recommendable=eligible!=null&&eligible>0&&snorkelingActivity!=null&&snorkelingActivity>0;
        return new SnorkelingSafetyResponse(id,siteType,entryStatus,recommendable,
                selected==null?null:selected.value(),common,confirmed,selected==null?null:selected.source(),
                selected==null?null:selected.checked(),selected==null?null:selected.verified());
    }
    private int rank(String status){return switch(status){case "PROHIBITED"->4;case "RESTRICTED"->3;case "UNKNOWN"->2;default->1;};}
    private List<Evidence> evidence(long id,String field,LocalDate today){
        return jdbc.query("""
                SELECT value_json,source_url,checked_at,verified_at FROM place_detail_fact
                WHERE place_id=? AND field_key=? AND verification_status='VERIFIED' AND verified_at IS NOT NULL
                  AND (valid_from IS NULL OR valid_from<=?) AND (valid_until IS NULL OR valid_until>=?)
                ORDER BY CASE source_kind WHEN 'OFFICIAL_WEBSITE' THEN 0 WHEN 'OFFICIAL_PUBLIC_DATA' THEN 1
                  WHEN 'OFFICIAL_TOURISM_ARTICLE' THEN 2 WHEN 'TOUR_API' THEN 3 ELSE 4 END, verified_at DESC, fact_id DESC
                """,(rs,n)->new Evidence(json.readTree(rs.getString(1)),rs.getString(2),
                        rs.getTimestamp(3)==null?null:rs.getTimestamp(3).toLocalDateTime(),
                        rs.getTimestamp(4).toLocalDateTime()),id,field,today,today);
    }
}
