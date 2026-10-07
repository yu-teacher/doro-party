package com.doro.party.infra.guard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PartySchemaMergerTest {

    private static final String OTHER_SERVICES = """
            # Doro Guard Standard Zanzibar Schema DSL

            type user {
              relation manager: user
            }

            type blog_post {
              relation author: user
              relation viewer: author | user
            }
            """;

    private static final String PARTY_V1 = """
            # DORO Party
            type party_map {
              relation owner: user
              relation viewer: owner | user
            }
            """;

    private static final String PARTY_V2 = """
            # DORO Party
            type party_group {
              relation owner: user
              relation member: owner | user
            }

            type party_map {
              relation owner: user
              relation viewer: owner | user | party_group#member
            }
            """;

    @Test
    @DisplayName("없는 도로 파티 타입은 뒤에 덧붙이고, 다른 서비스의 DSL 은 한 글자도 바꾸지 않는다")
    void appendsMissingTypes() {
        PartySchemaMerger.Result result = PartySchemaMerger.merge(OTHER_SERVICES, PARTY_V1);

        assertThat(result.addedTypes()).containsExactly("party_map");
        assertThat(result.replacedTypes()).isEmpty();
        assertThat(result.mergedDsl()).startsWith(OTHER_SERVICES.stripTrailing());
        assertThat(result.mergedDsl()).contains("type party_map {");
    }

    @Test
    @DisplayName("이미 같은 내용이면 아무것도 바꾸지 않는다(공백·주석 차이는 무시)")
    void idempotent() {
        String merged = PartySchemaMerger.merge(OTHER_SERVICES, PARTY_V1).mergedDsl();

        PartySchemaMerger.Result again = PartySchemaMerger.merge(merged, PARTY_V1.replace("relation owner: user", "relation   owner:   user").replace("type party_map {", "# 한 줄 주석\n\ntype party_map {"));

        assertThat(again.changed()).isFalse();
        assertThat(again.mergedDsl()).isEqualTo(merged);
    }

    @Test
    @DisplayName("내용이 바뀐 도로 파티 타입은 제자리에서 교체하고 새 타입은 덧붙이며, 나머지는 그대로다")
    void replacesChangedTypesInPlace() {
        String v1 = PartySchemaMerger.merge(OTHER_SERVICES, PARTY_V1).mergedDsl();
        String withTrailingService = v1 + "\ntype shop_order {\n  relation buyer: user\n}\n";

        PartySchemaMerger.Result result = PartySchemaMerger.merge(withTrailingService, PARTY_V2);

        assertThat(result.replacedTypes()).containsExactly("party_map");
        assertThat(result.addedTypes()).containsExactly("party_group");
        String merged = result.mergedDsl();
        assertThat(merged).contains("relation viewer: owner | user | party_group#member");
        assertThat(merged).doesNotContain("relation viewer: owner | user\n");
        assertThat(merged).contains("type shop_order {\n  relation buyer: user\n}");
        assertThat(merged).startsWith(OTHER_SERVICES.stripTrailing());
        assertThat(merged.indexOf("type party_map {")).isLessThan(merged.indexOf("type shop_order {"));
        assertThat(merged.split("type party_map \\{", -1)).as("party_map 은 한 번만 있다").hasSize(2);
    }

    @Test
    @DisplayName("이름이 접두사로만 겹치는 타입을 같은 타입으로 착각하지 않는다")
    void prefixNamesAreDistinct() {
        String active = OTHER_SERVICES + "\ntype party_map_archive {\n  relation owner: user\n}\n";

        PartySchemaMerger.Result result = PartySchemaMerger.merge(active, PARTY_V1);

        assertThat(result.addedTypes()).containsExactly("party_map");
        assertThat(result.mergedDsl()).contains("type party_map_archive {").contains("type party_map {");
    }

    @Test
    @DisplayName("활성 스키마가 비어 있어도 병합할 수 있다")
    void emptyActiveSchema() {
        PartySchemaMerger.Result result = PartySchemaMerger.merge("", PARTY_V2);

        assertThat(result.addedTypes()).containsExactly("party_group", "party_map");
        assertThat(result.mergedDsl()).startsWith("type party_group {").endsWith("}\n");
    }
}
