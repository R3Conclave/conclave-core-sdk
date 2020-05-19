package avian.test;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.security.Security;

import avian.test.avian.testing.Asserts;

public class SerialFilterTest {

    public static void main(String[] args) {
        // date object
        byte[] serializedData = new byte[]{
                (byte) 0xAC, (byte) 0xED, (byte) 0x00, (byte) 0x05, (byte) 0x73, (byte) 0x72, (byte) 0x00, (byte) 0x0E, (byte) 0x6A, (byte) 0x61, (byte) 0x76, (byte) 0x61, (byte) 0x2E, (byte) 0x75, (byte) 0x74, (byte) 0x69,
                (byte) 0x6C, (byte) 0x2E, (byte) 0x44, (byte) 0x61, (byte) 0x74, (byte) 0x65, (byte) 0x68, (byte) 0x6A, (byte) 0x81, (byte) 0x01, (byte) 0x4B, (byte) 0x59, (byte) 0x74, (byte) 0x19, (byte) 0x03, (byte) 0x00,
                (byte) 0x00, (byte) 0x78, (byte) 0x70, (byte) 0x77, (byte) 0x08, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x71, (byte) 0xEA, (byte) 0xC7, (byte) 0x20, (byte) 0x58, (byte) 0x78
        };

        System.out.println("system.property: jdk.serialFilter=" + System.getProperty("jdk.serialFilter"));
        System.out.println("security.property: jdk.serialFilter=" + Security.getProperty("jdk.serialFilter"));
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(serializedData);
            ObjectInputStream ois = new ObjectInputStream(bis);
            Object obj = ois.readObject();
            ois.close();

            System.out.println("de-serialization is expected to fail");
            Asserts.assertTrue(false);
        } catch (Exception ex) {
            String message = ex.toString();
            System.out.println(message);
            Asserts.assertTrue(message.endsWith("filter status: REJECTED"));
        }
    }

}

