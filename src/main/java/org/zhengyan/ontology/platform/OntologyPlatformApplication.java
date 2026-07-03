package org.zhengyan.ontology.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
/**
 * @author 郑炎 Zheng Yan
 */
public class OntologyPlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(OntologyPlatformApplication.class, args);
    }
}
