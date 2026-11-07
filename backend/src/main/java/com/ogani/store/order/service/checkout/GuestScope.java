package com.ogani.store.order.service.checkout;

import com.ogani.store.common.exception.AppException;
import com.ogani.store.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

@Component
public class GuestScope {
    private final SecureRandom random=new SecureRandom();
    public String initialize(HttpServletRequest request,HttpServletResponse response) {
        String raw=raw(request);
        if(raw==null){byte[] bytes=new byte[32];random.nextBytes(bytes);raw=HexFormat.of().formatHex(bytes);}
        response.addHeader("Set-Cookie",ResponseCookie.from("ogani_guest",raw).httpOnly(true).secure(request.isSecure())
                .sameSite("Strict").path("/api/guest").maxAge(Duration.ofDays(7)).build().toString());
        return CheckoutFingerprint.hash(List.of(raw));
    }
    public String require(HttpServletRequest request) {
        String raw=raw(request);
        if(raw==null)throw new AppException(ErrorCode.UNAUTHENTICATED);
        return CheckoutFingerprint.hash(List.of(raw));
    }
    private String raw(HttpServletRequest request) {
        if(request.getCookies()!=null)for(var cookie:request.getCookies())
            if(cookie.getName().equals("ogani_guest") && cookie.getValue().matches("[0-9a-f]{64}"))return cookie.getValue();
        return null;
    }
}
