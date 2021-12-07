package com.r3.conclave.sample.client;

import com.r3.conclave.client.EnclaveClient;
import com.r3.conclave.client.web.WebEnclaveTransport;
import com.r3.conclave.common.EnclaveConstraint;
import com.r3.conclave.common.InvalidEnclaveException;
import com.r3.conclave.mail.EnclaveMail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ReverseClient {

    public static void main(String... args) throws IOException, InvalidEnclaveException {
        if (args.length != 2) {
            System.out.println("Simple client that communicates with the ReverseEnclave using the web host.");
            System.out.println("Usage: reverse-client ENCLAVE_CONSTRAINT STRING_TO_REVERSE\n" +
                    "  ENCLAVE_CONSTRAINT: Enclave constraint which determines the enclave's identity and whether it's " +
                    "acceptable to use.\n" +
                    "  STRING_TO_REVERSE: The string to send to the enclave to reverse.");
        }

        EnclaveConstraint constraint = EnclaveConstraint.parse(args[0]);
        String stringToReverse = args[1];

        callEnclave(constraint, stringToReverse);
    }

    public static void callEnclave(EnclaveConstraint constraint, String stringToReverse) throws IOException, InvalidEnclaveException {
        try (WebEnclaveTransport transport = new WebEnclaveTransport("http://localhost:8080");
             EnclaveClient client = new EnclaveClient(constraint)) {

            // Connect to the host and send the string to reverse
            client.start(transport);
            byte[] requestMailBody = stringToReverse.getBytes(StandardCharsets.UTF_8);
            EnclaveMail responseMail = client.sendMail(requestMailBody);

            // Parse and print out the response
            String responseString = (responseMail != null) ? new String(responseMail.getBodyAsBytes()) : null;
            System.out.println("Reversing `" + stringToReverse + "` gives `" + responseString + "`");
        }
    }
}