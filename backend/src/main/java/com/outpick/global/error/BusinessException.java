package com.outpick.global.error;

import lombok.Getter;

import java.util.Objects;

/** 기능 코드에서 발생하는 비즈니스 로직상의 예외 */
@Getter
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;

	public BusinessException(ErrorCode errorCode) {
		super(Objects.requireNonNull(errorCode).message());
		this.errorCode = errorCode;
	}
}
