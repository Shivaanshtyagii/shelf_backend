package com.shelf.accounts.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class AccountMail {
    private static final Logger LOG = LoggerFactory.getLogger(AccountMail.class);
    private final JavaMailSender mail;
    private final String origin;
    private final String from;

    AccountMail(JavaMailSender mail, @Value("${shelf.web-origin}") String origin,
                @Value("${shelf.mail-from}") String from) {
        this.mail = mail;
        this.origin = origin;
        this.from = from;
    }

    @TransactionalEventListener
    void send(AccountService.MailChallenge challenge) {
        var verify = challenge.purpose().equals("VERIFY");
        var title = verify ? "Verify your Shelf email" : "Reset your Shelf password";
        var url = origin + "/account?mode=" + (verify ? "verify" : "reset") + "&token=" + challenge.token();
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(challenge.email());
        message.setSubject(title);
        message.setText(title + "\n\n" + url + "\n\nIf you did not request this link, you can ignore this email.");
        try {
            mail.send(message);
        } catch (MailException ex) {
            LOG.warn("Account email delivery failed for challenge type {}", challenge.purpose());
        }
    }
}
