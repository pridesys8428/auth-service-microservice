package com.auth.service.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class StartupLoggingConfig {

    private static final Logger logger = LoggerFactory.getLogger(StartupLoggingConfig.class);

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String datasourceUsername;

    @PostConstruct
    void logDatasourceConfig() {
        logger.info("Datasource URL: {}", datasourceUrl);
        logger.info("Datasource username: {}", datasourceUsername);
    }
}
