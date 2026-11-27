package com.outpick.domain.place.controller;

import com.outpick.domain.place.dto.PlacePageResponse;
import com.outpick.domain.place.dto.PlaceDetailResponse;
import com.outpick.domain.place.dto.PlaceVerifiedDetailsResponse;
import com.outpick.domain.place.dto.PlaceFilterResponse;
import com.outpick.domain.place.dto.SnorkelingSafetyResponse;
import com.outpick.domain.place.dto.FisheryProtectionResponse;
import com.outpick.domain.place.service.FisheryProtectionService;
import com.outpick.domain.place.service.SnorkelingSafetyService;
import com.outpick.domain.place.dto.PlaceSearchRequest;
import com.outpick.domain.place.service.PlaceSearchService;
import com.outpick.domain.place.service.PlaceDetailService;
import com.outpick.domain.place.service.PlaceVerifiedDetailsService;
import com.outpick.domain.place.service.PlaceFilterService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/places")
@RequiredArgsConstructor
public class PlaceController {
    private final com.outpick.domain.place.service.PlaceResearchDetailsService researchDetailsService;
    private final PlaceSearchService searchService;
    private final PlaceDetailService detailService;
    private final PlaceFilterService filterService;
    private final PlaceVerifiedDetailsService verifiedDetailsService;
    private final SnorkelingSafetyService snorkelingSafetyService;
    private final FisheryProtectionService fisheryProtectionService;

    @GetMapping("/{placeId}/research-details")
    public java.util.List<com.outpick.domain.place.service.PlaceResearchDetailsService.Evidence> researchDetails(@PathVariable @Positive long placeId) {
        return researchDetailsService.get(placeId);
    }

    @GetMapping("/filters")
    @Operation(summary = "장소 검색 필터 선택지", description = "최상위 지역 ID와 분류별 종목 ID를 반환합니다.")
    public PlaceFilterResponse getFilters() {
        return filterService.getFilters();
    }

    @GetMapping
    @Operation(summary = "공개 장소 검색", description = "장소명·별칭, 지역, 종목·시즌·실내외 및 지도 범위로 공개 장소만 조회합니다.")
    public PlacePageResponse search(@Valid @ModelAttribute @ParameterObject PlaceSearchRequest request) {
        return searchService.search(request);
    }

    @GetMapping("/{placeId}")
    @Operation(summary = "공개 장소 상세", description = "공개 장소의 활동·시즌·확인된 출처·유효한 공개 공지를 조회합니다.")
    public PlaceDetailResponse getDetail(@PathVariable("placeId") @Positive(message = "장소 ID는 1 이상이어야 합니다.") long placeId) {
        return detailService.getDetail(placeId);
    }

    @GetMapping("/{placeId}/snorkeling-safety")
    @Operation(summary = "스노클링 안전정보", description = "확인된 입수 상태와 현장 위험 정보, 유형별 공통 주의사항을 구분합니다.")
    public SnorkelingSafetyResponse getSnorkelingSafety(@PathVariable("placeId") @Positive long placeId) {
        return snorkelingSafetyService.get(placeId);
    }

    @GetMapping("/{placeId}/fishery-protection")
    @Operation(summary = "수산자원 보호 규정", description = "검증된 금어기와 검수 대기 자료를 분리합니다. 채취 허용 여부는 판정하지 않습니다.")
    public FisheryProtectionResponse getFisheryProtection(@PathVariable("placeId") @Positive long placeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate visitDate) {
        return fisheryProtectionService.get(placeId, visitDate == null ? LocalDate.now(java.time.ZoneId.of("Asia/Seoul")) : visitDate);
    }

    @GetMapping("/{placeId}/verified-details")
    @Operation(summary = "검증된 장소 상세 데이터", description = "현재 유효한 공식 검증 정보와 정상 가격만 반환합니다.")
    public PlaceVerifiedDetailsResponse getVerifiedDetails(@PathVariable("placeId") @Positive long placeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate visitDate) {
        return visitDate == null ? verifiedDetailsService.get(placeId) : verifiedDetailsService.get(placeId, visitDate);
    }
}
