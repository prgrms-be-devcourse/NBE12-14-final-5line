package com.outpick.domain.place.importer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.web.context.WebApplicationContext;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;

class TourImportApplicationTests {
    @Test
    @DisplayName("프로필을 지정하지 않으면 기존 설정 파일의 기본 로컬 프로필을 사용하고 웹 서버는 열지 않는다")
    void usesConfiguredDefaultWithoutStartingWebServer() {
        try (var context = start(Map.of())) {
            assertThat(context.getEnvironment().getDefaultProfiles()).containsExactly("local");
            assertThat(context.getBean(AtomicInteger.class).get()).isEqualTo(1);
            assertThat(context).isNotInstanceOf(WebApplicationContext.class);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "local,prod", "local"})
    @DisplayName("외부 활성 프로필을 보존하고 운영 프로필이면 로컬 전용 초기화가 실행되지 않는다")
    void respectsExternalProfilesAndLocalOnlyRunner(String profiles) {
        try (var context = start(Map.of("SPRING_PROFILES_ACTIVE", profiles))) {
            assertThat(context.getEnvironment().getActiveProfiles()).containsExactly(profiles.split(","));
            boolean prod = Arrays.asList(profiles.split(",")).contains("prod");
            assertThat(context.containsBean("localOnlyInitializer")).isEqualTo(!prod);
            assertThat(context.getBean(AtomicInteger.class).get()).isEqualTo(prod ? 0 : 1);
        }
    }

    private ConfigurableApplicationContext start(Map<String, Object> variables) {
        // 실제 프로세스의 키/DB/프로필을 읽지 않는 독립 환경에서 환경변수 매핑을 검증한다.
        var environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource("test-environment", variables));
        return TourImportApplication.builder(ProfileProbe.class).environment(environment)
                .properties("spring.main.banner-mode=off", "logging.level.root=OFF").run();
    }

    @Configuration(proxyBeanMethods = false)
    static class ProfileProbe {
        @Bean AtomicInteger initializerCalls() { return new AtomicInteger(); }

        @Bean @Profile("local & !prod")
        ApplicationRunner localOnlyInitializer(AtomicInteger initializerCalls) {
            return args -> initializerCalls.incrementAndGet();
        }
    }
}
