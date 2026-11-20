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

    // goes to the NEW address: clicking the link proves the user really owns it
    public void sendEmailChangeConfirmation(String newEmail, String rawToken) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("no-reply@technix.com");
        message.setTo(newEmail);
        message.setSubject("Confirm your new email address");
        message.setText("Confirm that this is the new email address for your account:\n"
                + frontend + "/confirm-email?token=" + rawToken
                + "\nThe link expires in 1 hour."
                + "\nIf you did not request this, please ignore this email.");
        mailSender.send(message);
    }

    // goes to the OLD address: the real owner hears about it even if someone else is using their session
    public void sendEmailChangeNotice(String oldEmail, String newEmail) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("no-reply@technix.com");
        message.setTo(oldEmail);
        message.setSubject("Email change requested for your account");
        message.setText("Someone asked to change your account's email to " + newEmail + ".\n"
                + "Nothing changes until the link sent to that address is clicked.\n"
                + "If this was not you, change your password now:\n"
                + frontend + "/account");
        mailSender.send(message);
    }
}
