package com.r3.conclave.template.enclave;

import com.r3.conclave.host.AttestationParameters;
import com.r3.conclave.host.EnclaveHost;
import com.r3.conclave.host.EnclaveLoadException;
import com.r3.conclave.host.MailCommand;
import com.r3.conclave.mail.PostOffice;
import com.r3.conclave.mail.MailDecryptionException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TemplateEnclaveJavaTest {
    private static List<MailCommand> mailCommands = new ArrayList<>();

    @Test
    void firstTest() throws EnclaveLoadException, IOException, MailDecryptionException {
        // Start the enclave
        EnclaveHost mockHost = EnclaveHost.load("com.r3.conclave.template.enclave.TemplateEnclaveJava");
        mockHost.start(null, null, null, (commands) -> mailCommands.addAll(commands));

        // Deliver some mail
        PostOffice postOffice = mockHost.getEnclaveInstanceInfo().createPostOffice();
        byte[] mail = postOffice.encryptMail("abc".getBytes());
        mockHost.deliverMail(mail, "test");

        final MailCommand.PostMail reply = (MailCommand.PostMail) mailCommands.get(0);
        final byte[] responseBytes = reply.getEncryptedBytes();
        final byte[] decryptedBytes = postOffice.decryptMail(responseBytes).getBodyAsBytes();

        assertEquals("321", new String(decryptedBytes));
    }
}
