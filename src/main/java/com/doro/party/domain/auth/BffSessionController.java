package com.doro.party.domain.auth;

import com.doro.party.common.response.ApiResponse;
import com.doro.party.domain.user.dto.PartyUserDtos.UserProfileResponse;
import com.doro.party.domain.user.service.PartyUserService;
import com.hunnit_beasts.doro.sdk.annotation.CurrentDoroUser;
import com.hunnit_beasts.doro.sdk.domain.DoroUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 현재 로그인 상태와 내 프로필. 로그인·콜백·로그아웃은 SDK 의 BFF 컨트롤러가 같은 경로(/api/v1/bff)에서 제공하고,
 * 프로필 모양이 서비스마다 다르므로 이 조회만 도로 파티가 맡는다.
 */
@Tag(name = "Auth (BFF)", description = "Doro OAuth 로그인 — 세션 쿠키 기반")
@RestController
@RequestMapping("/api/v1/bff")
@RequiredArgsConstructor
public class BffSessionController {

    private final PartyUserService userService;

    /** 응답 본문: 현재 로그인 상태와 내 프로필. 비로그인이어도 200 이다(화면이 401 오류 처리 없이 상태를 알 수 있게). */
    public record SessionResponse(boolean authenticated, UserProfileResponse user, String role) {}

    @Operation(summary = "내 로그인 상태", description = "로그인했다면 내 프로필, 아니면 authenticated=false")
    @GetMapping("/session")
    public ResponseEntity<ApiResponse<SessionResponse>> session(@CurrentDoroUser DoroUser doroUser) {
        SessionResponse body = (doroUser != null && doroUser.isAuthenticated())
                ? new SessionResponse(true, profileOf(doroUser), doroUser.role())
                : new SessionResponse(false, null, null);
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(ApiResponse.success(body));
    }

    /**
     * 사용자 행이 없으면 만든다. embedded 모드에서는 로그인 직후 훅이 이미 만들었고, gateway 모드(로그인을 별도 BFF 가 처리)에서는
     * 로그인 후 첫 화면이 이 조회라서 여기서 만든다(이미 있으면 읽기만 한다).
     */
    private UserProfileResponse profileOf(DoroUser doroUser) {
        userService.getOrCreateUser(doroUser);
        return userService.getProfileById(doroUser.userId());
    }
}
