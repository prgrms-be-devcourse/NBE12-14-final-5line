package com.outpick.domain.place;

import com.outpick.domain.place.entity.Place;
import com.outpick.domain.place.entity.ActivityCategory;
import com.outpick.domain.place.entity.ActivityType;
import com.outpick.domain.place.entity.PlaceActivity;
import com.outpick.domain.place.entity.PlacePublishStatus;
import com.outpick.domain.place.entity.Region;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class PlaceVerifiedDetailsApiTests {
    @Container @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4.13");

    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @Test
    @DisplayName("검수 전·기간 만료·과거 시즌 가격을 공개 상세 데이터에서 제외한다")
    void returnsOnlyVerifiedAndCurrentlyValidData() throws Exception {
        Region region = Region.builder().name("테스트 지역").build();
        em.persist(region);
        Place place = Place.builder().region(region).name("검증 장소")
                .publishStatus(PlacePublishStatus.PUBLISHED).build();
        em.persist(place);
        allowPublic(place);
        em.flush();

        LocalDate today = LocalDate.now();
        LocalDateTime checked = LocalDateTime.now().minusDays(1);
        jdbc.update("""
                INSERT INTO place_detail_fact(place_id,field_key,value_json,source_kind,source_url,checked_at,
                  verified_at,verification_status,valid_from,valid_until)
                VALUES(?,?,?,?,?, ?,?,'VERIFIED',?,?)
                """, place.getId(),"operatingHours","{\"text\":\"10:00~18:00\"}","OFFICIAL_WEBSITE",
                "https://example.com",checked,checked,today.minusDays(1),today.plusDays(1));
        jdbc.update("""
                INSERT INTO place_detail_fact(place_id,field_key,value_json,source_kind,verification_status)
                VALUES(?,?,?,?, 'NEEDS_REVIEW')
                """, place.getId(),"restDays","{\"text\":\"미확인\"}","TOUR_API");
        jdbc.update("""
                INSERT INTO place_price(place_id,price_kind,age_band,amount_krw,source_kind,
                  verified_at,verification_status,valid_from,valid_until)
                VALUES(?,?,?,?,?, ?,'VERIFIED',?,?)
                """,place.getId(),"ADMISSION","ADULT",5000,"OFFICIAL_WEBSITE",checked,today.minusDays(1),today.plusDays(1));
        jdbc.update("""
                INSERT INTO place_price(place_id,price_kind,age_band,amount_krw,source_kind,
                  verified_at,verification_status,valid_from,valid_until)
                VALUES(?,?,?,?,?, ?,'VERIFIED',?,?)
                """,place.getId(),"ADMISSION","ADULT",4500,"TOUR_API",checked,today.minusDays(1),today.plusDays(1));
        jdbc.update("""
                INSERT INTO place_price(place_id,price_kind,age_band,amount_krw,source_kind,
                  verified_at,verification_status,season_label,valid_from,valid_until)
                VALUES(?,?,?,?,?, ?,'VERIFIED',?,?,?)
                """,place.getId(),"ADMISSION","ADULT",3000,"OFFICIAL_WEBSITE",checked,
                "2025 WINTER",today.minusYears(1),today.minusDays(1));
        jdbc.update("""
                INSERT INTO place_price(place_id,price_kind,age_band,amount_krw,source_kind,verification_status)
                VALUES(?,?,?,?,?,'NEEDS_REVIEW')
                """,place.getId(),"RENTAL","ADULT",0,"TOUR_API");
        jdbc.update("""
                INSERT INTO place_price(place_id,price_kind,price_basis,amount_krw,source_kind,
                  verified_at,verification_status,valid_from,valid_until)
                VALUES(?,?,'DISCOUNT',?,?, ?,'VERIFIED',?,?)
                """,place.getId(),"ADMISSION",1000,"OFFICIAL_WEBSITE",checked,today.minusDays(1),today.plusDays(1));
        jdbc.update("""
                INSERT INTO place_tag(place_id,tag,source_kind,verified_at,verification_status)
                VALUES(?,?,?,?,'VERIFIED')
                """,place.getId(),"실내","OFFICIAL_WEBSITE",checked);
        jdbc.update("""
                INSERT INTO place_tag(place_id,tag,source_kind,verification_status)
                VALUES(?,?,?,'NEEDS_REVIEW')
                """,place.getId(),"무료","TOUR_API");

        mvc.perform(get("/api/v1/places/{id}/verified-details",place.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.facts",hasSize(1)))
                .andExpect(jsonPath("$.facts[0].value.text").value("10:00~18:00"))
                .andExpect(jsonPath("$.operating.operatingHours.text").value("10:00~18:00"))
                .andExpect(jsonPath("$.tags",hasSize(1)))
                .andExpect(jsonPath("$.tags[0]").value("실내"))
                .andExpect(jsonPath("$.prices",hasSize(1)))
                .andExpect(jsonPath("$.prices[0].amountKrw").value(5000));
        mvc.perform(get("/api/v1/places/{id}",place.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verifiedDetails.facts[0].fieldKey").value("operatingHours"))
                .andExpect(jsonPath("$.verifiedDetails.tags[0]").value("실내"))
                .andExpect(jsonPath("$.verifiedDetails.prices[0].amountKrw").value(5000));
        mvc.perform(get("/api/v1/places/{id}/verified-details",place.getId())
                        .param("visitDate",today.plusDays(30).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.prices",hasSize(0)));
    }

    @Test
    @DisplayName("검수 전 장소의 상세 데이터는 공개하지 않는다")
    void rejectsDraftPlace() throws Exception {
        Region region = Region.builder().name("테스트 지역").build();
        em.persist(region);
        Place draft = Place.builder().region(region).name("검수 전").build();
        em.persist(draft);
        em.flush();
        mvc.perform(get("/api/v1/places/{id}/verified-details",draft.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("중복 장소의 이전 ID 조회는 보존한 대표 장소 ID로 연결한다")
    void resolvesVerifiedDuplicateAlias() throws Exception {
        Region region = Region.builder().name("테스트 지역").build();
        em.persist(region);
        Place canonical = Place.builder().region(region).name("대표 장소")
                .publishStatus(PlacePublishStatus.PUBLISHED).build();
        Place duplicate = Place.builder().region(region).name("중복 장소")
                .publishStatus(PlacePublishStatus.HIDDEN).build();
        em.persist(canonical);
        em.persist(duplicate);
        allowPublic(canonical);
        em.flush();
        jdbc.update("""
                INSERT INTO place_duplicate_alias(duplicate_place_id,canonical_place_id,evidence,verified_at)
                VALUES(?,?,'{\"verified\":true}',CURRENT_TIMESTAMP(6))
                """,duplicate.getId(),canonical.getId());
        mvc.perform(get("/api/v1/places/{id}",duplicate.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(canonical.getId()));
        mvc.perform(get("/api/v1/places/{id}/verified-details",duplicate.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placeId").value(canonical.getId()));
    }

    @Test
    @DisplayName("탑승 인원·횟수·시간·장비별 정상 가격을 구분하고 할인·지난 시즌·미확인은 제외한다")
    void keepsDistinctPriceDimensions() throws Exception {
        Region region = Region.builder().name("가격 테스트 지역").build();
        em.persist(region);
        Place place = Place.builder().region(region).name("가격 테스트 장소")
                .publishStatus(PlacePublishStatus.PUBLISHED).build();
        em.persist(place);
        allowPublic(place);
        em.flush();
        LocalDate today=LocalDate.now();
        LocalDateTime verified=LocalDateTime.now().minusHours(1);
        String sql="""
                INSERT INTO place_price(place_id,price_kind,price_basis,ticket_name,price_type,
                  amount_krw,free_of_charge,price_unit,duration_minutes,use_count,passenger_count,
                  equipment_type,source_kind,verified_at,verification_status,valid_from,valid_until)
                VALUES(? ,? ,? ,? ,? ,? ,? ,? ,? ,? ,? ,? ,? ,? ,'VERIFIED',? ,?)
                """;
        Object[][] prices={
          {"RAILBIKE","NORMAL","2인승","RIDE",30000,false,"VEHICLE",null,null,2,null},
          {"RAILBIKE","NORMAL","4인승","RIDE",40000,false,"VEHICLE",null,null,4,null},
          {"LUGE","NORMAL","1회권","RIDE",12000,false,"PERSON",null,1,null,null},
          {"LUGE","NORMAL","3회권","RIDE",30000,false,"PERSON",null,3,null,null},
          {"LIFT","NORMAL","4시간권","ADMISSION",50000,false,"PERSON",240,null,null,null},
          {"LIFT","NORMAL","8시간권","ADMISSION",80000,false,"PERSON",480,null,null,null},
          {"WATERPARK","NORMAL","종일권","ADMISSION",60000,false,"PERSON",null,null,null,null},
          {"WATERPARK","NORMAL","오후권","ADMISSION",40000,false,"PERSON",null,null,null,null},
          {"RENTAL","NORMAL","스키 대여","RENTAL",20000,false,"EQUIPMENT",null,null,null,"SKIS"},
          {"RENTAL","NORMAL","헬멧 대여","RENTAL",5000,false,"EQUIPMENT",null,null,null,"HELMET"},
          {"BEACH","NORMAL","입장료","ADMISSION",0,true,"PERSON",null,null,null,null}
        };
        for(Object[] row:prices)jdbc.update(sql,place.getId(),row[0],row[1],row[2],row[3],row[4],row[5],
          row[6],row[7],row[8],row[9],row[10],"OFFICIAL_WEBSITE",verified,today.minusDays(1),today.plusDays(1));
        jdbc.update(sql,place.getId(),"LIFT","DISCOUNT","4시간권","ADMISSION",10000,false,
          "PERSON",240,null,null,null,"OFFICIAL_WEBSITE",verified,today.minusDays(1),today.plusDays(1));
        jdbc.update(sql,place.getId(),"LIFT","NORMAL","지난 시즌","ADMISSION",30000,false,
          "PERSON",240,null,null,null,"OFFICIAL_WEBSITE",verified,today.minusYears(1),today.minusDays(1));
        jdbc.update("""
          INSERT INTO place_price(place_id,price_kind,price_basis,ticket_name,source_kind,verification_status)
          VALUES(?,'RENTAL','NORMAL','미확인','TOUR_API','NEEDS_REVIEW')
          """,place.getId());
        mvc.perform(get("/api/v1/places/{id}/verified-details",place.getId()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.prices",hasSize(11)))
            .andExpect(jsonPath("$.prices[?(@.passengerCount == 2)]",hasSize(1)))
            .andExpect(jsonPath("$.prices[?(@.passengerCount == 4)]",hasSize(1)))
            .andExpect(jsonPath("$.prices[?(@.useCount == 1)]",hasSize(1)))
            .andExpect(jsonPath("$.prices[?(@.useCount == 3)]",hasSize(1)))
            .andExpect(jsonPath("$.prices[?(@.durationMinutes == 240)]",hasSize(1)))
            .andExpect(jsonPath("$.prices[?(@.durationMinutes == 480)]",hasSize(1)))
            .andExpect(jsonPath("$.prices[?(@.equipmentType == 'SKIS')]",hasSize(1)))
            .andExpect(jsonPath("$.prices[?(@.equipmentType == 'HELMET')]",hasSize(1)))
            .andExpect(jsonPath("$.prices[?(@.freeOfCharge == true)]",hasSize(1)));
    }
    private void allowPublic(Place place) {
        var category = ActivityCategory.builder().name("스키").build();
        em.persist(category);
        var type = ActivityType.builder().category(category).name("스키").build();
        em.persist(type);
        em.persist(PlaceActivity.builder().place(place).activityType(type).environmentType("OUTDOOR").build());
    }
}
