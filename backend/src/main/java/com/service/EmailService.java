package com.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;


@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.frontend.url}")
    private String frontend;


    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendPasswordResetEmail(String toWho, String rawToken) {
        String link = frontend + "/reset-password?token=" + rawToken;


        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom("no-reply@technix.com");
        message.setTo(toWho);
        message.setSubject("Password reset for your account");
        message.setText("Password reset link for your account \n"
                + link +
                "\nThe link expires in 30 minutes." +
                "\nIf you did not request this email, please ignore it.");
        mailSender.send(message);

    }

    // Sent instead of a reset link when the account was created with Google and has no password.
    // The API reply is the same 202 either way - only the inbox owner learns how they signed up.
    public void sendGoogleAccountEmail(String toWho) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("no-reply@technix.com");
        message.setTo(toWho);
        message.setSubject("Password reset for your account");
        message.setText("Someone asked to reset the password for this account, but it was created with Google "
                + "and has no password.\n"
                + "To log in, use \"Continue with Google\" on the login page:\n"
                + frontend + "/login"
                + "\nIf you did not request this email, please ignore it.");
        mailSender.send(message);
    }
}
