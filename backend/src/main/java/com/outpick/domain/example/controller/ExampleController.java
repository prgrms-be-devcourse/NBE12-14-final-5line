package com.outpick.domain.example.controller;

import com.outpick.domain.example.dto.ExampleCreateRequest;
import com.outpick.domain.example.dto.ExampleResponse;
import com.outpick.domain.example.service.ExampleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 요청을 검증하고 서비스에 전달한 뒤 DTO를 직접 반환하는 예시 컨트롤러입니다.
 */
@RestController
@RequestMapping("/examples")
@RequiredArgsConstructor
public class ExampleController {

    private final ExampleService exampleService;

    /**
     * 경로의 ID가 양수인지 검사하고 서비스가 반환한 조회 DTO를 200 응답으로 보냅니다.
     */
    @GetMapping("/{id}")
    public ExampleResponse getExample(
            @PathVariable("id")
            @Positive(message = "예시 ID는 1 이상이어야 합니다.")
            long id
    ) {
        return exampleService.getExample(id);
    }

    /** 요청 DTO의 필드 제약을 검사하고 서비스가 만든 미리보기를 200 응답으로 보냅니다. */
    @PostMapping("/preview")
    public ExampleResponse preview(
            @Valid
            @RequestBody
            ExampleCreateRequest request
    ) {
        return exampleService.preview(request);
    }
}
