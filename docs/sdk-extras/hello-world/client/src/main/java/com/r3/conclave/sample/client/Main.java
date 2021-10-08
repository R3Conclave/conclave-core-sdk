package com.r3.conclave.sample.client;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import com.r3.conclave.mail.EnclaveMail;

public class Main {

    /**
     * ./client "text to reverse"
     */
    public static void main(String[] args) throws Exception {
        // amend URI if required
        // amend mailStateFile as desired
        Client client = new Client("http://127.0.0.1:8080", null);
        client.connect();

        // correlationId identifies the mailbox
        String correlationId = UUID.randomUUID().toString();
        String input = String.join(" ", args);

        client.deliverMail(correlationId, input.getBytes(StandardCharsets.UTF_8));
        List<EnclaveMail> messages = client.checkInbox(correlationId);
        client.close();

        String actual = new String(messages.get(0).getBodyAsBytes());
        System.out.println("Reversing `" + input + "` gives `" + actual + "`");

        String expected = reverse(input);
        assert (actual == expected);
    }

    private static String reverse(String input) {
        StringBuilder builder = new StringBuilder(input.length());
        for (int i = input.length() - 1; i >= 0; i--)
            builder.append(input.charAt(i));
        return builder.toString();
    }
}
