package com.r3.conclave.sample.client;

import com.r3.conclave.common.EnclaveConstraint;
import com.r3.conclave.common.EnclaveInstanceInfo;
import com.r3.conclave.common.InvalidEnclaveException;
import com.r3.conclave.mail.Curve25519PrivateKey;
import com.r3.conclave.mail.EnclaveMail;
import com.r3.conclave.mail.PostOffice;
import com.r3.conclave.shaded.jackson.databind.JsonNode;
import com.r3.conclave.shaded.jackson.databind.ObjectMapper;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.entity.EntityBuilder;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Simple client to interact with enclave using REST API.
 *
 * Note: This is experimental code and as such is subject to change.
 * Note 2: The current implementation does not support HTTPS protocol.
 *         You might set up a "HTTPS To HTTP Reverse Proxy" as a workaround.
 */
public class Client implements Closeable {
    /**
     * Retrieves enclave attestation info and validates it.
     *
     * @throws IOException             thrown if host is not available or there is a problem with mail state file
     * @throws InvalidEnclaveException thrown if there is a problem with attestation
     */
    public void connect() throws IOException, InvalidEnclaveException {
        httpClient = HttpClients.createDefault();
        enclaveInstanceInfo = getEnclaveInstanceInfo();
        postOffice = buildPostOffice(enclaveInstanceInfo);
    }

    /**
     * Delivers message to an enclave, the reply (if any) will go into a specified mailbox.
     *
     * @param correlationId _uniquely_ identifies a mailbox
     * @param message       bytes to be sent
     * @throws IOException thrown if there is a problem communicating with the host
     *                     or with the enclave
     *                     or HTTP response code is not 200
     */
    public void deliverMail(String correlationId, byte[] message) throws IOException {
        byte[] encryptedMail = postOffice.encryptMail(message);
        HttpPost post = new HttpPost(domain + "/deliver-mail");
        post.addHeader("Correlation-ID", correlationId);
        post.setEntity(EntityBuilder.create().setBinary(encryptedMail).build());

        StatusLine status = httpClient.execute(post).getStatusLine();
        if (status.getStatusCode() != HttpStatus.SC_OK)
            throw new IOException(status.toString());
    }

    /**
     * The client won't be notified of any new messages from the enclave,
     * you have to call `checkInbox` to get the messages.
     * All messages pulled will be removed from the mailbox.
     *
     * @param correlationId _uniquely_ identifies a mailbox
     * @return retrieved mail as list of EnclaveMail objects
     * @throws Exception thrown if there is a problem communicating with the host
     *                   or HTTP response code is not 200
     */
    public List<EnclaveMail> checkInbox(String correlationId) throws IOException {
        HttpPost post = new HttpPost(domain + "/inbox");
        post.addHeader("Correlation-ID", correlationId);
        HttpResponse response = httpClient.execute(post);
        StatusLine status = response.getStatusLine();
        if (status.getStatusCode() != HttpStatus.SC_OK)
            throw new IOException(status.toString());

        ArrayList<EnclaveMail> inboundMail = new ArrayList<>();
        // retrieved json represents a list of encrypted mail messages
        JsonNode json = new ObjectMapper().readTree(response.getEntity().getContent());
        for (JsonNode node : json) {
            inboundMail.add(postOffice.decryptMail(node.binaryValue()));
        }
        return inboundMail;
    }

    /**
     * Client needs to know where the WebHost is
     * and where to store the mail state.
     *
     * @param domain        points to running WebHost (i.e. http://my.web.host:8080)
     * @param mailStateFile this is where the mail state (including mailSequenceNumber) is stored
     *
     * @throws IllegalArgumentException if `domain` string violates RFC 2396
     */
    public Client(String domain, String mailStateFile) throws IllegalArgumentException {
        this.domain = domain;
        this.mailStateFile = mailStateFile;

        // exploit uri syntax check
        URI.create(domain);
    }

    /**
     * Saves mail state.
     * Close HTTP connected.
     *
     * @throws IOException
     */
    public void close() throws IOException {
        persistMailState();
        httpClient.close();
    }

    /**
     * Load mail client state from text file:
     * [0] base64-encoded-private-key
     * [1] mail topic name
     * [2] next sequence number
     * [3] last enclave state id
     */
    private State loadMailState() throws IOException {
        // in case you don't care about mail state
        if (this.mailStateFile != null) {
            Path file = FileSystems.getDefault().getPath(this.mailStateFile);
            if (Files.exists(file)) {
                List<String> lines = Files.readAllLines(file);
                return new State(
                        new Curve25519PrivateKey(Base64.getDecoder().decode(lines.get(0))),
                        lines.get(1),
                        Long.parseLong(lines.get(2)),
                        decodeStateId(lines.get(3))
                );
            }
        }
        return new State();
    }

