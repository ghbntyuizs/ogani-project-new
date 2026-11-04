package com.ogani.store.auth.reset;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.net.URI;
import java.util.Set;

@Validated
@ConfigurationProperties("app.password-reset")
public record PasswordResetProperties(@Min(1) @Max(60) int ttlMinutes,
        @Min(1) @Max(100) int emailLimit,@Min(1) @Max(1000) int ipLimit,
        @Min(1) @Max(1000) int resetIpLimit,@Min(1) @Max(60) int windowMinutes,
        @Min(0) @Max(3600) int cooldownSeconds,@NotBlank String resetUrl,@NotBlank @Email String from) {
    public PasswordResetProperties {
        if(resetUrl!=null) {
            URI uri=URI.create(resetUrl);
            boolean loopback=Set.of("localhost","127.0.0.1","[::1]").contains(uri.getHost()==null?"":uri.getHost());
            if(uri.getHost()==null || uri.getRawUserInfo()!=null || uri.getRawQuery()!=null || uri.getRawFragment()!=null
                    || !("https".equalsIgnoreCase(uri.getScheme()) || (loopback && "http".equalsIgnoreCase(uri.getScheme()))))
                throw new IllegalArgumentException("Password reset URL must be a trusted HTTPS URL, or loopback HTTP, without query/fragment/userinfo");
        }
    }
}
