package com.workflowx.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 演示数据启动钩子：demo.seed=true 时在应用就绪后执行一次演示数据生成。
 *
 * 用法（幂等，可放心常开）：
 * - 本地：DEMO_SEED=true mvn spring-boot:run
 * - Docker：compose 里给 backend 设 DEMO_SEED=true
 */
@Component
@ConditionalOnProperty(name = "demo.seed", havingValue = "true")
public class DemoDataRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataRunner.class);

    private final DemoDataSeeder seeder;

    public DemoDataRunner(DemoDataSeeder seeder) {
        this.seeder = seeder;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            seeder.seed();
        } catch (Exception e) {
            // 演示数据失败不阻断应用启动（基础设施未就绪等场景），只记录并给出明确提示
            log.error("[demo-seed] 演示数据生成失败（不影响应用继续运行）: {}", e.getMessage(), e);
        }
    }
}
