package com.tss.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoRepositories("com.tss.mongodb.repo")
public class MongoConfig {
    
}
