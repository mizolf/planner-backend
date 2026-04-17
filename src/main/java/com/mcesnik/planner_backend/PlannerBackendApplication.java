package com.mcesnik.planner_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PlannerBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(PlannerBackendApplication.class, args);
	}

}
