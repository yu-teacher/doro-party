package com.doro.party.domain.auth;

import com.doro.party.common.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hunnit_beasts.doro.sdk.domain.DoroUser;
import com.hunnit_beasts.doro.sdk.domain.DoroUserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

/**
 * 세션 쿠키를 {@link DoroUser} 로 바꿔 {@link DoroUserContext} 에 넣는다. 이후 {@code @CurrentDoroUser}, {@code @DoroGuard}
 * 는 Bearer 토큰이나 API 키로 들어온 요청과 똑같이 동작한다.
 *
 * <p>쿠키는 브라우저가 자동으로 붙이므로 CSRF 방어가 필요하다. 쿠키가 있는 요청이 상태를 바꾸는 메서드(POST/PUT/PATCH/DELETE)면
 * (1) 커스텀 헤더가 있어야 하고(다른 사이트는 CORS 허용 없이 커스텀 헤더를 붙일 수 없다) (2) Origin 헤더가 있다면 이 사이트와 같아야 한다.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@RequiredArgsConstructor
public class BffSessionAuthFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final BffAuthService authService;
    private final BffProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String cookieValue = sessionCookie(request);
        if (cookieValue == null) {
            chain.doFilter(request, response);
            return;
        }

        if (!SAFE_METHODS.contains(request.getMethod()) && !passesCsrfCheck(request)) {
            log.warn("Rejected a cookie-authenticated request without a valid CSRF proof: method={}, uri={}",
                    request.getMethod(), request.getRequestURI());
            writeError(response, HttpStatus.FORBIDDEN, "AUTH-403-03", "요청을 확인할 수 없습니다. 페이지를 새로고침한 뒤 다시 시도해 주세요.");
            return;
        }

        // Bearer 토큰이나 다른 방식으로 이미 인증된 요청이면 그대로 둔다
        DoroUser existing = DoroUserContext.getCurrentUser();
        if (existing != null && existing.isAuthenticated()) {
            chain.doFilter(request, response);
            return;
        }

        Optional<DoroUser> user = authService.authenticate(cookieValue);
        if (user.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        DoroUserContext.setCurrentUser(user.get());
        try {
            chain.doFilter(request, response);
        } finally {
            DoroUserContext.clear();
        }
    }

    private String sessionCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (props.getCookieName().equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private boolean passesCsrfCheck(HttpServletRequest request) {
        String header = request.getHeader(props.getCsrfHeader());
        if (header == null || header.isBlank()) {
            return false;
        }
        String origin = request.getHeader("Origin");
        return origin == null || origin.equals(props.publicOrigin());
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(status, code, message)));
    }
}
