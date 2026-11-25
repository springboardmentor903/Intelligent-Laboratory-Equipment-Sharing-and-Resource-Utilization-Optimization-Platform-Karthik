package com.labresource.platform.notification.channel;

import com.labresource.platform.notification.entity.Notification;
import com.labresource.platform.notification.entity.PushDeviceToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PushNotificationSender {
    private static final Logger log = LoggerFactory.getLogger(PushNotificationSender.class);

    public void sendPush(Notification notification, List<PushDeviceToken> deviceTokens) {
        if (deviceTokens == null || deviceTokens.isEmpty()) {
            throw new IllegalArgumentException("No device tokens available for push");
        }

        for (PushDeviceToken device : deviceTokens) {
            log.info("[PUSH DISPATCH] Token: {} | Platform: {} | Title: {}",
                    device.getToken(), device.getPlatform(), notification.getTitle());
        }
    }
}
