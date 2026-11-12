package com.outpick.global.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 환경별 CORS 허용 출처입니다. 설정이 없으면 허용 출처를 비워 로컬 정책이 다른 환경으로 이어지지 않게 합니다. */
@ConfigurationProperties("app.cors")
public record CorsProperties(List<String> allowedOrigins) {

    /** 설정에서 읽은 출처 목록을 보관하고, 누락된 경우 빈 목록으로 초기화합니다. */
    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
