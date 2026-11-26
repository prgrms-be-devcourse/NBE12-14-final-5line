package com.outpick.domain.member.security;

import java.io.Serializable;

public record MemberPrincipal(Long memberId, long authVersion) implements Serializable {
    private static final long serialVersionUID = 1L;
}
