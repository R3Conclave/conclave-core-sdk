package com.r3.conclave.cordapp.sample.enclave;

import com.r3.conclave.enclave.Enclave;
import com.r3.conclave.mail.EnclaveMail;
import com.r3.conclave.mail.MutableMail;

import java.security.PublicKey;

/**
 * Simply reverses the bytes that are passed in.
 */
public class ReverseEnclave extends Enclave {
    private static String reverse(String input) {
        StringBuilder builder = new StringBuilder(input.length());
        for (int i = input.length() - 1; i >= 0; i--)
            builder.append(input.charAt(i));
        return builder.toString();
    }

    @Override
    protected void receiveMail(long id, String routingHint, EnclaveMail mail) {
        // Reverse it and re-encode to UTF-8 to send back.
        final byte[] reversedEncodedString = reverse(new String(mail.getBodyAsBytes())).getBytes();
        // Check the client that sent the mail set things up so we can reply.
        final PublicKey sender = mail.getAuthenticatedSender();
        if (sender == null)
            throw new IllegalArgumentException("Mail sent to this enclave must be authenticated so we can reply.");
        // Create and send back the mail to the sender.
        final MutableMail reply = createMail(sender, reversedEncodedString);
        postMail(reply, routingHint);
    }
}
