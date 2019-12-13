package avian.test;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class FileSystemEmulation {

    public static void checkReadFromJavaHome() throws IOException {
        FileInputStream fis = new FileInputStream("/avian-embedded/javahomeJar/lib/currency.data");
        try {
            byte[] buffer = new byte[4];
            fis.read(buffer);
            fis.read(buffer, 0, 1);
        } finally {
            fis.close();
        }
    }

    public static void checkFileNotFound() {
        boolean passed = false;
        try {
            FileInputStream fis = new FileInputStream("/test");
        } catch (FileNotFoundException f) {
            passed = true;
        }
        if (!passed) {
            throw new RuntimeException("Failed");
        }
    }

    public static void main(String[] args) throws IOException {
        checkReadFromJavaHome();
        checkFileNotFound();
    }
}
