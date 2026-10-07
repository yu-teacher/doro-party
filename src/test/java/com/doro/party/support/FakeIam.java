package com.doro.party.support;

import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** 테스트용 가짜 Doro IAM: /oauth2/token(코드 교환·갱신)과 /oauth2/revoke 만 흉내 낸다. 실제 RS256 JWT 를 발급한다. */
public final class FakeIam implements AutoCloseable {

    public static final String KID = "fake-iam-kid";

    private record Grant(UUID userId, String email, UUID sessionId, String challenge, String clientId) {}

    private final HttpServer server;
    private final KeyPair keyPair;
    private final Map<String, Grant> codes = new ConcurrentHashMap<>();
    private final Map<String, Grant> refreshTokens = new ConcurrentHashMap<>();

    public final AtomicInteger codeExchanges = new AtomicInteger();
    public final AtomicInteger refreshCalls = new AtomicInteger();
    public final AtomicInteger revokeCalls = new AtomicInteger();
    public volatile String lastRevokedToken;
    public volatile boolean rejectRefresh;
    public volatile boolean unavailable;
    public volatile long accessTtlSeconds = 900;

    public FakeIam() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keyPair = gen.generateKeyPair();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/oauth2/token", exchange -> {
            Map<String, String> form = readForm(exchange.getRequestBody().readAllBytes());
            if (unavailable) {
                respond(exchange, 503, "{}");
                return;
            }
            if ("authorization_code".equals(form.get("grant_type"))) {
                codeExchanges.incrementAndGet();
                Grant grant = codes.remove(form.get("code"));
                if (grant == null || !grant.challenge().equals(s256(form.get("code_verifier")))) {
                    respond(exchange, 400, "{\"error\":\"invalid_grant\"}");
                    return;
                }
                respond(exchange, 200, tokenBody(grant));
            } else if ("refresh_token".equals(form.get("grant_type"))) {
                refreshCalls.incrementAndGet();
                Grant grant = rejectRefresh ? null : refreshTokens.remove(form.get("refresh_token"));
                if (grant == null) {
                    respond(exchange, 400, "{\"error\":\"invalid_grant\"}");
                    return;
                }
                respond(exchange, 200, tokenBody(grant));
            } else {
                respond(exchange, 400, "{\"error\":\"unsupported_grant_type\"}");
            }
        });
        server.createContext("/oauth2/revoke", exchange -> {
            Map<String, String> form = readForm(exchange.getRequestBody().readAllBytes());
            revokeCalls.incrementAndGet();
            lastRevokedToken = form.get("token");
            respond(exchange, 200, "");
        });
        server.start();
    }

    public java.security.PublicKey publicKey() {
        return keyPair.getPublic();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** 사용자가 Doro 에서 로그인해 인가 코드를 받은 상황을 만든다. */
    public String issueCode(UUID userId, String email, String challenge, String clientId) {
        String code = "code-" + UUID.randomUUID();
        codes.put(code, new Grant(userId, email, UUID.randomUUID(), challenge, clientId));
        return code;
    }

    private String tokenBody(Grant grant) {
        String access = Jwts.builder()
                .header().keyId(KID).and()
                .subject(grant.userId().toString())
                .claim("email", grant.email())
                .claim("sid", grant.sessionId().toString())
                .claim("role", "USER")
                .claim("cid", grant.clientId())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTtlSeconds * 1000))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
        String refresh = "refresh-" + UUID.randomUUID();
        refreshTokens.put(refresh, grant);
        return "{\"access_token\":\"" + access + "\",\"token_type\":\"Bearer\",\"expires_in\":" + accessTtlSeconds
                + ",\"refresh_token\":\"" + refresh + "\"}";
    }

    private static Map<String, String> readForm(byte[] body) {
        Map<String, String> form = new HashMap<>();
        for (String pair : new String(body, StandardCharsets.UTF_8).split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            form.put(key, value);
        }
        return form;
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }

    public static String s256(String verifier) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
