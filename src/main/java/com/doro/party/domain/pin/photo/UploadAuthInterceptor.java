package com.doro.party.domain.pin.photo;

import com.doro.party.common.exception.ErrorCode;
import com.doro.party.common.exception.PartyException;
import com.hunnit_beasts.doro.sdk.domain.DoroUser;
import com.hunnit_beasts.doro.sdk.domain.DoroUserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 사진 업로드는 로그인한 사용자만 할 수 있다. 파일(요청 본문)을 읽기 전에 검사해서,
 * 로그인하지 않은 요청이 큰 파일을 올리게 두지 않고 바로 401 로 끊는다.
 */
@Component
public class UploadAuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"POST".equals(request.getMethod())) {
            return true;
        }
        DoroUser user = DoroUserContext.getCurrentUser();
        if (user == null || !user.isAuthenticated()) {
            throw new PartyException(ErrorCode.UNAUTHORIZED);
        }
        return true;
    }
}
