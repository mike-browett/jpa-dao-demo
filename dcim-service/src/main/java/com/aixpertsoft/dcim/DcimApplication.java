// Created once by datastack-generator. This file is yours: the generator never overwrites it.
// Put custom code here; it survives every regeneration.
package com.aixpertsoft.dcim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * Runs the dcim model on its own. It deliberately does no component scan: the model's beans come
 * from the generated DcimModelConfiguration (an auto-configuration), exactly as when the jar
 * is deployed into datastack-server next to other models.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
public class DcimApplication {

    public static void main(String[] args) {
        SpringApplication.run(DcimApplication.class, args);
    }
}
