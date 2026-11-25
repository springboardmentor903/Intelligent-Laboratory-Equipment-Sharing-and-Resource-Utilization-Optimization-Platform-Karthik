package com.labresource.platform.notification.broker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationDeliveryBroker {
    private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryBroker.class);

    private final NotificationDeliveryConsumer consumer;

    public NotificationDeliveryBroker(NotificationDeliveryConsumer consumer) {
        this.consumer = consumer;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNotificationCreated(NotificationDeliveryEvent event) {
        log.debug("Dispatching notification delivery event for notification: {}", event.notificationId());
        consumer.processDelivery(event.notificationId());
    }
}
