package com.mirage.web.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirage.common.R;
import com.mirage.common.exception.BizExceptionEnum;
import com.mirage.web.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Spring Security 配置: JWT 无状态鉴权
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> {
                })
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 认证接口放行
                        .requestMatchers(new AntPathRequestMatcher("/api/auth/**")).permitAll()
                        // 内部 Agent 接口 (供 Python Agent 调用, 可加 IP 白名单/签名校验)
                        .requestMatchers(new AntPathRequestMatcher("/api/internal/**")).permitAll()
                        // WebSocket endpoint 放行
                        .requestMatchers(new AntPathRequestMatcher("/ws/**")).permitAll()
                        // 健康检查
                        .requestMatchers(new AntPathRequestMatcher("/actuator/**")).permitAll()
                        // OPTIONS 预检放行
                        .requestMatchers(request -> HttpMethod.OPTIONS.name().equals(request.getMethod())).permitAll()
                        // 其余接口需要认证
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(401);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            R<Void> r = R.fail(BizExceptionEnum.UNAUTHORIZED.getCode(),
                                    BizExceptionEnum.UNAUTHORIZED.getMessage());
                            response.getWriter().write(objectMapper.writeValueAsString(r));
                        })
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
