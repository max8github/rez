package com.rezhub.reservation.telegramnotifier;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramNotifierTest {

    @Test
    void parsesRecipientId() {
        String botToken = "8675246662:AAEU2cYIShvwejG6ooHLt0ouKyQYZJtAPVA";
        String chatId = "8698799755";
        String recipientId = botToken + ":" + chatId;

        int lastColon = recipientId.lastIndexOf(':');
        assertThat(recipientId.substring(0, lastColon)).isEqualTo(botToken);
        assertThat(recipientId.substring(lastColon + 1)).isEqualTo(chatId);
    }
}
