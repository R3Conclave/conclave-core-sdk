package com.r3.conclave.sample.enclave;

import com.r3.conclave.enclave.Enclave;
import com.r3.conclave.mail.EnclaveMail;

/**
 * Simply reverses the bytes that are passed in.
 */
public class ReverseEnclave extends Enclave {
    // We store the previous result to showcase that the enclave internals can be examined in a mock test.
    byte[] previousResult;

    @Override
    protected byte[] receiveFromUntrustedHost(byte[] bytes) {
        // This is used for host->enclave calls so we don't have to think about authentication.
        final var input = new String(bytes);
        var result = reverse(input).getBytes();
        previousResult = result;

        return result;
    }

    private static String reverse(String input) {
        var builder = new StringBuilder(input.length());
        for (var i = input.length() - 1; i >= 0; i--)
            builder.append(input.charAt(i));
        return builder.toString();
    }

    @Override
    protected void receiveMail(EnclaveMail mail, String routingHint) {
        // This is used when the host delivers a message from the client.
        // First, decode mail body as a String.
        final var stringToReverse = new String(mail.getBodyAsBytes());
        // Reverse it and re-encode to UTF-8 to send back.
        final var reversedEncodedString = reverse(stringToReverse).getBytes();
        // Get the post office object for responding back to this mail and use it to encrypt our response.
        final var responseBytes = postOffice(mail).encryptMail(reversedEncodedString);
        postMail(responseBytes, routingHint);
    }
}
