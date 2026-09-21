package io.github.aryan52.tenantflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class TenantflowBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(TenantflowBackendApplication.class, args);
	}

	@Bean
	public com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
		com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
		mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
		mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		return mapper;
	}
}
