package com.aixpertsoft;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Phase 1 of the AixBOMS data stack: every layer written by hand for the Rack use case.
 * <ul>
 *   <li>{@code com.aixpertsoft.datastack}: the reusable framework (DAO base class, rule dispatcher, error handling)</li>
 *   <li>{@code com.aixpertsoft.dcim}: the Rack / Component / Person microservice built on it</li>
 * </ul>
 */
@SpringBootApplication
public class Phase1Application {

    public static void main(String[] args) {
        SpringApplication.run(Phase1Application.class, args);
    }
}
