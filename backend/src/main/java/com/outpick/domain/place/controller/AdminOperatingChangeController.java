package com.outpick.domain.place.controller;

import com.outpick.domain.member.security.MemberSessionPrincipal;
import com.outpick.domain.place.dto.AdminOperatingChangePageResponse;
import com.outpick.domain.place.dto.AdminOperatingChangeResolutionRequest;
import com.outpick.domain.place.service.AdminOperatingChangeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/admin/place-operating-changes")
@RequiredArgsConstructor
public class AdminOperatingChangeController {
    private final AdminOperatingChangeService service;

    @GetMapping
    public AdminOperatingChangePageResponse pending(
            @RequestParam(defaultValue = "0") @Min(0) @Max(1_000_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            @AuthenticationPrincipal MemberSessionPrincipal principal) {
        if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        return service.pending(principal.memberId(), page, size);
    }

    @PatchMapping("/{candidateId}/resolution")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resolve(@PathVariable @Min(1) long candidateId,
                        @Valid @RequestBody AdminOperatingChangeResolutionRequest request,
                        @AuthenticationPrincipal MemberSessionPrincipal principal) {
        if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        service.resolve(principal.memberId(), candidateId, request);
    }
}
