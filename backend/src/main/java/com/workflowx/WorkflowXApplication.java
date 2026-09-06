package com.workflowx;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * WorkFlowX 后端入口。
 * 架构：Modular Monolith（ADR-001），业务模块见 com.workflowx 下各包。
 */
@SpringBootApplication
public class WorkflowXApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkflowXApplication.class, args);
    }
}
