package com.doro.party.common.web;

/** 목록 API 의 페이지 파라미터 허용 범위. 어노테이션 값으로 쓰이므로 컴파일 상수여야 한다. */
public final class PageLimits {

    public static final int MIN_PAGE = 0;
    public static final int MIN_SIZE = 1;
    public static final int MAX_SIZE = 100;
    /** 연관 글 추천처럼 개수를 직접 받는 API 의 상한. */
    public static final int MAX_LIMIT = 20;

    private PageLimits() {
    }
}
