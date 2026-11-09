package pl.jollycart.auth;

import java.util.Properties;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import pl.jollycart.config.AppConfig;

/** Wysylka e-maili przez SMTP (Jakarta Mail). */
public final class EmailService {

    private final Session session;
    private final String from;

    public EmailService(AppConfig config) {
        Properties props = new Properties();
        props.put("mail.smtp.host", config.mailHost());
        props.put("mail.smtp.port", String.valueOf(config.mailPort()));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "5000");
        props.put("mail.smtp.writetimeout", "5000");

        this.session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(config.mailUsername(), config.mailPassword());
            }
        });
        this.from = config.mailFrom();
    }

    public void sendVerificationCode(String to, String code, long validMinutes) {
        String text = "Twój kod weryfikacyjny: " + code + "\n\n"
                + "Kod jest ważny przez " + validMinutes + " minut.\n"
                + "Jeśli to nie Ty zakładasz konto w JollyCart, zignoruj tę wiadomość.";

        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(to, true));
            message.setSubject("JollyCart - kod weryfikacyjny", "UTF-8");
            message.setText(text, "UTF-8");

            Transport.send(message);
        } catch (MessagingException e) {
            throw new EmailSendException(
                    "Nie udało się wysłać wiadomości e-mail. Spróbuj ponownie za chwilę.",
                    e
            );
        }
    }
}
