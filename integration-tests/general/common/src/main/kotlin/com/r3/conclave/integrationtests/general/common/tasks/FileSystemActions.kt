package com.r3.conclave.integrationtests.general.common.tasks

import com.r3.conclave.integrationtests.general.common.EnclaveContext
import com.r3.conclave.integrationtests.general.common.threadWithFuture
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.io.*
import java.net.URL
import java.nio.channels.ByteChannel
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.readBytes
import kotlin.io.path.writeText

@Serializable
abstract class FileSystemAction<R> : EnclaveTestAction<R>() {
    override fun createNewState() = State()

    class State {
        val outputStreams = ConcurrentHashMap<Int, OutputStream>()
        val inputStreams = ConcurrentHashMap<Int, InputStream>()
        val byteChannels = ConcurrentHashMap<Int, ByteChannel>()
    }
}

@Serializable
class PathsGet(private val path: String) : FileSystemAction<String>() {
    override fun run(context: EnclaveContext, isMail: Boolean): String = Paths.get(path).toString()
    override fun resultSerializer(): KSerializer<String> = String.serializer()
}

@Serializable
class FilesWrite(private val path: String, val bytes: ByteArray) : FileSystemAction<Unit>() {
    override fun run(context: EnclaveContext, isMail: Boolean) {
        Files.write(Paths.get(path), bytes)
    }
    override fun resultSerializer(): KSerializer<Unit> = Unit.serializer()
}

@Serializable
class DeleteFile(private val path: String, private val nioApi: Boolean) : FileSystemAction<Boolean>() {
    override fun run(context: EnclaveContext, isMail: Boolean) : Boolean {
        //  Note that java.io.File "delete" does not throw
        //  when the file is not present, while java.nio.Files "delete" does
        return if (nioApi) {
            Files.delete(Paths.get(path))
            true
        } else {
            val file = File(path)
            file.delete()
        }
    }
    override fun resultSerializer(): KSerializer<Boolean> = Boolean.serializer()
}

@Serializable
class FilesCreateDirectory(private val path: String) : FileSystemAction<Unit>() {
    override fun run(context: EnclaveContext, isMail: Boolean) {
        Files.createDirectory(Paths.get(path))
    }
    override fun resultSerializer(): KSerializer<Unit> = Unit.serializer()
}

@Serializable
class FilesCreateDirectories(private val path: String) : FileSystemAction<Unit>() {
    override fun run(context: EnclaveContext, isMail: Boolean) {
        Files.createDirectories(Paths.get(path))
    }
    override fun resultSerializer(): KSerializer<Unit> = Unit.serializer()
}

@Serializable
class FilesExists(private val path: String) : FileSystemAction<Boolean>() {
    override fun run(context: EnclaveContext, isMail: Boolean): Boolean = Files.exists(Paths.get(path))
    override fun resultSerializer(): KSerializer<Boolean> = Boolean.serializer()
}

@Serializable
class FilesSize(private val path: String) : FileSystemAction<Long>() {
    override fun run(context: EnclaveContext, isMail: Boolean): Long = Files.size(Paths.get(path))
    override fun resultSerializer(): KSerializer<Long> = Long.serializer()
}

@Serializable
class FilesReadAllBytes(private val path: String) : FileSystemAction<ByteArray>() {
    override fun run(context: EnclaveContext, isMail: Boolean): ByteArray = Files.readAllBytes(Paths.get(path))
    override fun resultSerializer(): KSerializer<ByteArray> = ByteArraySerializer()
}

@Serializable
class NewDeleteOnCloseOutputStream(private val path: String, private val uid: Int) : FileSystemAction<String>() {
    override fun run(context: EnclaveContext, isMail: Boolean): String {
        val outputStream = Files.newOutputStream(Paths.get(path), CREATE_NEW, WRITE, DELETE_ON_CLOSE)
        context.stateAs<State>().outputStreams[uid] = outputStream
        return outputStream.toString()
    }

    override fun resultSerializer(): KSerializer<String> = String.serializer()
}

@Serializable
class NewFileOuputStream(
    private val path: String,
    private val append: Boolean,
    private val uid: Int
) : FileSystemAction<String>() {
    override fun run(context: EnclaveContext, isMail: Boolean): String {
        val fos = FileOutputStream(path, append)
        context.stateAs<State>().outputStreams[uid] = fos
        return fos.toString()
    }

    override fun resultSerializer(): KSerializer<String> = String.serializer()
}

