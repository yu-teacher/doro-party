package com.doro.party.domain.pin.service;

import com.doro.party.common.exception.ErrorCode;
import com.doro.party.common.exception.PartyException;
import com.doro.party.domain.pin.dto.PinDtos;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** 태그 입력을 정리한다: 앞뒤 공백·앞의 #을 떼고 소문자로 맞추며, 글자·숫자·'_'·'-' 만 허용하고 중복은 하나로 합친다. */
public final class TagNormalizer {

    private static final Pattern ALLOWED = Pattern.compile("^[\\p{L}\\p{N}_-]{1," + PinDtos.TAG_MAX + "}$");

    private TagNormalizer() {
    }

    public static Set<String> normalize(List<String> raw, int maxTags) {
        Set<String> result = new LinkedHashSet<>();
        if (raw == null) {
            return result;
        }
        for (String tag : raw) {
            String cleaned = clean(tag);
            if (cleaned == null || !ALLOWED.matcher(cleaned).matches()) {
                throw new PartyException(ErrorCode.INVALID_INPUT,
                        "태그는 1~" + PinDtos.TAG_MAX + "자의 글자, 숫자, '-', '_' 만 사용할 수 있습니다.");
            }
            result.add(cleaned);
        }
        if (result.size() > maxTags) {
            throw new PartyException(ErrorCode.LIMIT_EXCEEDED, "태그는 핀마다 최대 " + maxTags + "개까지 붙일 수 있습니다.");
        }
        return result;
    }

    /** 검색 조건용: 하나만 정리하고 비어 있으면 null. */
    public static String normalizeOne(String raw) {
        String cleaned = clean(raw);
        if (cleaned == null || cleaned.isEmpty()) {
            return null;
        }
        if (!ALLOWED.matcher(cleaned).matches()) {
            throw new PartyException(ErrorCode.INVALID_INPUT, "태그 형식이 올바르지 않습니다.");
        }
        return cleaned;
    }

    private static String clean(String tag) {
        if (tag == null) {
            return null;
        }
        String trimmed = tag.strip();
        if (trimmed.startsWith("#")) {
            trimmed = trimmed.substring(1).strip();
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }
}
