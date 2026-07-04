package com.mirage.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * MirageStudio 后端主应用启动类
 *
 * <p>扫描范围: com.mirage (涵盖 common / dao / service / task / integration / web)</p>
 */
@SpringBootApplication(scanBasePackages = "com.mirage")
@MapperScan("com.mirage.dao.mapper")
@EnableTransactionManagement
@EnableAsync
public class MirageStudioApplication {

    public static void main(String[] args) {
        SpringApplication.run(MirageStudioApplication.class, args);
    }
}
