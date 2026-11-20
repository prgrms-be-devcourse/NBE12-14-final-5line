package com.outpick.domain.member.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.outpick.domain.member.entity.Member;
import com.outpick.domain.member.entity.RefreshToken;
import com.outpick.domain.member.repository.MemberRepository;
import com.outpick.domain.member.repository.RefreshTokenRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthTokenService {
    public static final String ACCESS_COOKIE = "outpick_access";
    public static final String REFRESH_COOKIE = "outpick_refresh";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration ACCESS_TTL = Duration.ofMinutes(15);
    private static final Duration REFRESH_TTL = Duration.ofDays(7);
    private final MemberRepository members;
    private final RefreshTokenRepository refreshTokens;
    private final Clock clock;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final boolean secureCookie;

    public AuthTokenService(MemberRepository members, RefreshTokenRepository refreshTokens, Clock clock,
                            Environment environment,
                            @Value("${app.auth.jwt-secret:}") String configuredSecret,
                            @Value("${app.auth.cookie-secure:false}") boolean secureCookie) {
        this.members = members;
        this.refreshTokens = refreshTokens;
        this.clock = clock;
        this.secureCookie = secureCookie;
        if (configuredSecret.isBlank() && !environment.acceptsProfiles(Profiles.of("local", "test"))) {
            throw new IllegalArgumentException("AUTH_JWT_SECRET_BASE64 must be configured outside local/test");
        }
        byte[] secret = configuredSecret.isBlank() ? randomBytes(32) : Base64.getDecoder().decode(configuredSecret);
        if (secret.length < 32) throw new IllegalArgumentException("JWT secret must contain at least 32 bytes");
        SecretKey key = new SecretKeySpec(secret, "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        var jwtDecoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        jwtDecoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("outpick"));
        this.decoder = jwtDecoder;
    }

    @Transactional
    public void issue(Member member, HttpServletResponse response) {
        writePair(member, UUID.randomUUID().toString(), response);
    }

    @Transactional
    public boolean rotate(String rawRefresh, HttpServletResponse response) {
        if (rawRefresh == null || !rawRefresh.matches("[A-Za-z0-9_-]{43}")) return false;
        RefreshToken previous = refreshTokens.findByTokenHash(hash(rawRefresh)).orElse(null);
        LocalDateTime now = LocalDateTime.now(clock);
        if (previous == null) return false;
        if (!previous.usableAt(now)) {
            if (previous.getRevokedAt() != null && now.isBefore(previous.getExpiresAt())) {
                refreshTokens.revokeFamily(previous.getFamilyId(), now);
            }
            return false;
        }
        previous.revoke(now);
        writePair(previous.getMember(), previous.getFamilyId(), response);
        return true;
    }

    @Transactional
    public void revoke(String rawRefresh, String rawAccess, HttpServletResponse response) {
        Member member = null;
        if (rawRefresh != null && rawRefresh.matches("[A-Za-z0-9_-]{43}")) {
            var token = refreshTokens.findByTokenHash(hash(rawRefresh)).orElse(null);
            LocalDateTime now = LocalDateTime.now(clock);
            if (token != null && token.usableAt(now)) {
                token.revoke(now);
                member = token.getMember();
            }
        }
        if (member == null) member = authenticate(rawAccess).orElse(null);
        if (member != null) member.revokeLogins();
        clearCookies(response);
    }

    public Optional<Member> authenticate(String rawAccess) {
        if (rawAccess == null) return Optional.empty();
        try {
            Jwt jwt = decoder.decode(rawAccess);
            if (!"access".equals(jwt.getClaimAsString("typ"))) return Optional.empty();
            if (jwt.getSubject() == null) return Optional.empty();
            Long memberId = Long.valueOf(jwt.getSubject());
            Object versionClaim = jwt.getClaims().get("ver");
            if (!(versionClaim instanceof Number version)) return Optional.empty();
            return members.findById(memberId)
                    .filter(member -> "ACTIVE".equals(member.getStatus())
                            && member.getAuthVersion() == version.longValue());
        } catch (JwtException | IllegalArgumentException error) {
            return Optional.empty();
        }
    }

    public void clearCookies(HttpServletResponse response) {
        addCookie(response, ACCESS_COOKIE, "", Duration.ZERO, "/api/v1");
        addCookie(response, REFRESH_COOKIE, "", Duration.ZERO, "/api/v1/auth");
    }

    public static String cookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (var cookie : request.getCookies()) if (name.equals(cookie.getName())) return cookie.getValue();
        return null;
    }

    private void writePair(Member member, String familyId, HttpServletResponse response) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("outpick").subject(member.getId().toString())
                .issuedAt(now).expiresAt(now.plus(ACCESS_TTL))
                .claim("typ", "access").claim("ver", member.getAuthVersion()).build();
        String access = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(32));
        refreshTokens.save(new RefreshToken(member, hash(refresh), familyId, LocalDateTime.now(clock)));
        addCookie(response, ACCESS_COOKIE, access, ACCESS_TTL, "/api/v1");
        addCookie(response, REFRESH_COOKIE, refresh, REFRESH_TTL, "/api/v1/auth");
    }

    private void addCookie(HttpServletResponse response, String name, String value, Duration age, String path) {
        ResponseCookie cookie = ResponseCookie.from(name, value).httpOnly(true).secure(secureCookie)
                .sameSite("Lax").path(path).maxAge(age).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private static byte[] randomBytes(int count) {
        byte[] value = new byte[count];
        RANDOM.nextBytes(value);
        return value;
    }

    private static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
