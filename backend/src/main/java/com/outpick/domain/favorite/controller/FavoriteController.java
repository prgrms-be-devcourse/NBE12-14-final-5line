package com.outpick.domain.favorite.controller;

import com.outpick.domain.favorite.dto.FavoriteActionResponse;
import com.outpick.domain.favorite.dto.FavoriteCreateRequest;
import com.outpick.domain.favorite.dto.FavoriteNotificationRequest;
import com.outpick.domain.favorite.dto.FavoriteNotificationResponse;
import com.outpick.domain.favorite.dto.FavoritePlaceResponse;
import com.outpick.domain.favorite.service.FavoriteService;
import com.outpick.domain.member.security.MemberPrincipal;
import jakarta.validation.constraints.Positive;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/members/me/favorites")
@RequiredArgsConstructor
public class FavoriteController {
    private final FavoriteService favoriteService;

    @GetMapping
    public List<FavoritePlaceResponse> findMyFavorites(Authentication authentication) {
        return favoriteService.findPlaces(((MemberPrincipal) authentication.getPrincipal()).memberId());
    }

    @PatchMapping("/{placeId}/notification-settings")
    public FavoriteNotificationResponse changeNotificationSetting(
            Authentication authentication,
            @PathVariable("placeId") @Positive(message = "장소 ID는 1 이상이어야 합니다.") long placeId,
            @Valid @RequestBody FavoriteNotificationRequest request) {
        favoriteService.changeNotificationSetting(
                ((MemberPrincipal) authentication.getPrincipal()).memberId(), placeId, request.isNotified());
        return new FavoriteNotificationResponse(placeId, request.isNotified());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FavoriteActionResponse save(Authentication authentication,
                                       @Valid @RequestBody FavoriteCreateRequest request) {
        favoriteService.save(((MemberPrincipal) authentication.getPrincipal()).memberId(), request.placeId());
        return new FavoriteActionResponse(request.placeId(), "관심 장소에 저장했습니다.");
    }

    @DeleteMapping("/{placeId}")
    public FavoriteActionResponse remove(Authentication authentication,
                       @PathVariable("placeId") @Positive(message = "장소 ID는 1 이상이어야 합니다.") long placeId) {
        favoriteService.remove(((MemberPrincipal) authentication.getPrincipal()).memberId(), placeId);
        return new FavoriteActionResponse(placeId, "관심 장소에서 해제했습니다.");
    }
}
