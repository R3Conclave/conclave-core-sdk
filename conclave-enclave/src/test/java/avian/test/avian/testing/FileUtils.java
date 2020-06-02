package avian.test.avian.testing;

import avian.test.avian.OcallReadResourceBytes;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

public class FileUtils {

    @NotNull
    public static URL createInMemory(String path) throws IOException {
        byte[] jar = OcallReadResourceBytes.readBytes(path);

        final Map<String, byte[]> map = new HashMap<>();

        try (JarInputStream is = new JarInputStream(new ByteArrayInputStream(jar))) {
            JarEntry nextEntry;
            while ((nextEntry = is.getNextJarEntry()) != null) {
                final int est = (int) nextEntry.getSize();
                byte[] data = new byte[est > 0 ? est : 1024];
                int real = 0;
                for (int r = is.read(data); r > 0; r = is.read(data, real, data.length - real)) {
                    if (data.length == (real += r)) {
                        data = Arrays.copyOf(data, data.length * 2);
                    }
                }
                if (real != data.length) {
                    data = Arrays.copyOf(data, real);
                }
                map.put("/" + nextEntry.getName(), data);
            }
        }

        URL u = new URL("x-buffer", null, -1, "/", new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL u) throws java.io.IOException {
                final byte[] data = map.get(u.getFile());
                if (data == null) {
                    throw new FileNotFoundException(u.getFile());
                }
                return new URLConnection(u) {
                    @Override
                    public void connect() throws IOException {
                    }

                    @Override
                    public InputStream getInputStream() throws IOException {
                        return new ByteArrayInputStream(data);
                    }
                };
            }
        });
        return u;
    }
}
