package com.ogani.store.auth.reset;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

@Configuration(proxyBeanMethods=false)
@EnableConfigurationProperties(PasswordResetProperties.class)
public class PasswordResetConfig {
    @Bean public Clock passwordResetClock() { return Clock.systemUTC(); }
}
