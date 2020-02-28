package com.r3.sgx.djvm;

import com.google.protobuf.InvalidProtocolBufferException;
import com.r3.sgx.djvm.proto.StringList;
import com.r3.sgx.djvm.util.SerializationUtils;
import com.r3.sgx.test.EnclaveJvmTest;
import com.r3.sgx.test.serialization.TestSerializable;
import net.corda.djvm.execution.DeterministicSandboxExecutor;
import net.corda.djvm.execution.ExecutionSummaryWithResult;
import net.corda.djvm.execution.SandboxExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;

import static com.r3.sgx.djvm.DummyJar.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.zip.Deflater.NO_COMPRESSION;
import static org.assertj.core.api.Assertions.assertThat;

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
                SandboxExecutor<InputStream, String[]> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
                ExecutionSummaryWithResult<String[]> success = WithJava.run(executor, JarStreamer.class, inputStream);
                assertThat(success.getResult()).isNotNull();
                assertThat(success.getResult())
                        .isEqualTo(new String[] {
                                "Manifest-Version: 1.0\r\n\r\n",
                                DummyJar.directoryOf(getClass()).getName(),
                                DummyJar.getResourceName(getClass()),
                                "binary.dat",
                                "comment.txt"
                        });
                output.set(success.getResult());
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
                                DummyJar.directoryOf(getClass()).getName().replaceAll("/asserters",""),
                                DummyJar.getResourceName(getClass()).replaceAll("/asserters",""),
                                "binary.dat",
                                "comment.txt"
                        });
            } catch (InvalidProtocolBufferException e) {
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
