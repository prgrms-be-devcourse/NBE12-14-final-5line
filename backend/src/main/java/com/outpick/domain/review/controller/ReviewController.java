package com.outpick.domain.review.controller;

import com.outpick.domain.member.security.MemberPrincipal;
import com.outpick.domain.review.dto.ReviewRequest;
import com.outpick.domain.review.dto.ReportRequest;
import com.outpick.domain.review.dto.ReviewReportResponse;
import com.outpick.domain.review.dto.ReviewPhotoResponse;
import com.outpick.domain.review.dto.ReviewResponse;
import com.outpick.domain.review.service.ReviewPhotoService;
import com.outpick.domain.review.service.ReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class ReviewController {
    private final ReviewService reviews;
    private final ReviewPhotoService photos;

    @GetMapping("/places/{placeId}/reviews")
    public List<ReviewResponse> list(@PathVariable @Positive long placeId) {
        return reviews.list(placeId);
    }

    @PostMapping("/places/{placeId}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse create(@PathVariable @Positive long placeId,
                                 @Valid @RequestBody ReviewRequest request, Authentication authentication) {
        return reviews.create(placeId, memberId(authentication), request.body());
    }

    @PutMapping("/reviews/{reviewId}")
    public ReviewResponse update(@PathVariable @Positive long reviewId,
                                 @Valid @RequestBody ReviewRequest request, Authentication authentication) {
        return reviews.update(reviewId, memberId(authentication), request.body());
    }

    @DeleteMapping("/reviews/{reviewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable @Positive long reviewId, Authentication authentication) {
        reviews.delete(reviewId, memberId(authentication));
    }

    @PostMapping("/reviews/{reviewId}/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewReportResponse report(@PathVariable @Positive long reviewId,
                                       @Valid @RequestBody ReportRequest request, Authentication authentication) {
        return reviews.report(reviewId, memberId(authentication), request.reason());
    }

    @GetMapping("/admin/review-reports")
    public List<ReviewReportResponse> pendingReports(Authentication authentication) {
        return reviews.pendingReports(memberId(authentication));
    }

    @PatchMapping("/admin/reviews/{reviewId}/hide")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void hide(@PathVariable @Positive long reviewId, Authentication authentication) {
        reviews.hide(reviewId, memberId(authentication));
    }

    @PatchMapping("/admin/review-reports/{reportId}/dismiss")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dismiss(@PathVariable @Positive long reportId, Authentication authentication) {
        reviews.dismiss(reportId, memberId(authentication));
    }

    @PostMapping(value = "/reviews/{reviewId}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewPhotoResponse uploadPhoto(@PathVariable @Positive long reviewId,
                                           @RequestPart("file") MultipartFile file, Authentication authentication) {
        return photos.upload(reviewId, memberId(authentication), file);
    }

    @GetMapping("/review-photos/{assetId}")
    public ResponseEntity<byte[]> photo(@PathVariable @Positive long assetId) {
        var photo = photos.publicPhoto(assetId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(photo.contentType()))
                .header("Cache-Control", "no-store").body(photo.bytes());
    }

    @DeleteMapping("/reviews/{reviewId}/photos/{assetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePhoto(@PathVariable @Positive long reviewId, @PathVariable @Positive long assetId,
                            Authentication authentication) {
        photos.delete(reviewId, assetId, memberId(authentication));
    }

    private long memberId(Authentication authentication) {
        return ((MemberPrincipal) authentication.getPrincipal()).memberId();
    }
}
