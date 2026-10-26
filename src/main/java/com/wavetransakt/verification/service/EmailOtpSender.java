package com.wavetransakt.verification.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.util.Properties;

/**
 * Email verification codes are sent only from the backend over authenticated
 * TLS SMTP. Secrets never leave the server or enter Android responses/logs.
 * Missing configuration fails closed; it never silently marks an email verified.
 */
@Service
public class EmailOtpSender {
    @Value("${wave.email.smtp.host:}") private String host;
    @Value("${wave.email.smtp.port:587}") private int port;
    @Value("${wave.email.smtp.username:}") private String username;
    @Value("${wave.email.smtp.password:}") private String password;
    @Value("${wave.email.from:}") private String from;
    @Value("${wave.email.smtp.starttls:true}") private boolean startTls;

    public boolean isConfigured() {
        return notBlank(host) && port > 0 && port <= 65535
                && notBlank(username) && notBlank(password)
                && notBlank(from) && startTls;
    }

    public void sendVerificationCode(String recipient, String code) {
        if (!isConfigured()) {
            throw new IllegalStateException("Email verification delivery is not configured");
        }
        if (recipient == null || !recipient.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("A valid email address is required");
        }
        if (code == null || !code.matches("\\d{6}")) {
            throw new IllegalArgumentException("Invalid email verification code format");
        }

        JavaMailSenderImpl mailer = new JavaMailSenderImpl();
        mailer.setHost(host.trim());
        mailer.setPort(port);
        mailer.setUsername(username.trim());
        mailer.setPassword(password);

        Properties properties = mailer.getJavaMailProperties();
        properties.put("mail.transport.protocol", "smtp");
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.starttls.required", "true");
        properties.put("mail.smtp.ssl.checkserveridentity", "true");
        properties.put("mail.smtp.connectiontimeout", "8000");
        properties.put("mail.smtp.timeout", "8000");
        properties.put("mail.smtp.writetimeout", "8000");

        SimpleMailMessage email = new SimpleMailMessage();
        email.setFrom(from.trim());
        email.setTo(recipient.trim());
        email.setSubject("Verify your Wave Transakt email");
        email.setText("Your Wave Transakt verification code is " + code +
                ". It expires in 10 minutes. Never share this code. " +
                "If you did not create an account, ignore this email.");
        try {
            mailer.send(email);
        } catch (MailException ex) {
            // Never expose SMTP provider errors, addresses, codes or credentials.
            throw new IllegalStateException(
                    "Email verification delivery is temporarily unavailable"
            );
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
