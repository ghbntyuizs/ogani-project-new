package com.ogani.store;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OganiStoreApplication {
    public static void main(String[] args) {
        SpringApplication.run(OganiStoreApplication.class, args);
    }
}