    private void persistMailState() throws IOException {
        // in case you don't care about mail state
        if (this.mailStateFile != null) {
            Path path = FileSystems.getDefault().getPath(this.mailStateFile);
            ArrayList<String> lines = new ArrayList<>();
            lines.add(Base64.getEncoder().encodeToString(postOffice.getSenderPrivateKey().getEncoded()));
            lines.add(postOffice.getTopic());
            lines.add(Long.toString(postOffice.getNextSequenceNumber()));
            lines.add(encodeStateId(postOffice.getLastSeenStateId()));
            Files.write(path, lines);
        }
    }

    // using hex string and NULL for readability
    private static byte[] decodeStateId(String s) {
        int len = s.length();
        if (len == 0 || s.equals("NULL"))
            return null;

        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    private static String encodeStateId(byte[] bytes) {
        if (bytes == null || bytes.length == 0)
            return "NULL";

        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    private PostOffice buildPostOffice(EnclaveInstanceInfo enclaveInstanceInfo) throws IOException {
        State state = loadMailState();
        PostOffice postOffice = enclaveInstanceInfo.createPostOffice(state.identityKey, state.topic);
        postOffice.setNextSequenceNumber(state.nextMailSequenceN);
        postOffice.setLastSeenStateId(state.stateId);
        return postOffice;
    }

    private EnclaveInstanceInfo getEnclaveInstanceInfo() throws IOException, InvalidEnclaveException {
        HttpResponse response = httpClient.execute(new HttpGet(domain + "/attestation"));
        StatusLine status = response.getStatusLine();
        if (status.getStatusCode() != HttpStatus.SC_OK)
            throw new IOException(status.toString());

        int len = (int) response.getEntity().getContentLength();
        ByteArrayOutputStream baos = new ByteArrayOutputStream(len);
        response.getEntity().writeTo(baos);

        byte[] attestationBytes = baos.toByteArray();
        EnclaveInstanceInfo attestation = EnclaveInstanceInfo.deserialize(attestationBytes);
        // Check it's the enclave we expect. This will throw InvalidEnclaveException if not valid.
        System.out.println("Connected to " + attestation);
        // Three distinct signing key hashes can be accepted.
        // Release mode:            360585776942A4E8A6BD70743E7C114A81F9E901BF90371D27D55A241C738AD9
        // Debug/Simulation mode:   4924CA3A9C8241A3C0AA1A24A407AA86401D2B79FA9FF84932DA798A942166D4
        // Mock mode:               0000000000000000000000000000000000000000000000000000000000000000
        EnclaveConstraint.parse("S:360585776942A4E8A6BD70743E7C114A81F9E901BF90371D27D55A241C738AD9 "
                + "S:4924CA3A9C8241A3C0AA1A24A407AA86401D2B79FA9FF84932DA798A942166D4 "
                + "S:0000000000000000000000000000000000000000000000000000000000000000 PROD:1 SEC:INSECURE").check(attestation);
        return attestation;
    }

    @Override
    public String toString() {
        StringBuffer out = new StringBuffer();
        out.append("URI: " + domain + "\n");
        out.append(enclaveInstanceInfo.toString());
        return out.toString();
    }

    private EnclaveInstanceInfo enclaveInstanceInfo;
    private PostOffice postOffice;

    private String domain;
    private String mailStateFile;

    private CloseableHttpClient httpClient;

    private static class State {
        public PrivateKey identityKey;
        public String topic;
        public long nextMailSequenceN;
        public byte[] stateId;

        State() {
            this.identityKey = Curve25519PrivateKey.random();
            this.topic = UUID.randomUUID().toString();
            this.nextMailSequenceN = 0;
            this.stateId = null;
        }

        State(PrivateKey identityKey, String topic, long nextMailSequenceN, byte[] stateId) {
            this.identityKey = identityKey;
            this.topic = topic;
            this.nextMailSequenceN = nextMailSequenceN;
            this.stateId = stateId;
        }

        @Override
        public String toString() {
            return "key=" + identityKey + ", topic=" + topic + ", seq=" + nextMailSequenceN + ", state=" + encodeStateId(stateId);
        }
    }
}
