package com.r3.conclave.template.client;

import com.r3.conclave.client.EnclaveClient;
import com.r3.conclave.client.web.WebEnclaveTransport;
import com.r3.conclave.common.EnclaveConstraint;
import com.r3.conclave.common.InvalidEnclaveException;
import com.r3.conclave.mail.EnclaveMail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

class TemplateEnclaveClient {
    public static void main(String[] args) throws IOException, InvalidEnclaveException {
        if (args.length != 1) {
            System.out.println("Usage: java -jar client.jar ENCLAVE_CONSTRAINT");
            System.exit(1);
        }

        EnclaveConstraint constraint = EnclaveConstraint.parse(args[0]);

        // The URL has been hardcoded to localhost:8080, but this will need to be parametrised in a real client.
        try (WebEnclaveTransport transport = new WebEnclaveTransport("http://localhost:8080");
             EnclaveClient client = new EnclaveClient(constraint))
        {
            client.start(transport);
            byte[] requestMailBody = "abc".getBytes(StandardCharsets.UTF_8);
            EnclaveMail responseMail = client.sendMail(requestMailBody);
            String responseString = (responseMail != null) ? new String(responseMail.getBodyAsBytes()) : null;
            System.out.println("Enclave returned " + responseString);
        }
    }
}
