package com.outpick.global.config;

import org.jspecify.annotations.NonNull;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 도메인 REST API의 공통 경로와 환경별 CORS 정책을 설정합니다. 인증·권한 검사는 담당하지 않습니다. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CorsProperties.class)
public class WebConfig implements WebMvcConfigurer {

    public static final String API_PREFIX = "/api/v1";

    private final CorsProperties corsProperties;

    /** 환경 설정에 바인딩한 CORS 허용 출처를 주입받습니다. */
    public WebConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    /** 도메인의 REST 컨트롤러에만 접두사를 붙여 Swagger·Actuator 등 프레임워크 경로를 유지합니다. */
    @Override
    public void configurePathMatch(@NonNull PathMatchConfigurer configurer) {
        configurer.addPathPrefix(API_PREFIX,
                HandlerTypePredicate.forBasePackage("com.outpick.domain")
                        .and(HandlerTypePredicate.forAnnotation(RestController.class)));
    }

    /** 등록한 출처에서 도메인 API를 호출할 때만 쿠키 전송과 필요한 요청 메서드·헤더를 허용합니다. */
    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        registry.addMapping(API_PREFIX + "/**")
                .allowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "Authorization")
                .allowCredentials(true);
    }
}
