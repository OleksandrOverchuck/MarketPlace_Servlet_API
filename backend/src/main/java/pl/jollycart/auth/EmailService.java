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

        send(to, "JollyCart - kod weryfikacyjny", text,
                "Nie udało się wysłać wiadomości e-mail. Spróbuj ponownie za chwilę.");
    }

    public void sendNewMessageNotification(String to, String recipientNickname, String senderNickname,
                                           String text, String attachmentName, String chatUrl) {
        StringBuilder body = new StringBuilder();

        body.append("Cześć ").append(recipientNickname).append("!\n\n");
        body.append(senderNickname).append(" wysłał(a) do Ciebie wiadomość w JollyCart:\n\n");

        if (text != null && !text.isBlank()) {
            String preview = text.length() > 500 ? text.substring(0, 500) + "..." : text;
            body.append("\"").append(preview).append("\"\n");
        }

        if (attachmentName != null && !attachmentName.isBlank()) {
            body.append("Załącznik: ").append(attachmentName).append("\n");
        }

        body.append("\nOdpowiedz w czacie: ").append(chatUrl).append("\n\n");
        body.append("Aby nie zasypywać Twojej skrzynki, kolejne wiadomości ")
                .append("z tej rozmowy w ciągu kilku minut nie będą ")
                .append("powodować nowych maili.");

        // usuwamy znaki nowej linii - ochrona przed wstrzyknieciem naglowkow
        String subject = "JollyCart - nowa wiadomość od " + senderNickname.replaceAll("[\\r\\n]", " ");

        send(to, subject, body.toString(), "Nie udało się wysłać wiadomości e-mail.");
    }

    private void send(String to, String subject, String text, String errorMessage) {
        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(to, true));
            message.setSubject(subject, "UTF-8");
            message.setText(text, "UTF-8");

            Transport.send(message);
        } catch (MessagingException e) {
            throw new EmailSendException(errorMessage, e);
        }
    }
}
