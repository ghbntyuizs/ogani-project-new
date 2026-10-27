package com.ogani.store;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OganiStoreApplication {
    public static void main(String[] args) {
        SpringApplication.run(OganiStoreApplication.class, args);
        System.out.println("URL: " + System.getenv("TEST_DB_URL"));
        System.out.println("USER: " + System.getenv("TEST_DB_USER"));

        System.out.println(
                "PASSWORD exists: "
                        + (System.getenv("TEST_DB_PASSWORD") != null)
        );
    }
}
