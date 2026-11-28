package com.outpick.domain.place.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class FisheryProtectionServiceTests {
    @Test @DisplayName("금어기 시작일과 종료일을 모두 포함한다")
    void inclusiveDates() {
        assertThat(FisheryProtectionService.inSeason(LocalDate.of(2026, 6, 1),6,1,8,31)).isTrue();
        assertThat(FisheryProtectionService.inSeason(LocalDate.of(2026, 8, 31),6,1,8,31)).isTrue();
        assertThat(FisheryProtectionService.inSeason(LocalDate.of(2026, 9, 1),6,1,8,31)).isFalse();
    }
    @Test @DisplayName("연도를 넘는 금어기는 다음 해 종료일까지 적용한다")
    void crossingYear() {
        assertThat(FisheryProtectionService.inSeason(LocalDate.of(2026, 11, 1),11,1,3,31)).isTrue();
        assertThat(FisheryProtectionService.inSeason(LocalDate.of(2027, 3, 31),11,1,3,31)).isTrue();
        assertThat(FisheryProtectionService.inSeason(LocalDate.of(2027, 4, 1),11,1,3,31)).isFalse();
    }
    @Test @DisplayName("금지체장은 경계값과 그 이하를 포함하며 미확인 수치는 계산하지 않는다")
    void minimumSizeBoundary() {
        assertThat(FisheryProtectionService.prohibitedSize(new BigDecimal("5.00"),new BigDecimal("5"))).isTrue();
        assertThat(FisheryProtectionService.prohibitedSize(new BigDecimal("5.01"),new BigDecimal("5"))).isFalse();
        assertThat(FisheryProtectionService.prohibitedSize(null,new BigDecimal("5"))).isFalse();
    }
    @Test @DisplayName("추자도는 제주 본섬 소라 금어기에서 제외하고 별도 규정에만 매칭한다")
    void excludedRegion() {
        String location="제주특별자치도 제주시 추자면 추자도";
        assertThat(FisheryProtectionService.regionMatches("제주","추자도",location)).isFalse();
        assertThat(FisheryProtectionService.regionMatches("제주 추자도",null,location)).isTrue();
    }
    @Test @DisplayName("좌표 검증 전 지정 해역은 주소만으로 자동 판정하지 않는다")
    void designatedSeaAreaNotGuessed() {
        assertThat(FisheryProtectionService.regionMatches("강원 고성군 현내면 지정 해역",null,"강원도 고성군 현내면")).isFalse();
    }
}