@Serializable
class NewInputStream(
    private val path: String,
    private val uid: Int,
    private val nioApi: Boolean
) : FileSystemAction<String>() {
    override fun run(context: EnclaveContext, isMail: Boolean): String {
        val fis = if (nioApi) Files.newInputStream(Paths.get(path), READ) else FileInputStream(path)
        context.stateAs<State>().inputStreams[uid] = fis
        return fis.toString()
    }

    override fun resultSerializer(): KSerializer<String> = String.serializer()
}

@Serializable
class ReadByteFromInputStream(private val uid: Int) : FileSystemAction<Int>() {
    override fun run(context: EnclaveContext, isMail: Boolean): Int {
        val inputStream = context.stateAs<State>().inputStreams.getValue(uid)
        return inputStream.read()
    }

    override fun resultSerializer(): KSerializer<Int> = Int.serializer()
}

@Serializable
class ReadBytesFromInputStream(private val uid: Int, val length: Int) : FileSystemAction<Int>() {
    override fun run(context: EnclaveContext, isMail: Boolean): Int {
        val inputStream = context.stateAs<State>().inputStreams.getValue(uid)
        return inputStream.read(ByteArray(length))
    }

    override fun resultSerializer(): KSerializer<Int> = Int.serializer()
}

@Serializable
class ReadAllBytesFromInputStream(private val uid: Int) : FileSystemAction<ByteArray>() {
    override fun run(context: EnclaveContext, isMail: Boolean): ByteArray {
        val inputStream = context.stateAs<State>().inputStreams.getValue(uid)
        return inputStream.readBytes()
    }

    override fun resultSerializer(): KSerializer<ByteArray> = ByteArraySerializer()
}

@Serializable
class IsInputStreamMarkSupported(private val uid: Int) : FileSystemAction<Boolean>() {
    override fun run(context: EnclaveContext, isMail: Boolean): Boolean {
        val inputStream = context.stateAs<State>().inputStreams.getValue(uid)
        return inputStream.markSupported()
    }

    override fun resultSerializer(): KSerializer<Boolean> = Boolean.serializer()
}

@Serializable
class CloseInputStream(private val uid: Int) : FileSystemAction<Unit>() {
    override fun run(context: EnclaveContext, isMail: Boolean) {
        val inputStream = context.stateAs<State>().inputStreams.getValue(uid)
        inputStream.close()
    }

    override fun resultSerializer(): KSerializer<Unit> = Unit.serializer()
}

@Serializable
class ResetInputStream(private val uid: Int) : FileSystemAction<Unit>() {
    override fun run(context: EnclaveContext, isMail: Boolean) {
        val inputStream = context.stateAs<State>().inputStreams.getValue(uid)
        inputStream.reset()
    }

    override fun resultSerializer(): KSerializer<Unit> = Unit.serializer()
}

@Serializable
class IsFileInputStreamFDValid(private val uid: Int) : FileSystemAction<Boolean>() {
    override fun run(context: EnclaveContext, isMail: Boolean): Boolean {
        val fis = context.stateAs<State>().inputStreams[uid] as FileInputStream
        return fis.fd.valid()
    }

    override fun resultSerializer(): KSerializer<Boolean> = Boolean.serializer()
}

@Serializable
class OpenUrlFileInputStream(private val path: String, private val uid: Int) : FileSystemAction<String>() {
    override fun run(context: EnclaveContext, isMail: Boolean): String {
        val inputStream = URL("file://$path").openStream()
        context.stateAs<State>().inputStreams[uid] = inputStream
        return inputStream.toString()
    }

    override fun resultSerializer(): KSerializer<String> = String.serializer()
}

@Serializable
class WriteByteToOuputStream(private val uid: Int, val byte: Int) : FileSystemAction<Unit>() {
    override fun run(context: EnclaveContext, isMail: Boolean) {
        val outputStream = context.stateAs<State>().outputStreams.getValue(uid)
        outputStream.write(byte)
    }

    override fun resultSerializer(): KSerializer<Unit> = Unit.serializer()
}

@Serializable
class WriteBytesToOuputStream(private val uid: Int, val bytes: ByteArray) : FileSystemAction<Unit>() {
    override fun run(context: EnclaveContext, isMail: Boolean) {
        val outputStream = context.stateAs<State>().outputStreams.getValue(uid)
        outputStream.write(bytes)
    }

    override fun resultSerializer(): KSerializer<Unit> = Unit.serializer()
}

@Serializable
class WriteOffsetBytesToOuputStream(
    private val uid: Int,
    private val bytes: ByteArray,
    private val offset: Int,
    private val length: Int
) : FileSystemAction<Unit>() {
    override fun run(context: EnclaveContext, isMail: Boolean) {
        val outputStream = context.stateAs<State>().outputStreams.getValue(uid)
        outputStream.write(bytes, offset, length)
    }

    override fun resultSerializer(): KSerializer<Unit> = Unit.serializer()
}

