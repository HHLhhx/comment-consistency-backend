package com.nju.comment.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableCaching
@EnableJpaAuditing
@EnableScheduling
public class CommentConsistencyBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommentConsistencyBackendApplication.class, args);
    }

}
