package com.parcelshare.notification;

import com.parcelshare.common.AppProps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Sends email through any SMTP provider (Brevo, Resend, Gmail SMTP...). If no SMTP host is configured the message
 * is only logged, so local development and tests need no mail server. Sending never blocks or breaks a request.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final ObjectProvider<JavaMailSender> sender;
    private final AppProps props;

    public MailService(ObjectProvider<JavaMailSender> sender, AppProps props) {
        this.sender = sender;
        this.props = props;
    }

    @Async
    public void send(String to, String subject, String body) {
        JavaMailSender s = sender.getIfAvailable();
        if (s == null || props.mailFrom().isBlank()) {
            log.info("[mail not configured] to={} subject=\"{}\"\n{}", to, subject, body);
            return;
        }
        try {
            SimpleMailMessage m = new SimpleMailMessage();
            m.setFrom(props.mailFrom());
            m.setTo(to);
            m.setSubject(subject);
            m.setText(body);
            s.send(m);
        } catch (Exception e) {
            log.warn("Could not send email to {}: {}", to, e.getMessage());
        }
    }
}
