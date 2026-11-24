package com.aiworkbench;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AiWorkbenchApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiWorkbenchApplication.class, args);
        System.out.println("\n🚀 AI Workbench (Spring Boot) running on http://localhost:8080");
        System.out.println("   MySQL: localhost:3306/ai_workbench\n");
    }
}
