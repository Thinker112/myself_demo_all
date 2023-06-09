package com.example.amqp_mq_demo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@Slf4j
public class AmqpMqDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(AmqpMqDemoApplication.class, args);
        log.info("start application success");
    }

}
