package com.doro.party.domain.user.controller;

import com.doro.party.common.response.ApiResponse;
import com.doro.party.domain.user.dto.PartyUserDtos.UpdateProfileRequest;
import com.doro.party.domain.user.dto.PartyUserDtos.UserProfileResponse;
import com.doro.party.domain.user.service.PartyUserService;
import com.hunnit_beasts.doro.sdk.annotation.CurrentDoroUser;
import com.hunnit_beasts.doro.sdk.domain.DoroUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "0. Me (내 프로필)", description = "닉네임과 사용자명")
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MeController {

    private final PartyUserService userService;

    @Operation(summary = "내 프로필 (로그인)")
    @GetMapping
    public ApiResponse<UserProfileResponse> me(@CurrentDoroUser DoroUser doroUser) {
        return ApiResponse.success(UserProfileResponse.from(userService.getOrCreateUser(doroUser)));
    }

    @Operation(summary = "닉네임·사용자명 수정 (로그인)", description = "사용자명은 친구가 나를 찾는 이름이다. 소문자 영문·숫자·_ 3~30자")
    @RequestMapping(method = {RequestMethod.PUT, RequestMethod.PATCH})
    public ApiResponse<UserProfileResponse> update(@CurrentDoroUser DoroUser doroUser, @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.success(userService.updateProfile(doroUser, request));
    }
}
