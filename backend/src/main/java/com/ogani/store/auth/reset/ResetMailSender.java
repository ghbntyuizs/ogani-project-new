package com.ogani.store.auth.reset;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ResetMailSender {
    private final JavaMailSender sender;
    private final PasswordResetProperties props;
    public void send(String email,String token) {
        var message=new SimpleMailMessage();message.setFrom(props.from());message.setTo(email);
        message.setSubject("Ogani password reset");
        message.setText("Use this link to reset your Ogani password within "+props.ttlMinutes()+" minutes:\n\n"
                +props.resetUrl()+"#token="+token+"\n\nIf you did not request this, ignore this email. Never share this link.");
        sender.send(message);
    }
}
