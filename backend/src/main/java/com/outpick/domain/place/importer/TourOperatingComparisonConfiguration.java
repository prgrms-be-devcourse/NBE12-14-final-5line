package com.outpick.domain.place.importer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "outpick.tour-operating.enabled", havingValue = "true")
@EnableScheduling
@Import({TourOperatingChangeReader.class, TourOperatingCandidateStore.class})
class TourOperatingComparisonConfiguration {
    @Bean(defaultCandidate = false)
    ThreadPoolTaskScheduler tourOperatingTaskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("tour-operating-");
        return scheduler;
    }

    @Bean
    TourOperatingComparisonScheduler tourOperatingComparisonScheduler(
            TourOperatingChangeReader reader, TourOperatingCandidateStore store,
            @Value("${TOUR_API_KEY:}") String apiKey,
            @Value("${outpick.tour-operating.max-places}") int maxPlaces) {
        return new TourOperatingComparisonScheduler(reader, store,
                new TourApiOperatingClient(new TourApiHttpTransport(apiKey)), maxPlaces);
    }
}
