package com.zyagent.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;

@Configuration
public class SchemaInitializer {
    private static final Logger log = LoggerFactory.getLogger(SchemaInitializer.class);

    @Bean
    ApplicationRunner zyagentSchemaRunner(DataSource dataSource) {
        return ignored -> {
            try {
                ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource("schema.sql"));
                populator.execute(dataSource);
                log.info("zyagent MySQL schema initialized.");
            } catch (RuntimeException ex) {
                log.warn("zyagent MySQL schema initialization skipped: {}", ex.getMessage());
            }
        };
    }
}
