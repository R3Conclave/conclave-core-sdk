package com.r3.conclave.sample.client;

import com.r3.conclave.client.EnclaveClient;
import com.r3.conclave.client.web.WebEnclaveTransport;
import com.r3.conclave.common.EnclaveConstraint;
import com.r3.conclave.common.InvalidEnclaveException;
import com.r3.conclave.mail.EnclaveMail;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "reverse-client",
        mixinStandardHelpOptions = true,
        description = "Simple client that communicates with the ReverseEnclave using the web host.")
public class ReverseClient implements Callable<Void> {
    @Parameters(index = "0", description = "The string to send to the enclave to reverse.")
    private String string;

    @Option(names = {"-u", "--url"},
            required = true,
            description = "URL of the web host running the enclave.")
    private String url;

    @Option(names = {"-c", "--constraint"},
            required = true,
            description = "Enclave constraint which determines the enclave's identity and whether it's acceptable to use.",
            converter = EnclaveConstraintConverter.class)
    private EnclaveConstraint constraint;

    @Option(names = {"-f", "--file-state"},
            required = true,
            description = "File to store the state of the client. If the file doesn't exist a new one will be created.")
    private Path file;

    @Override
    public Void call() throws IOException, InvalidEnclaveException {
        EnclaveClient enclaveClient;
        if (Files.exists(file)) {
            enclaveClient = new EnclaveClient(Files.readAllBytes(file));
            System.out.println("Loaded previous client state and thus using existing private key.");
        } else {
            System.out.println("No previous client state. Generating new state with new private key.");
            enclaveClient = new EnclaveClient(constraint);
        }

        try (WebEnclaveTransport transport = new WebEnclaveTransport(url);
             EnclaveClient client = enclaveClient)
        {
            client.start(transport);
            byte[] requestMailBody = string.getBytes(StandardCharsets.UTF_8);
            EnclaveMail responseMail = client.sendMail(requestMailBody);
            String responseString = (responseMail != null) ? new String(responseMail.getBodyAsBytes()) : null;
            System.out.println("Reversing `" + string + "` gives `" + responseString + "`");
            Files.write(file, client.save());
        }

        return null;
    }

    private static class EnclaveConstraintConverter implements ITypeConverter<EnclaveConstraint> {
        @Override
        public EnclaveConstraint convert(String value) {
            return EnclaveConstraint.parse(value);
        }
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new ReverseClient()).execute(args);
        System.exit(exitCode);
    }
}
