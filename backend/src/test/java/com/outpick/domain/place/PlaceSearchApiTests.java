package com.outpick.domain.place;

import com.outpick.domain.place.entity.ActivityCategory;
import com.outpick.domain.place.entity.ActivityType;
import com.outpick.domain.place.entity.Place;
import com.outpick.domain.place.entity.PlaceActivity;
import com.outpick.domain.place.entity.PlaceActivitySeason;
import com.outpick.domain.place.entity.PlacePublishStatus;
import com.outpick.domain.place.entity.Region;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class PlaceSearchApiTests {
    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4.13");

    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    private Region province;
    private Region district;
    private ActivityType ski;
    private ActivityType board;
    private ActivityType surf;
    private Place first;
    private Place second;
    private Place literal;

    @BeforeEach
    void fixtures() {
        province = persist(Region.builder().name("강원").build());
        district = persist(Region.builder().name("평창").parent(province).build());
        var otherRegion = persist(Region.builder().name("경기").build());
        var category = persist(ActivityCategory.builder().name("스키").build());
        ski = persist(ActivityType.builder().category(category).name("스키").build());
        var sledCategory = persist(ActivityCategory.builder().name("눈썰매").build());
        var waterCategory = persist(ActivityCategory.builder().name("워터파크").build());
        var iceCategory = persist(ActivityCategory.builder().name("아이스스케이팅").build());
        board = persist(ActivityType.builder().category(sledCategory).name("눈썰매").build());
        surf = persist(ActivityType.builder().category(waterCategory).name("워터파크").build());
        var climb = persist(ActivityType.builder().category(iceCategory).name("아이스스케이팅").build());
        first = place("동일이름", district, "37.37", "128.39", PlacePublishStatus.PUBLISHED);
        activity(first, ski, "OUTDOOR", "WINTER");
        activity(first, board, "OUTDOOR", "WINTER");
        second = place("동일이름", district, "37.38", "128.66", PlacePublishStatus.PUBLISHED);
        activity(second, surf, "OUTDOOR", "SUMMER");
        activity(second, climb, "INDOOR", "WINTER");
        literal = persist(Place.builder().region(province).name("100%_샘플").nameAlias("별칭 검색\\자료")
                .latitude(new BigDecimal("38")).longitude(new BigDecimal("129"))
                .publishStatus(PlacePublishStatus.PUBLISHED).build());
        activity(literal, ski, "OUTDOOR", "WINTER");
        activity(place("경기 장소", otherRegion, null, null, PlacePublishStatus.PUBLISHED), climb, "OUTDOOR", "SUMMER");
        place("검수 전 장소", district, "37.37", "128.39", PlacePublishStatus.DRAFT);
        place("숨긴 장소", district, "37.37", "128.39", PlacePublishStatus.HIDDEN);
        em.flush();
        em.clear();
    }

    @Test
    void returnsOnlyPublishedPlacesWithoutAuthenticationOrDataWrapper() throws Exception {
        mvc.perform(get("/api/v1/places"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(4)))
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.items[*].publishStatus").doesNotExist())
                .andExpect(jsonPath("$.items[*].version").doesNotExist());
    }

    @Test
    void swaggerDocumentsOnlyPublicSearchParameters() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/places'].get.parameters[*].name", containsInAnyOrder(
                        "query", "regionId", "activityTypeIds", "season", "environmentType",
                        "south", "west", "north", "east", "page", "size", "sort")));
    }

    @Test
    void appliesActivitySeasonAndEnvironmentToTheSameActivity() throws Exception {
        mvc.perform(get("/api/v1/places").param("activityTypeIds", surf.getId().toString())
                        .param("season", "WINTER").param("environmentType", "INDOOR"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/places").param("activityTypeIds", surf.getId().toString())
                        .param("season", "SUMMER").param("environmentType", "OUTDOOR"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(second.getId()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void multipleMatchingActivitiesDoNotDuplicatePlacesOrCount() throws Exception {
        mvc.perform(get("/api/v1/places").param("activityTypeIds", ski.getId().toString(), board.getId().toString())
                        .param("season", "WINTER").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void regionMatchesItselfAndImmediateChildren() throws Exception {
        mvc.perform(get("/api/v1/places").param("regionId", province.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get("/api/v1/places").param("regionId", district.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void includesMapBoundaryAndCombinesItWithOtherFilters() throws Exception {
        mvc.perform(get("/api/v1/places").param("south", "37.37").param("west", "128.39")
                        .param("north", "37.38").param("east", "128.66"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[*].id", containsInAnyOrder(first.getId().intValue(), second.getId().intValue())));
        mvc.perform(get("/api/v1/places").param("south", "37.37").param("west", "128.39")
                        .param("north", "37.38").param("east", "128.66")
                        .param("activityTypeIds", ski.getId().toString()).param("season", "WINTER"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(first.getId()));
    }

    @Test
    void literalWildcardsAndAliasesAreSearchable() throws Exception {
        for (String query : new String[]{"%_", "별칭", "  100%_  ", "\\"}) {
            mvc.perform(get("/api/v1/places").param("query", query))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.items[0].id").value(literal.getId()));
        }
    }

    @Test
    void tieBreaksEqualNamesByIdAcrossPagesAndKeepsCountOnEmptyPage() throws Exception {
        mvc.perform(get("/api/v1/places").param("query", "동일이름").param("size", "1").param("page", "0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(first.getId()))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/places").param("query", "동일이름").param("size", "1").param("page", "1").param("sort", "name-desc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(second.getId()))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/places").param("query", "동일이름").param("size", "1").param("page", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void noMatchIsAnEmptySuccessfulPage() throws Exception {
        mvc.perform(get("/api/v1/places").param("query", "없는장소"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void sortsNamesInBothDirectionsWithStableIdTieBreak() throws Exception {
        mvc.perform(get("/api/v1/places").param("regionId", province.getId().toString()).param("sort", "name"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(literal.getId()));
        mvc.perform(get("/api/v1/places").param("regionId", province.getId().toString()).param("sort", "name-desc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(first.getId()))
                .andExpect(jsonPath("$.items[1].id").value(second.getId()));
    }

    @Test
    void pointBoundsIncludeAnExactlyMatchingCoordinate() throws Exception {
        mvc.perform(get("/api/v1/places").param("south", "37.37").param("north", "37.37")
                        .param("west", "128.39").param("east", "128.39"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(first.getId()));
    }

    @Test
    void rejectsMalformedNumbersAndOversizedQuery() throws Exception {
        mvc.perform(get("/api/v1/places").param("page", "oops"))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        mvc.perform(get("/api/v1/places").param("activityTypeIds", "oops"))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        mvc.perform(get("/api/v1/places").param("query", "가".repeat(101)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"?page=-1", "?page=1000001", "?size=0", "?size=51", "?regionId=0",
            "?activityTypeIds=-1", "?sort=invalid", "?south=37", "?south=38&west=128&north=37&east=129",
            "?south=37&west=129&north=38&east=128", "?south=-91&west=128&north=38&east=129"})
    void invalidInputsUseCommonValidationProblem(String query) throws Exception {
        var result = mvc.perform(get("/api/v1/places" + query))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty())
                .andExpect(jsonPath("$.requestId").isNotEmpty());
        String requestId = result.andReturn().getResponse().getHeader("X-Request-Id");
        result.andExpect(jsonPath("$.requestId").value(requestId));
    }

    private <T> T persist(T entity) {
        em.persist(entity);
        return entity;
    }

    private Place place(String name, Region region, String lat, String lng, PlacePublishStatus status) {
        return persist(Place.builder().region(region).name(name).publishStatus(status)
                .latitude(lat == null ? null : new BigDecimal(lat)).longitude(lng == null ? null : new BigDecimal(lng)).build());
    }

    private void activity(Place place, ActivityType type, String environment, String season) {
        var activity = persist(PlaceActivity.builder().place(place).activityType(type).environmentType(environment).build());
        persist(PlaceActivitySeason.builder().placeActivity(activity).season(season).build());
    }
}
