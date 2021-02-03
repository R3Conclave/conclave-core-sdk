package com.r3.conclave.cordapp.sample.enclave;

import com.r3.conclave.enclave.Enclave;
import com.r3.conclave.enclave.EnclavePostOffice;
import com.r3.conclave.mail.EnclaveMail;

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
    protected void receiveMail(long id, EnclaveMail mail, String routingHint) {
        // Reverse it and re-encode to UTF-8 to send back.
        final byte[] reversedEncodedString = reverse(new String(mail.getBodyAsBytes())).getBytes();
        // Get the PostOffice instance for responding back to this mail. Our response will use the same topic.
        final EnclavePostOffice postOffice = postOffice(mail);
        // Create the encrypted response and send it back to the sender.
        final byte[] reply = postOffice.encryptMail(reversedEncodedString);
        postMail(reply, routingHint);
    }
}
