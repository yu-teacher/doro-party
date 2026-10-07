package com.doro.party.infra.guard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Guard 의 활성 스키마에 블로그 타입을 "없는 것만" 덧붙이는 순수 병합 로직.
 *
 * <p>Guard 스키마 등록은 전체 교체이므로 기존 DSL 은 한 글자도 바꾸지 않고 뒤에 이어 붙인다. 타입은 Guard 파서와 같은 규칙
 * (한 줄 {@code type 이름 {} … {@code }})으로 읽는다. 이름이 접두사로만 겹치는 타입(party_map / party_map_archive)을 같은
 * 타입으로 착각하지 않으려고 부분 문자열이 아니라 타입 이름 전체로 비교한다.
 */
final class PartySchemaMerger {

    private static final Pattern TYPE_START = Pattern.compile("^type\\s+([A-Za-z0-9_]+)\\s*\\{\\s*$");

    private PartySchemaMerger() {
    }

    /**
     * @param addedTypes     활성 스키마에 없어서 덧붙인 블로그 타입(블로그 스키마 파일의 선언 순서)
     * @param differingTypes 활성 스키마에 같은 이름이 있지만 내용이 다른 블로그 타입 (자동으로 바꾸지 않는다. 운영자가 확인해야 함)
     */
    record Result(String mergedDsl, List<String> addedTypes, List<String> differingTypes) {
        boolean changed() {
            return !addedTypes.isEmpty();
        }
    }

    static Result merge(String activeDsl, String partyDsl) {
        String active = activeDsl == null ? "" : activeDsl;
        Map<String, String> activeBlocks = parseBlocks(active);
        Map<String, String> partyBlocks = parseBlocks(partyDsl == null ? "" : partyDsl);

        List<String> added = new ArrayList<>();
        List<String> differing = new ArrayList<>();
        StringBuilder merged = new StringBuilder(active.stripTrailing());

        partyBlocks.forEach((name, block) -> {
            String existing = activeBlocks.get(name);
            if (existing == null) {
                added.add(name);
                if (!merged.isEmpty()) {
                    merged.append("\n\n");
                }
                merged.append(block.stripTrailing());
            } else if (!normalize(existing).equals(normalize(block))) {
                differing.add(name);
            }
        });

        if (added.isEmpty()) {
            return new Result(active, List.of(), List.copyOf(differing));
        }
        merged.append('\n');
        return new Result(merged.toString(), List.copyOf(added), List.copyOf(differing));
    }

    /** 타입 이름 -> 블록 텍스트(여는 줄부터 닫는 줄까지, 줄바꿈은 \n 으로 통일). 선언 순서를 유지한다. */
    private static Map<String, String> parseBlocks(String dsl) {
        Map<String, String> blocks = new LinkedHashMap<>();
        String name = null;
        StringBuilder current = null;
        for (String raw : dsl.split("\\R", -1)) {
            String line = raw.strip();
            if (name == null) {
                Matcher start = TYPE_START.matcher(line);
                if (start.matches()) {
                    name = start.group(1);
                    current = new StringBuilder(raw.stripTrailing());
                }
            } else {
                current.append('\n').append(raw.stripTrailing());
                if (line.equals("}")) {
                    blocks.put(name, current.toString());
                    name = null;
                    current = null;
                }
            }
        }
        return blocks;
    }

    /** 공백/주석 차이를 무시하고 비교하기 위한 정규화: 줄을 다듬고, 빈 줄과 전체 줄 주석을 버리고, 연속 공백을 하나로 줄인다. */
    private static String normalize(String block) {
        StringBuilder out = new StringBuilder();
        for (String raw : block.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) {
                continue;
            }
            out.append(line.replaceAll("\\s+", " ")).append('\n');
        }
        return out.toString();
    }
}
