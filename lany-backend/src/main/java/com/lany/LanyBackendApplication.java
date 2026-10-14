package com.lany;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LanyBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(LanyBackendApplication.class, args);
		System.out.println("Lany Backend Application started successfully.");
	}

}
