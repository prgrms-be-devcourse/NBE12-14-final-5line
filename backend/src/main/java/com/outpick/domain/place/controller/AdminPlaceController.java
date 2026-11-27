package com.outpick.domain.place.controller;

import com.outpick.domain.member.security.MemberPrincipal;
import com.outpick.domain.place.dto.AdminPlaceDetailResponse;
import com.outpick.domain.place.dto.AdminPlaceCandidateResponse;
import com.outpick.domain.place.dto.AdminPlaceUpdateRequest;
import com.outpick.domain.place.dto.AdminEvidenceResponse;
import com.outpick.domain.place.dto.AdminEvidenceReviewRequest;
import com.outpick.domain.place.dto.AdminPriceDraftRequest;
import com.outpick.domain.place.dto.AdminFactDraftRequest;
import com.outpick.domain.place.service.AdminEvidenceService;
import com.outpick.domain.place.dto.AdminPlaceVisibilityRequest;
import com.outpick.domain.place.dto.AdminPlaceNoticePageResponse;
import com.outpick.domain.place.dto.AdminPlaceNoticeRequest;
import com.outpick.domain.place.dto.AdminPlaceNoticeResponse;
import com.outpick.domain.place.dto.AdminPlaceNoticeVisibilityRequest;
import com.outpick.domain.place.dto.AdminPlacePageResponse;
import com.outpick.domain.place.dto.AdminPlaceRequest;
import com.outpick.domain.place.dto.AdminPlaceResponse;
import com.outpick.domain.place.entity.PlacePublishStatus;
import com.outpick.domain.place.service.AdminPlaceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminPlaceController {
    private final AdminPlaceService service;
    private final AdminEvidenceService evidence;

    @GetMapping("/dashboard")
    public Map<String, Long> dashboard(Authentication authentication) {
        return service.summary(memberId(authentication));
    }

    /** 기존 장소 관리 화면의 매개변수 없는 요청에는 최근 등록 목록을 배열로 반환합니다. */
    @GetMapping(path = "/places", params = {"!query", "!status", "!page", "!size"})
    public List<AdminPlaceResponse> recentPlaces(Authentication authentication) {
        return service.recentPlaces(memberId(authentication));
    }

    @GetMapping("/places")
    public AdminPlacePageResponse places(
            @RequestParam(required = false) @Size(max = 100) String query,
            @RequestParam(required = false) PlacePublishStatus status,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1_000_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            Authentication authentication) {
        return service.places(memberId(authentication), query, status, page, size);
    }

    @GetMapping("/place-candidates/snorkeling")
    public AdminPlaceCandidateResponse snorkelingCandidates(
            @RequestParam(defaultValue = "0") @Min(0) @Max(1_000_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            Authentication authentication) {
        return service.snorkelingCandidates(memberId(authentication), page, size);
    }

    @GetMapping("/places/{placeId}")
    public AdminPlaceDetailResponse place(@PathVariable @Positive long placeId, Authentication authentication) {
        return service.place(memberId(authentication), placeId);
    }

    @PostMapping("/places")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminPlaceResponse create(@Valid @RequestBody AdminPlaceRequest request, Authentication authentication) {
        return service.createDraft(memberId(authentication), request);
    }

    @PatchMapping("/places/{placeId}")
    public AdminPlaceResponse updatePlace(@PathVariable @Positive long placeId,
            @Valid @RequestBody AdminPlaceUpdateRequest request, Authentication authentication) {
        return service.updatePlace(memberId(authentication), placeId, request);
    }

    @PatchMapping("/places/{placeId}/visibility")
    public AdminPlaceResponse changePlaceVisibility(@PathVariable @Positive long placeId,
            @Valid @RequestBody AdminPlaceVisibilityRequest request, Authentication authentication) {
        return service.changePlaceVisibility(memberId(authentication), placeId, request.status());
    }

    @GetMapping("/places/{placeId}/evidence")
    public AdminEvidenceResponse evidence(@PathVariable @Positive long placeId, Authentication authentication) {
        return evidence.get(memberId(authentication), placeId);
    }

    @PostMapping("/places/{placeId}/prices")
    @ResponseStatus(HttpStatus.CREATED)
    public void createPrice(@PathVariable @Positive long placeId,
            @Valid @RequestBody AdminPriceDraftRequest request, Authentication authentication) {
        evidence.createPrice(memberId(authentication), placeId, request);
    }

    @PostMapping("/places/{placeId}/facts")
    @ResponseStatus(HttpStatus.CREATED)
    public void createFact(@PathVariable @Positive long placeId,
            @Valid @RequestBody AdminFactDraftRequest request, Authentication authentication) {
        evidence.createFact(memberId(authentication), placeId, request);
    }

    @PatchMapping("/places/{placeId}/prices/{priceId}/review")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reviewPrice(@PathVariable @Positive long placeId, @PathVariable @Positive long priceId,
            @Valid @RequestBody AdminEvidenceReviewRequest request, Authentication authentication) {
        evidence.reviewPrice(memberId(authentication), placeId, priceId, request.status());
    }

    @PatchMapping("/places/{placeId}/facts/{factId}/review")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reviewFact(@PathVariable @Positive long placeId, @PathVariable @Positive long factId,
            @Valid @RequestBody AdminEvidenceReviewRequest request, Authentication authentication) {
        evidence.reviewFact(memberId(authentication), placeId, factId, request.status());
    }

    @GetMapping("/places/{placeId}/notices")
    public AdminPlaceNoticePageResponse notices(
            @PathVariable @Positive long placeId,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1_000_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            Authentication authentication) {
        return service.notices(memberId(authentication), placeId, page, size);
    }

    @PostMapping("/places/{placeId}/notices")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminPlaceNoticeResponse createNotice(
            @PathVariable @Positive long placeId,
            @RequestParam(defaultValue = "false") boolean publish,
            @RequestParam(required = false) UUID publicationRequestId,
            @Valid @RequestBody AdminPlaceNoticeRequest request, Authentication authentication) {
        return service.createNotice(memberId(authentication), placeId, request, publish, publicationRequestId);
    }

    @PatchMapping("/places/{placeId}/notices/{noticeId}")
    public AdminPlaceNoticeResponse updateNotice(
            @PathVariable @Positive long placeId, @PathVariable @Positive long noticeId,
            @RequestParam(defaultValue = "false") boolean publish,
            @Valid @RequestBody AdminPlaceNoticeRequest request, Authentication authentication) {
        return service.updateNotice(memberId(authentication), placeId, noticeId, request, publish);
    }

    @PatchMapping("/places/{placeId}/notices/{noticeId}/visibility")
    public AdminPlaceNoticeResponse changeNoticeVisibility(
            @PathVariable @Positive long placeId, @PathVariable @Positive long noticeId,
            @Valid @RequestBody AdminPlaceNoticeVisibilityRequest request, Authentication authentication) {
        return service.changeNoticeVisibility(memberId(authentication), placeId, noticeId, request.visible());
    }

    private long memberId(Authentication authentication) {
        return ((MemberPrincipal) authentication.getPrincipal()).memberId();
    }
}
