package io.salad109.conjunctionapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ConjunctionApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConjunctionApiApplication.class, args);
    }

}
