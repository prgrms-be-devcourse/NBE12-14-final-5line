package com.outpick.domain.place;

import com.outpick.domain.place.repository.PlaceDetailRepository;
import com.outpick.domain.place.service.PlaceDetailService;
import com.outpick.domain.place.service.PlaceCanonicalResolver;
import com.outpick.domain.place.service.PlaceVerifiedDetailsService;
import com.outpick.global.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class PlaceDetailServiceTests {
    @Test
    void unavailablePlaceDoesNotLoadAnyRelatedInformation() {
        var repository = mock(PlaceDetailRepository.class);
        var canonicalResolver = mock(PlaceCanonicalResolver.class);
        when(canonicalResolver.resolve(42)).thenReturn(42L);
        when(repository.findPublishedPlace(42)).thenReturn(Optional.empty());
        var service = new PlaceDetailService(repository, Clock.systemUTC(), canonicalResolver,
                mock(PlaceVerifiedDetailsService.class));
        assertThatThrownBy(() -> service.getDetail(42)).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode().code()).isEqualTo("PLACE_NOT_FOUND"));
        verify(repository).findPublishedPlace(42);
        verifyNoMoreInteractions(repository);
    }
}
