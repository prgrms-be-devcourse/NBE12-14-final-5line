package com.outpick.domain.place.importer;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** 외부 프로필을 보존한다. 별도 지정이 없을 때의 기본값은 application.yaml이 결정한다. */
final class TourImportApplication {
    private TourImportApplication() { }

    static SpringApplicationBuilder builder(Class<?> source) {
        return new SpringApplicationBuilder(source).web(WebApplicationType.NONE);
    }
}
