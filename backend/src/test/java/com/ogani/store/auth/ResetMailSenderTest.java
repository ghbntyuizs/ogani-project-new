package com.ogani.store.auth;

import com.ogani.store.auth.reset.PasswordResetProperties;
import com.ogani.store.auth.reset.ResetMailSender;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class ResetMailSenderTest {
    PasswordResetProperties props(String url) { return new PasswordResetProperties(15,3,20,50,15,60,url,"no-reply@ogani.local"); }
    @ParameterizedTest @ValueSource(strings={"http://example.test/reset","javascript:alert(1)","https://example.test/reset#token=x","https://example.test/reset?token=x","https://user:password@example.test/reset","//example.test/reset"})
    void rejectsUntrustedResetUrlConfigurations(String value) { assertThrows(IllegalArgumentException.class,() -> props(value)); }
    @Test void sendsARealSmtpMessageWithConfiguredLink() throws Exception {
        try(var server=new ServerSocket(0,1,InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(5000);var executor=Executors.newSingleThreadExecutor();
            try {
                var delivered=executor.submit(() -> receive(server));
                var smtp=new JavaMailSenderImpl();smtp.setHost(server.getInetAddress().getHostAddress());smtp.setPort(server.getLocalPort());
                smtp.getJavaMailProperties().setProperty("mail.smtp.connectiontimeout","2000");smtp.getJavaMailProperties().setProperty("mail.smtp.timeout","2000");
                String token="A".repeat(43),url="http://localhost/reset-password.html";
                new ResetMailSender(smtp,props(url)).send("buyer@example.test",token);
                var mime=new MimeMessage(Session.getInstance(new Properties()),new ByteArrayInputStream(delivered.get(5,TimeUnit.SECONDS).getBytes(StandardCharsets.UTF_8)));
                assertEquals("Ogani password reset",mime.getSubject());
                assertTrue(mime.getFrom()[0].toString().contains("no-reply@ogani.local"));
                assertTrue(mime.getAllRecipients()[0].toString().contains("buyer@example.test"));
                assertTrue(mime.getContent().toString().contains(url+"#token="+token));
                assertTrue(mime.getContent().toString().contains("15 minutes"));
            } finally { executor.shutdownNow();assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS)); }
        }
    }
    String receive(ServerSocket server) throws Exception {
        try(var socket=server.accept();var input=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
                var output=new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.UTF_8))) {
            socket.setSoTimeout(5000);write(output,"220 localhost SMTP test");StringBuilder data=new StringBuilder();boolean reading=false;
            String line;
            while((line=input.readLine())!=null) {
                if(reading) {
                    if(line.equals(".")) {reading=false;write(output,"250 queued");}
                    else data.append(line.startsWith("..")?line.substring(1):line).append("\r\n");
                } else if(line.startsWith("DATA")) {reading=true;write(output,"354 end with dot");}
                else if(line.startsWith("QUIT")) {write(output,"221 bye");break;}
                else write(output,"250 OK");
            }
            return data.toString();
        }
    }
    void write(BufferedWriter output,String value) throws IOException {output.write(value+"\r\n");output.flush();}
}
