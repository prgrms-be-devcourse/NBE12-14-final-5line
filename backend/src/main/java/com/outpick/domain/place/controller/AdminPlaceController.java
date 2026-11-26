package com.outpick.domain.place.controller;

import com.outpick.domain.member.security.MemberPrincipal;
import com.outpick.domain.place.dto.AdminPlaceRequest;
import com.outpick.domain.place.dto.AdminPlaceResponse;
import com.outpick.domain.place.service.AdminPlaceService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminPlaceController {
    private final AdminPlaceService service;

    @GetMapping("/dashboard")
    public Map<String, Long> dashboard(Authentication authentication) {
        return service.summary(memberId(authentication));
    }

    @GetMapping("/places")
    public List<AdminPlaceResponse> places(Authentication authentication) {
        return service.recentPlaces(memberId(authentication));
    }

    @PostMapping("/places")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminPlaceResponse create(@Valid @RequestBody AdminPlaceRequest request, Authentication authentication) {
        return service.createDraft(memberId(authentication), request);
    }

    private long memberId(Authentication authentication) {
        return ((MemberPrincipal) authentication.getPrincipal()).memberId();
    }
}
