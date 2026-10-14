package com.lany;

import org.springframework.boot.SpringApplication;

public class TestLanyBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(LanyBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
