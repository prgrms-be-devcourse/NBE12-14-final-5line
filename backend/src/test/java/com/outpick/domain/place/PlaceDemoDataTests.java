package com.outpick.domain.place;

import com.outpick.domain.place.config.PlaceDemoData;
import com.outpick.domain.place.repository.PlaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureMockMvc
@ActiveProfiles({"local", "demo"})
@Testcontainers
class PlaceDemoDataTests {
    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4.13");

    @Autowired PlaceDemoData demoData;
    @Autowired PlaceRepository places;
    @Autowired MockMvc mvc;

    @Test
    void seedsOnlyOnceAndProvidesRealFilterIds() throws Exception {
        assertThat(places.count()).isEqualTo(6);
        assertThat(places.findAll()).allSatisfy(place -> assertThat(place.getName()).startsWith("[데모]"));
        demoData.run(new DefaultApplicationArguments());
        assertThat(places.count()).isEqualTo(6);
        mvc.perform(get("/api/v1/places/filters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regions[*].name", containsInAnyOrder("강원", "경기")))
                .andExpect(jsonPath("$.categories[*].name", containsInAnyOrder("아이스스케이팅", "해수욕장", "스노클링", "워터파크", "루지·레일바이크·중력 레이싱", "스키", "눈썰매")))
                .andExpect(jsonPath("$.categories[?(@.name == '스키')].activityTypeIds[0]").exists());
        mvc.perform(get("/api/v1/places"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(4));
    }
}
