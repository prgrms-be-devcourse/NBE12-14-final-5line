package com.outpick.domain.example.service;

import com.outpick.domain.example.dto.ExampleCreateRequest;
import com.outpick.domain.example.dto.ExampleResponse;
import com.outpick.domain.example.exception.ExampleErrorCode;
import com.outpick.global.error.BusinessException;
import org.springframework.stereotype.Service;

/**
 * DB 없이 조회와 미리보기를 처리해 서비스 계층과 공통 예외의 사용법을 보여 줍니다.
 */
@Service
public class ExampleService {

    /**
     * ID가 1이면 고정 예시를 반환하고, 다른 ID는 도메인 오류로 공통 예외 처리기에 전달합니다.
     */
    public ExampleResponse getExample(long id) {
        if (id != 1) {
            throw new BusinessException(ExampleErrorCode.EXAMPLE_NOT_FOUND.getErrorCode());
        }
        return new ExampleResponse("예시 액티비티", 3);
    }

    /**
     * 컨트롤러에서 검증한 입력으로 미리보기 응답을 만듭니다.<br />
     * 데이터는 저장하지 않습니다.
     */
    public ExampleResponse preview(ExampleCreateRequest request) {
        return new ExampleResponse(request.name(), request.participantCount());
    }
}
