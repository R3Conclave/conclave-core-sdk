package com.r3.conclave.cordapp.sample.enclave;

import com.r3.conclave.cordapp.common.SenderIdentity;
import com.r3.conclave.enclave.EnclavePostOffice;
import com.r3.conclave.mail.EnclaveMail;

import java.nio.charset.StandardCharsets;

/**
 * Simply reverses the bytes that are passed in.
 */
public class ReverseEnclave extends CordaEnclave {
    private static String reverse(String input) {
        StringBuilder builder = new StringBuilder(input.length());
        for (int i = input.length() - 1; i >= 0; i--)
            builder.append(input.charAt(i));
        return builder.toString();
    }

    @Override
    protected void receiveMail(EnclaveMail mail, String routingHint, SenderIdentity identity) {
        String reversedString = reverse(new String(mail.getBodyAsBytes()));

        String responseString;
        if (identity == null) {
            responseString = String.format("Reversed string: %s; Sender name: <Anonymous>", reversedString);
        } else {
            responseString = String.format("Reversed string: %s; Sender name: %s", reversedString, identity.getName());
        }

        // Get the PostOffice instance for responding back to this mail. Our response will use the same topic.
        final EnclavePostOffice postOffice = postOffice(mail);
        // Create the encrypted response and send it back to the sender.
        final byte[] reply = postOffice.encryptMail(responseString.getBytes(StandardCharsets.UTF_8));
        postMail(reply, routingHint);
    }
}
