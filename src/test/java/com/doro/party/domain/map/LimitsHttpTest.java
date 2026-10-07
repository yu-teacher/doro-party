package com.doro.party.domain.map;

import com.doro.party.support.PartyHttpTestBase;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 개수 상한(지도·핀·태그)은 동시 요청에서도 지켜져야 한다. 작은 상한으로 따로 띄운 컨텍스트에서 검증한다. */
@TestPropertySource(properties = {
        "party.limits.max-maps-per-user=3",
        "party.limits.max-pins-per-map=3",
        "party.limits.max-tags-per-pin=2",
})
class LimitsHttpTest extends PartyHttpTestBase {

    private static final int PARALLEL_REQUESTS = 12;

    private int statusOf(TestUser user, String url, String body) throws Exception {
        return mockMvc.perform(user.sign(post(url).contentType(MediaType.APPLICATION_JSON).content(body)))
                .andReturn().getResponse().getStatus();
    }

    private List<Integer> parallel(List<Callable<Integer>> calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> call : calls) {
                futures.add(pool.submit(call));
            }
            List<Integer> results = new ArrayList<>();
            for (Future<Integer> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("지도 개수 상한: 동시에 여러 개를 만들어도 상한 이상 만들어지지 않는다")
    void mapLimitHoldsUnderConcurrency() throws Exception {
        TestUser user = newUser();
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < PARALLEL_REQUESTS; i++) {
            String name = "{\"name\":\"지도" + i + "\"}";
            calls.add(() -> statusOf(user, "/api/v1/maps", name));
        }

        List<Integer> statuses = parallel(calls);

        assertThat(statuses.stream().filter(s -> s == 200).count()).isEqualTo(3);
        assertThat(statuses.stream().filter(s -> s == 400).count()).isEqualTo(PARALLEL_REQUESTS - 3);
    }

    @Test
    @DisplayName("핀 개수 상한: 동시에 여러 개를 꽂아도 상한 이상 저장되지 않는다")
    void pinLimitHoldsUnderConcurrency() throws Exception {
        TestUser user = newUser();
        String mapId = JsonPath.read(mockMvc.perform(user.sign(post("/api/v1/maps").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"상한\"}"))).andReturn().getResponse().getContentAsString(), "$.data.id");
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < PARALLEL_REQUESTS; i++) {
            String body = "{\"name\":\"핀" + i + "\",\"lat\":37.5,\"lng\":127.0}";
            calls.add(() -> statusOf(user, "/api/v1/maps/" + mapId + "/pins", body));
        }

        List<Integer> statuses = parallel(calls);

        assertThat(statuses.stream().filter(s -> s == 200).count()).isEqualTo(3);
        assertThat(statuses.stream().filter(s -> s == 400).count()).isEqualTo(PARALLEL_REQUESTS - 3);
    }

    @Test
    @DisplayName("태그 개수 상한을 넘으면 400(LIMIT-400-01)")
    void tagLimit() throws Exception {
        TestUser user = newUser();
        String mapId = JsonPath.read(mockMvc.perform(user.sign(post("/api/v1/maps").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"태그\"}"))).andReturn().getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(user.sign(post("/api/v1/maps/{m}/pins", mapId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"a\",\"lat\":1,\"lng\":1,\"tags\":[\"a\",\"b\",\"c\"]}")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LIMIT-400-01"));
        mockMvc.perform(user.sign(post("/api/v1/maps/{m}/pins", mapId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"a\",\"lat\":1,\"lng\":1,\"tags\":[\"a\",\"#A\"]}")))
                .andExpect(status().isOk());
    }
}
