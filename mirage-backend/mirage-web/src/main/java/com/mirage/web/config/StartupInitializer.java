package com.mirage.web.config;

import com.mirage.task.GpuSlotManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 启动初始化配置: 初始化 GPU 槽位等运行时状态
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class StartupInitializer {

    @Bean
    public ApplicationRunner initGpuSlots(GpuSlotManager gpuSlotManager,
                                          @Value("${gpu.slots:2}") int totalSlots) {
        return args -> {
            gpuSlotManager.initSlots(totalSlots);
            log.info("启动初始化完成: GPU 槽位={}", totalSlots);
        };
    }
}
