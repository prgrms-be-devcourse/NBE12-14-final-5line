package com.outpick.domain.place.service;

import com.outpick.domain.member.entity.MemberRole;
import com.outpick.domain.member.service.MemberService;
import com.outpick.domain.place.dto.AdminEvidenceResponse;
import com.outpick.domain.place.dto.AdminPriceDraftRequest;
import com.outpick.domain.place.dto.AdminFactDraftRequest;
import com.outpick.domain.place.repository.PlaceRepository;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;
import tools.jackson.databind.json.JsonMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminEvidenceService {
    private final MemberService members;
    private final PlaceRepository places;
    private final JdbcTemplate jdbc;
    private final Clock placeClock;
    private static final Set<String> SOURCES=Set.of("OFFICIAL_WEBSITE","OFFICIAL_PUBLIC_DATA","OFFICIAL_TOURISM_ARTICLE","TOUR_API");
    private static final Set<String> PRICE_UNITS=Set.of("PERSON","VEHICLE","EQUIPMENT","GROUP");
    private final JsonMapper json=JsonMapper.builder().build();

    public AdminEvidenceResponse get(long memberId, long placeId) {
        requirePlace(memberId, placeId);
        var prices = jdbc.query("""
                SELECT price_id,ticket_name,price_kind,price_basis,amount_krw,free_of_charge,
                       source_kind,source_url,checked_at,verified_at,valid_from,valid_until,verification_status
                FROM place_price WHERE place_id=? ORDER BY price_id DESC
                """, (rs,n)->new AdminEvidenceResponse.Price(rs.getLong(1),rs.getString(2),rs.getString(3),
                rs.getString(4),(Integer)rs.getObject(5),rs.getBoolean(6),rs.getString(7),rs.getString(8),
                rs.getTimestamp(9)==null?null:rs.getTimestamp(9).toLocalDateTime(),
                rs.getTimestamp(10)==null?null:rs.getTimestamp(10).toLocalDateTime(),
                rs.getDate(11)==null?null:rs.getDate(11).toLocalDate(),
                rs.getDate(12)==null?null:rs.getDate(12).toLocalDate(),rs.getString(13)),placeId);
        var facts = jdbc.query("""
                SELECT fact_id,field_key,value_json,source_kind,source_url,checked_at,verified_at,
                       valid_from,valid_until,verification_status
                FROM place_detail_fact WHERE place_id=? ORDER BY fact_id DESC
                """, (rs,n)->new AdminEvidenceResponse.Fact(rs.getLong(1),rs.getString(2),rs.getString(3),
                rs.getString(4),rs.getString(5),
                rs.getTimestamp(6)==null?null:rs.getTimestamp(6).toLocalDateTime(),
                rs.getTimestamp(7)==null?null:rs.getTimestamp(7).toLocalDateTime(),
                rs.getDate(8)==null?null:rs.getDate(8).toLocalDate(),
                rs.getDate(9)==null?null:rs.getDate(9).toLocalDate(),rs.getString(10)),placeId);
        return new AdminEvidenceResponse(prices, facts);
    }

    @Transactional
    public void createPrice(long memberId,long placeId,AdminPriceDraftRequest request) {
        requirePlace(memberId,placeId);
        if(!SOURCES.contains(request.sourceKind()) || !validSource(request.sourceUrl()) ||
                !request.priceKind().matches("[A-Z_]{2,30}") ||
                (request.priceUnit()!=null && !PRICE_UNITS.contains(request.priceUnit())) ||
                (request.amountKrw()==null && !request.freeOfCharge()) ||
                (request.freeOfCharge() && (request.amountKrw()==null || request.amountKrw()!=0)) ||
                (request.seasonLabel()!=null && (request.validFrom()==null || request.validUntil()==null)) ||
                (request.validFrom()!=null && request.validUntil()!=null && request.validFrom().isAfter(request.validUntil())))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"가격·출처·적용 기간을 확인해 주세요.");
        jdbc.update("""
                INSERT INTO place_price(place_id,price_kind,price_basis,age_band,day_type,season_label,
                  amount_krw,free_of_charge,ticket_name,price_unit,duration_minutes,use_count,passenger_count,
                  equipment_type,equipment_included,mandatory_extra_cost,residency_condition,raw_price_text,
                  source_kind,source_url,checked_at,valid_from,valid_until,verification_status)
                VALUES (? ,?,'NORMAL',?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'NEEDS_REVIEW')
                """,placeId,request.priceKind(),request.ageBand(),request.dayType(),request.seasonLabel(),
                request.amountKrw(),request.freeOfCharge(),request.ticketName(),request.priceUnit(),
                request.durationMinutes(),request.useCount(),request.passengerCount(),request.equipmentType(),
                request.equipmentIncluded(),request.mandatoryExtraCost(),request.residencyCondition(),
                request.rawPriceText(),request.sourceKind(),request.sourceUrl(),request.checkedAt(),
                request.validFrom(),request.validUntil());
    }

    @Transactional
    public void createFact(long memberId,long placeId,AdminFactDraftRequest request) {
        requirePlace(memberId,placeId);
        if(!SOURCES.contains(request.sourceKind()) || !validSource(request.sourceUrl()) ||
                !request.fieldKey().matches("[A-Za-z][A-Za-z0-9]{1,79}") ||
                (request.validFrom()!=null && request.validUntil()!=null && request.validFrom().isAfter(request.validUntil())))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"상세정보·출처·적용 기간을 확인해 주세요.");
        try { json.readTree(request.valueJson()); }
        catch (RuntimeException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"JSON 값이 올바르지 않습니다."); }
        jdbc.update("""
                INSERT INTO place_detail_fact(place_id,field_key,value_json,source_kind,source_url,
                  checked_at,valid_from,valid_until,verification_status)
                VALUES (?,?,?,?,?,?,?,?,'NEEDS_REVIEW')
                """,placeId,request.fieldKey(),request.valueJson(),request.sourceKind(),
                request.sourceUrl(),request.checkedAt(),request.validFrom(),request.validUntil());
    }

    @Transactional
    public void reviewPrice(long memberId, long placeId, long priceId, String status) {
        requirePlace(memberId, placeId);
        var rows = jdbc.query("""
                SELECT price_basis,amount_krw,free_of_charge,source_url,checked_at,
                       season_label,valid_from,valid_until
                FROM place_price WHERE place_id=? AND price_id=? FOR UPDATE
                """,(rs,n)->new Object[]{rs.getString(1),rs.getObject(2),rs.getBoolean(3),rs.getString(4),
                rs.getTimestamp(5),rs.getString(6),rs.getDate(7),rs.getDate(8)},placeId,priceId);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var row=rows.getFirst();
        if(status.equals("VERIFIED")) {
            if(!"NORMAL".equals(row[0]) || (row[1]==null && !Boolean.TRUE.equals(row[2])) ||
                    !validSource((String)row[3]) || row[4]==null ||
                    (row[5]!=null && (row[6]==null || row[7]==null)) ||
                    (row[6]!=null && row[7]!=null && ((java.sql.Date)row[6]).after((java.sql.Date)row[7])))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"정상가·금액·출처·확인일·시즌 적용기간을 확인해 주세요.");
        }
        jdbc.update("UPDATE place_price SET verification_status=?,verified_at=? WHERE place_id=? AND price_id=?",
                status,status.equals("VERIFIED")?LocalDateTime.now(placeClock):null,placeId,priceId);
    }

    @Transactional
    public void reviewFact(long memberId, long placeId, long factId, String status) {
        requirePlace(memberId, placeId);
        var rows=jdbc.query("""
                SELECT field_key,JSON_UNQUOTE(JSON_EXTRACT(value_json,'$.entryStatus')),source_kind,source_url,checked_at,valid_from,valid_until
                FROM place_detail_fact WHERE place_id=? AND fact_id=? FOR UPDATE
                """,(rs,n)->new Object[]{rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),
                rs.getTimestamp(5),rs.getDate(6),rs.getDate(7)},placeId,factId);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var row=rows.getFirst();
        if(status.equals("VERIFIED")) {
            if(!validSource((String)row[3]) || row[4]==null)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"출처 URL과 확인일이 필요합니다.");
            if("snorkelingSafety".equals(row[0]) && "ALLOWED".equals(row[1]) &&
                    (row[5]==null || row[6]==null ||
                    !("OFFICIAL_WEBSITE".equals(row[2]) || "OFFICIAL_PUBLIC_DATA".equals(row[2]))))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"입수 허용에는 공식 출처와 유효기간이 필요합니다.");
        }
        jdbc.update("UPDATE place_detail_fact SET verification_status=?,verified_at=? WHERE place_id=? AND fact_id=?",
                status,status.equals("VERIFIED")?LocalDateTime.now(placeClock):null,placeId,factId);
    }

    private boolean validSource(String text) {
        try { var uri=URI.create(text); return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost()!=null && uri.getUserInfo()==null; }
        catch (RuntimeException ex) { return false; }
    }
    private void requirePlace(long memberId,long placeId) {
        if(members.findActiveMember(memberId).getRole()!=MemberRole.ADMIN)throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if(!places.existsById(placeId))throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
