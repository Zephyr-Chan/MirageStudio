package com.mirage.gateway;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * JWT 鉴权全局过滤器
 *
 * <p>流程:
 * <ol>
 *   <li>白名单路径直接放行 (登录/注册/内部接口/WebSocket/健康检查)</li>
 *   <li>从 Authorization: Bearer xxx 提取 token</li>
 *   <li>解析验证 token, 提取 userId</li>
 *   <li>将 userId 写入下游请求头 X-User-Id, 供后端服务使用</li>
 *   <li>验证失败返回 401</li>
 * </ol></p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USERNAME_HEADER = "X-Username";

    /** 鉴权白名单 */
    private static final Set<String> WHITELIST = Set.of(
            "/api/auth/**",
            "/api/internal/**",
            "/ws/**",
            "/actuator/**"
    );

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final GatewayJwtUtil jwtUtil;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 白名单放行
        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        // OPTIONS 预检放行
        String method = request.getMethod().name();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return chain.filter(exchange);
        }

        // 提取 token
        String token = extractToken(request.getHeaders());
        if (!StringUtils.hasText(token)) {
            return unauthorized(exchange, "缺少认证令牌");
        }
        if (!jwtUtil.validateToken(token)) {
            return unauthorized(exchange, "认证令牌无效或已过期");
        }

        // 解析 userId, 传递到下游
        Long userId = jwtUtil.getUserId(token);
        if (userId == null) {
            return unauthorized(exchange, "令牌中缺少用户信息");
        }

        // 将 userId 注入下游请求头
        ServerHttpRequest mutated = request.mutate()
                .header(USER_ID_HEADER, String.valueOf(userId))
                .build();
        log.debug("网关鉴权通过: path={}, userId={}", path, userId);
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private boolean isWhitelisted(String path) {
        for (String pattern : WHITELIST) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private String extractToken(HttpHeaders headers) {
        String bearer = headers.getFirst(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearer) && bearer.startsWith(BEARER_PREFIX)) {
            return bearer.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":10003,\"message\":\"" + message + "\",\"success\":false}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        log.warn("网关鉴权失败: path={}, reason={}", exchange.getRequest().getURI().getPath(), message);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // 在限流过滤器之前执行
        return -200;
    }
}
