package com.arcadigitalis.backend.notifications;

import com.arcadigitalis.backend.persistence.entity.EventRecordEntity;
import com.arcadigitalis.backend.persistence.entity.NotificationTargetEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailDelivery {

    private final JavaMailSender mailSender;

    @Value("${arca.notifications.enabled:false}")
    private boolean enabled;

    @Value("${spring.mail.username:noreply@arcadigitalis.com}")
    private String fromAddress;

    public EmailDelivery(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void send(NotificationTargetEntity target, EventRecordEntity event) {
        if (!enabled) {
            throw new NotificationDispatcher.DeliveryException("Email notifications not enabled");
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(target.getChannelValue());
            message.setSubject("Arca Package Event: " + event.getEventType());
            message.setText(buildEmailBody(event));

            mailSender.send(message);

        } catch (Exception e) {
            throw new NotificationDispatcher.DeliveryException("Email delivery failed", e);
        }
    }

    private String buildEmailBody(EventRecordEntity event) {
        return String.format("""
            Event Type: %s
            Package Key: %s
            Block Number: %d
            Transaction: %s
            Timestamp: %s

            Data: %s
            """,
            event.getEventType(),
            event.getPackageKey(),
            event.getBlockNumber(),
            event.getTxHash(),
            event.getBlockTimestamp(),
            event.getRawData()
        );
    }
}
