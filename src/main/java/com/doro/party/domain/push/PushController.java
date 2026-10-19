package com.doro.party.domain.push;

import com.doro.party.common.exception.ErrorCode;
import com.doro.party.common.exception.PartyException;
import com.doro.party.common.response.ApiResponse;
import com.doro.party.domain.push.PushDtos.PreferencesDto;
import com.doro.party.domain.push.PushDtos.PushConfigResponse;
import com.doro.party.domain.push.PushDtos.SubscribeRequest;
import com.doro.party.domain.push.PushDtos.TestResult;
import com.doro.party.domain.push.PushDtos.UnsubscribeRequest;
import com.hunnit_beasts.doro.sdk.annotation.CurrentDoroUser;
import com.hunnit_beasts.doro.sdk.domain.DoroUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "9. Push (푸시 알림)", description = "이 기기로 알림 받기: 구독 등록·해제, 알림 종류 설정")
@RestController
@RequestMapping("/api/v1/push")
@RequiredArgsConstructor
public class PushController {

    private final PushSubscriptionService service;

    private static DoroUser authenticated(DoroUser user) {
        if (user == null || !user.isAuthenticated()) {
            throw new PartyException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    @Operation(summary = "푸시 설정 (로그인)", description = "enabled 가 false 면 서버에 VAPID 가 없어 켤 수 없다. publicKey 는 브라우저가 구독할 때 쓴다")
    @GetMapping("/config")
    public ApiResponse<PushConfigResponse> config(@CurrentDoroUser DoroUser user) {
        authenticated(user);
        return ApiResponse.success(service.config());
    }

    @Operation(summary = "이 기기를 알림 기기로 등록 (로그인)", description = "브라우저의 PushSubscription.toJSON() 을 그대로 보낸다. 허용된 푸시 서비스 주소만 받는다")
    @PutMapping("/subscription")
    public ApiResponse<Void> subscribe(@CurrentDoroUser DoroUser user, @Valid @RequestBody SubscribeRequest body, HttpServletRequest request) {
        service.subscribe(authenticated(user), body.endpoint(), body.keys().p256dh(), body.keys().auth(), request.getHeader(HttpHeaders.USER_AGENT));
        return ApiResponse.success();
    }

    @Operation(summary = "이 기기의 알림 해제 (로그인)", description = "없어도 성공. 내 구독만 지울 수 있다")
    @DeleteMapping("/subscription")
    public ApiResponse<Void> unsubscribe(@CurrentDoroUser DoroUser user, @Valid @RequestBody UnsubscribeRequest body) {
        service.unsubscribe(authenticated(user), body.endpoint());
        return ApiResponse.success();
    }

    @Operation(summary = "알림 종류 설정 보기 (로그인)")
    @GetMapping("/preferences")
    public ApiResponse<PreferencesDto> preferences(@CurrentDoroUser DoroUser user) {
        return ApiResponse.success(service.preferences(authenticated(user)));
    }

    @Operation(summary = "알림 종류 설정 바꾸기 (로그인)")
    @PutMapping("/preferences")
    public ApiResponse<PreferencesDto> updatePreferences(@CurrentDoroUser DoroUser user, @RequestBody PreferencesDto body) {
        return ApiResponse.success(service.updatePreferences(authenticated(user), body));
    }

    @Operation(summary = "시험 알림 보내기 (로그인)", description = "내 모든 기기로 알림을 보내고 결과(기기 수, 전달 수)를 돌려준다")
    @PostMapping("/test")
    public ApiResponse<TestResult> test(@CurrentDoroUser DoroUser user) {
        return ApiResponse.success(service.sendTest(authenticated(user)));
    }
}