@Serializable
class CloseOuputStream(private val uid: Int) : FileSystemAction<Unit>() {
    override fun run(context: EnclaveContext, isMail: Boolean) {
        val outputStream = context.stateAs<State>().outputStreams.getValue(uid)
        outputStream.close()
    }

    override fun resultSerializer(): KSerializer<Unit> = Unit.serializer()
}

@Serializable
class NewDeleteOnCloseByteChannel(private val path: String, private val uid: Int) : FileSystemAction<String>() {
    override fun run(context: EnclaveContext, isMail: Boolean): String {
        val byteChannel = Files.newByteChannel(Paths.get(path), CREATE_NEW, WRITE, DELETE_ON_CLOSE)
        context.stateAs<State>().byteChannels[uid] = byteChannel
        return byteChannel.toString()
    }

    override fun resultSerializer(): KSerializer<String> = String.serializer()
}

@Serializable
class CloseByteChannel(private val uid: Int) : FileSystemAction<Unit>() {
    override fun run(context: EnclaveContext, isMail: Boolean) {
        val byteChannel = context.stateAs<State>().byteChannels.getValue(uid)
        byteChannel.close()
    }

    override fun resultSerializer(): KSerializer<Unit> = Unit.serializer()
}

@Serializable
class NewBufferedFileInputStream(private val path: String, private val uid: Int) : FileSystemAction<String>() {
    override fun run(context: EnclaveContext, isMail: Boolean): String {
        val bis = BufferedInputStream(FileInputStream(File(path)), 1000000)
        context.stateAs<State>().inputStreams[uid] = bis
        return bis.toString()
    }

    override fun resultSerializer(): KSerializer<String> = String.serializer()
}

@Serializable
class ReadAndWriteFiles(private val files: Int, private val parentDir: String) : FileSystemAction<List<String>>() {
    override fun run(context: EnclaveContext, isMail: Boolean): List<String> {
        repeat(files) { i ->
            Paths.get(parentDir, "test_file_$i.txt").writeText("Dummy text from file $i")
        }
        return (0 until files).map { i ->
            String(Paths.get(parentDir, "test_file_$i.txt").readBytes())
        }
    }

    override fun resultSerializer(): KSerializer<List<String>> = ListSerializer(String.serializer())
}

@Serializable
class WriteFilesConcurrently(private val files: Int, private val parentDir: String) : FileSystemAction<List<String>>() {
    override fun run(context: EnclaveContext, isMail: Boolean): List<String> {
        val futures = (0 until files).map { i ->
            threadWithFuture {
                Paths.get(parentDir, "test_file_$i.txt").writeText("Dummy text from file $i")
            }
        }

        return futures.mapIndexed { i, future ->
            future.join()
            String(Paths.get(parentDir, "test_file_$i.txt").readBytes())
        }
    }

    override fun resultSerializer(): KSerializer<List<String>> = ListSerializer(String.serializer())
}

@Serializable
class RandomAccessFileConcurrentWrites(private val threads: Int, private val parentDir: String) : FileSystemAction<String>() {
    override fun run(context: EnclaveContext, isMail: Boolean): String {
        val testFile = Paths.get(parentDir, "test_file.txt")
        RandomAccessFile(testFile.toFile(), "rws").use { raf ->
            (0 until threads).map { i ->
                threadWithFuture {
                    raf.write("Dummy text from file $i".toByteArray())
                }
            }.forEach { it.join() }
        }
        return String(testFile.readBytes())
    }

    override fun resultSerializer(): KSerializer<String> = String.serializer()
}

@Serializable
class ReadFiles(private val files: Int, private val parentDir: String) : FileSystemAction<List<String>>() {
    override fun run(context: EnclaveContext, isMail: Boolean): List<String> {
        return (0 until files).map { i ->
            String(Paths.get(parentDir, "test_file_$i.txt").readBytes())
        }
    }

    override fun resultSerializer(): KSerializer<List<String>> = ListSerializer(String.serializer())
}

@Serializable
class ReadAndWriteFilesToDefaultFileSystem(private val files: Int) : FileSystemAction<List<String>>() {
    override fun run(context: EnclaveContext, isMail: Boolean): List<String> {
        val fileSystem = FileSystems.getDefault();
        val path = fileSystem.getPath("/")
        repeat(files) { i ->
            path.resolve("test_file_$i.txt").writeText("Dummy text from file $i")
        }
        return (0 until files).map { i ->
            path.resolve("test_file_$i.txt").readText()
        }
    }

    override fun resultSerializer(): KSerializer<List<String>> = ListSerializer(String.serializer())
}
