package com.r3.conclave.jvmtester.djvm.testsauxiliary;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import static java.nio.charset.StandardCharsets.UTF_8;

public class JarStreamer implements Function<InputStream, String[]> {
    @Override
    public String[] apply(InputStream input) {
        try (
                JarInputStream jar = new JarInputStream(input);
                ByteArrayOutputStream baos = new ByteArrayOutputStream()
        ) {
            Manifest manifest = jar.getManifest();
            manifest.write(baos);

            List<String> entries = new LinkedList<>();
            entries.add(new String(baos.toByteArray(), UTF_8));

            JarEntry entry;
            while ((entry = jar.getNextJarEntry()) != null) {
                entries.add(entry.getName());
            }
            return entries.toArray(new String[0]);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
