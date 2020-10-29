package com.r3.conclave.sample.enclave;

import com.r3.conclave.enclave.Enclave;
import com.r3.conclave.mail.EnclaveMail;
import com.r3.conclave.mail.MutableMail;

import java.security.PublicKey;

/**
 * Simply reverses the bytes that are passed in.
 */
public class ReverseEnclave extends Enclave {
    // We store the previous result to showcase that the enclave internals can be examined in a mock test.
    byte[] previousResult;

    @Override
    protected byte[] receiveFromUntrustedHost(byte[] bytes) {
        // This is used for host->enclave calls so we don't have to think about authentication.
        final String input = new String(bytes);
        byte[] result = reverse(input).getBytes();
        previousResult = result;
        return result;
    }

    private static String reverse(String input) {
        StringBuilder builder = new StringBuilder(input.length());
        for (int i = input.length() - 1; i >= 0; i--)
            builder.append(input.charAt(i));
        return builder.toString();
    }

    @Override
    protected void receiveMail(long id, String routingHint, EnclaveMail mail) {
        // This is used when the host delivers a message from the client.
        // First, decode mail body as a String.
        final String stringToReverse = new String(mail.getBodyAsBytes());
        // Reverse it and re-encode to UTF-8 to send back.
        final byte[] reversedEncodedString = reverse(stringToReverse).getBytes();
        // Check the client that sent the mail set things up so we can reply.
        final PublicKey sender = mail.getAuthenticatedSender();
        if (sender == null)
            throw new IllegalArgumentException("Mail sent to this enclave must be authenticated so we can reply.");
        // Create and send back the mail with the same topic as the sender used.
        final MutableMail reply = createMail(sender, reversedEncodedString);
        reply.setTopic(mail.getTopic());
        postMail(reply, routingHint);
    }
}
