package io.github.aryan52.tenantflow.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables housekeeping jobs (e.g. purging expired idempotency keys). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
