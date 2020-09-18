package com.r3.conclave.integrationtests.djvm.sandboxtests;

import com.google.protobuf.InvalidProtocolBufferException;
import com.r3.conclave.integrationtests.djvm.base.DJVMBase;
import com.r3.conclave.integrationtests.djvm.base.EnclaveJvmTest;
import com.r3.conclave.integrationtests.djvm.base.TestSerializable;
import com.r3.conclave.integrationtests.djvm.sandboxtests.proto.StringList;
import com.r3.conclave.integrationtests.djvm.sandboxtests.util.DummyJar;
import com.r3.conclave.integrationtests.djvm.sandboxtests.util.JarStreamer;
import com.r3.conclave.integrationtests.djvm.sandboxtests.util.SerializationUtils;
import com.r3.conclave.integrationtests.djvm.sandboxtests.proto.StringList;
import com.r3.conclave.integrationtests.djvm.sandboxtests.util.DummyJar;
import com.r3.conclave.integrationtests.djvm.sandboxtests.util.JarStreamer;
import com.r3.conclave.integrationtests.djvm.sandboxtests.util.SerializationUtils;
import net.corda.djvm.TypedTaskFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;

import static com.r3.conclave.integrationtests.djvm.sandboxtests.util.DummyJar.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.zip.Deflater.NO_COMPRESSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

public class JarInputStreamTest {

    public static class ReadingDataTestEnclaveTest extends DJVMBase implements EnclaveJvmTest, TestSerializable {

        private static final int DATA_SIZE = 512;

        @NotNull
        @Override
        public byte[] getTestInput() {
            try {
                DummyJar jarstream = new DummyJar(Files.createTempDirectory(JarInputStreamTest.class.getName()), "jarstream").build((jar, path) -> {
                    jar.setComment(JarInputStreamTest.class.getName());
                    jar.setLevel(NO_COMPRESSION);

                    // One directory entry (stored)
                    putDirectoryOf(jar, JarInputStreamTest.ReadingDataTestEnclaveTest.class);

                    // One compressed class file
                    putCompressedClass(jar, JarInputStreamTest.ReadingDataTestEnclaveTest.class);

                    // One compressed non-class file
                    putCompressedEntry(jar, "binary.dat", arrayOfJunk(DATA_SIZE));

                    // One uncompressed text file
                    String text = "Jar: " + path.toAbsolutePath() + System.lineSeparator()
                            + "Class: " + JarInputStreamTest.ReadingDataTestEnclaveTest.class.getName()
                            + System.lineSeparator();
                    putUncompressedEntry(jar, "comment.txt", text.getBytes(UTF_8));
                });
                return Files.readAllBytes(jarstream.getPath());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            InputStream inputStream = (InputStream) input;
            sandbox(ctx -> {
                try {
                    TypedTaskFactory taskFactory = ctx.getClassLoader().createTypedTaskFactory();
                    String[] result = WithJava.run(taskFactory, JarStreamer.class, inputStream);
                    assertThat(result).isNotNull();
                    assertThat(result)
                            .isEqualTo(new String[] {
                                    "Manifest-Version: 1.0\r\n\r\n",
                                    directoryOf(getClass()).getName(),
                                    getResourceName(getClass()),
                                    "binary.dat",
                                    "comment.txt"
                            });
                    output.set(result);
                } catch (Exception e) {
                    fail(e);
                }
                return null;
            });
            return output.get();
        }

        @Override
        public byte[] serializeTestOutput(Object output) {
            return SerializationUtils.serializeStringArray(output).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            try {
                String[] result = StringList.parseFrom(testResult).getValuesList().toArray(new String[0]);
                assertThat(result)
                        .isEqualTo(new String[] {
                                "Manifest-Version: 1.0\r\n\r\n",
                                directoryOf(getClass()).getName().replaceAll("/asserters",""),
                                getResourceName(getClass()).replaceAll("/asserters",""),
                                "binary.dat",
                                "comment.txt"
                        });
            } catch(InvalidProtocolBufferException e) {
                throw new RuntimeException(e.getMessage());
            }
        }

        @Nullable
        @Override
        public Object deserializeTestInput(@NotNull byte[] data) {
            return new ByteArrayInputStream(data);
        }
    }

}
