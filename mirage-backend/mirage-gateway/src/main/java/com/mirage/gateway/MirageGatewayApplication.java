package com.mirage.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MirageStudio API 网关启动类
 *
 * <p>Spring Cloud Gateway 基于 WebFlux (响应式), 与 mirage-web 的 Servlet 栈隔离。</p>
 */
@SpringBootApplication
public class MirageGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(MirageGatewayApplication.class, args);
    }
}
