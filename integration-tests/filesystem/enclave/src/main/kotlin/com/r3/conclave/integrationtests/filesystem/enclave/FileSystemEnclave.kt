package com.r3.conclave.integrationtests.filesystem.enclave

import com.google.protobuf.Int32Value
import com.google.protobuf.Int64Value
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.integrationtests.filesystem.common.proto.Request
import java.io.*
import java.net.URL
import java.nio.channels.ByteChannel
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.collections.forEachIndexed
import kotlin.collections.map
import kotlin.collections.plus
import kotlin.collections.set
import kotlin.io.path.readBytes
import kotlin.io.path.writeText


class FileSystemEnclave : Enclave() {

    companion object {
        const val NUM_THREADS = 9
    }

    private val inputStreams = ConcurrentHashMap<Int, InputStream>()
    private val outputStreams = ConcurrentHashMap<Int, OutputStream>()
    private val byteChannels = ConcurrentHashMap<Int, ByteChannel>()

    override fun receiveFromUntrustedHost(bytes: ByteArray): ByteArray? {
        val request = Request.parseFrom(bytes)
        return when (request.type) {
            Request.Type.PATHS_GET -> {
                Paths.get(request.path).toString().toByteArray()
            }

            Request.Type.FILES_WRITE -> {
                Files.write(Paths.get(request.path), request.data.toByteArray()).toString().toByteArray()
            }
            Request.Type.FILES_DELETE -> {
                Files.delete(Paths.get(request.path))
                byteArrayOf()
            }
            Request.Type.FILES_CREATE_DIRECTORY -> {
                Files.createDirectory(Paths.get(request.path)).toString().toByteArray()
            }
            Request.Type.FILES_CREATE_DIRECTORIES -> {
                Files.createDirectories(Paths.get(request.path)).toString().toByteArray()
            }
            Request.Type.FILES_SIZE -> {
                val size = Files.size(Paths.get(request.path))
                Int64Value.newBuilder().setValue(size).build().toByteArray()
            }
            Request.Type.FILES_READ_ALL_BYTES -> {
                Files.readAllBytes(Paths.get(request.path))
            }

            Request.Type.FILES_NEW_OUTPUT_STREAM_DELETE_ON_CLOSE -> {
                val outputStream = Files.newOutputStream(Paths.get(request.path),
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.DELETE_ON_CLOSE)
                outputStreams[request.uid] = outputStream
                outputStream.toString().toByteArray()
            }

            Request.Type.FILE_INPUT_STREAM_CONSTRUCTOR -> {
                val fis = FileInputStream(request.path)
                inputStreams[request.uid] = FileInputStream(request.path)
                fis.toString().toByteArray()
            }
            Request.Type.INPUT_STREAM_READ -> {
                val inputStream = inputStreams[request.uid]!!
                byteArrayOf(inputStream.read().toByte())
            }
            Request.Type.INPUT_STREAM_READ_BYTES -> {
                val inputStream = inputStreams[request.uid]!!
                inputStream.readBytes()
            }
            Request.Type.INPUT_STREAM_MARK_AVAILABLE -> {
                val fis = inputStreams[request.uid]!!
                byteArrayOf(fis.markSupported().toByte())
            }
            Request.Type.INPUT_STREAM_CLOSE -> {
                val inputStream = inputStreams.remove(request.uid)!!
                inputStream.close()
                byteArrayOf()
            }
            Request.Type.INPUT_STREAM_RESET -> {
                val inputStream = inputStreams[request.uid]!!
                inputStream.reset()
                byteArrayOf()
            }

            Request.Type.FILE_INPUT_STREAM_FD -> {
                val fis : FileInputStream = inputStreams[request.uid] as FileInputStream
                byteArrayOf(fis.fd.valid().toByte())
            }

            Request.Type.INPUT_STREAM_OPEN -> {
                val inputStream = URL("file://${request.path}").openStream()
                inputStreams[request.uid] = inputStream
                return inputStream.toString().toByteArray()
            }

            Request.Type.FILE_OUTPUT_STREAM_CONSTRUCTOR -> {
                val fos = FileOutputStream(request.path, request.append)
                outputStreams[request.uid] = fos
                fos.toString().toByteArray()
            }
            Request.Type.OUTPUT_STREAM_WRITE -> {
                val outputStream = outputStreams[request.uid]!!
                outputStream.write(request.data.toByteArray()[0].toInt())
                byteArrayOf()
            }
            Request.Type.OUTPUT_STREAM_WRITE_BYTES -> {
                val outputStream = outputStreams[request.uid]!!
                outputStream.write(request.data.toByteArray())
                byteArrayOf()
            }
            Request.Type.OUTPUT_STREAM_WRITE_OFFSET -> {
                val outputStream = outputStreams[request.uid]!!
                outputStream.write(request.data.toByteArray(), request.offset, request.length)
                byteArrayOf()
            }
            Request.Type.OUTPUT_STREAM_CLOSE -> {
                val outputStream = outputStreams.remove(request.uid)!!
                outputStream.close()
                byteArrayOf()
            }

            Request.Type.BYTE_CHANNEL_DELETE_ON_CLOSE -> {
                val byteChannel = Files.newByteChannel(Paths.get(request.path),
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.DELETE_ON_CLOSE)
                byteChannels[request.uid] = byteChannel
                byteChannel.toString().toByteArray()
            }
            Request.Type.BYTE_CHANNEL_CLOSE -> {
                val byteChannel = byteChannels.remove(request.uid)!!
                byteChannel.close()
                byteArrayOf()
            }

            Request.Type.BUFFERED_INPUT_STREAM_OPEN -> {
                val bis = BufferedInputStream(
                        FileInputStream(File(request.path)),
                        1000000
                )
                inputStreams[request.uid] = bis
                bis.toString().toByteArray()
            }
            Request.Type.BUFFERED_INPUT_STREAM_READ_BYTES -> {
                val inputStream = inputStreams[request.uid]!!
                val buf = ByteArray(request.length)
                val read = inputStream.read(buf)
                Int32Value.newBuilder()
                        .setValue(read)
                        .build()
                        .toByteArray()
            }

            Request.Type.FILES_NEW_INPUT_STREAM -> {
                val inputStream = Files.newInputStream(Paths.get(request.path), StandardOpenOption.READ)
                inputStreams[request.uid] = inputStream
                inputStream.javaClass.name.toByteArray()
            }

            Request.Type.MANY_FILES_READ_WRITE -> {
                //  These are not really Java threads, just reusing the terminology for consistency
                //    with the tests below
                val numFiles = NUM_THREADS
                repeat(numFiles) { i ->
                    Paths.get("test_file_$i.txt").writeText("Dummy text from thread $i\n")
                }

                var allByteArray = byteArrayOf(numFiles.toByte())

                repeat(numFiles) { i ->
                    allByteArray += Paths.get("test_file_$i.txt").readBytes()
                }
                allByteArray
            }

            Request.Type.MULTI_THREAD_MANY_FILES_READ_WRITE -> {
                val executor = Executors.newFixedThreadPool(NUM_THREADS)
                val futures = (0 until NUM_THREADS).map { i ->
                    executor.submit {
                        Paths.get("test_file_$i.txt").writeText("Dummy text from thread $i\n")
                    }
                }
                var allThreadFilesContent = byteArrayOf(NUM_THREADS.toByte())

                futures.forEachIndexed{ i, it_future ->
                    it_future.get()
                    allThreadFilesContent += Paths.get("test_file_$i.txt").readBytes()
                }
                executor.shutdown()
                if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                    executor.shutdownNow()
                }

                allThreadFilesContent
            }

            Request.Type.MULTI_THREAD_SINGLE_FILE_READ_WRITE -> {
                val executor = Executors.newFixedThreadPool(NUM_THREADS)
                val raf = RandomAccessFile("test_file.txt", "rws")
                val futures = (0 until NUM_THREADS).map { i ->
                    executor.submit {
                        val text = "Dummy text from thread $i\n"
                        raf.write(text.toByteArray())
                    }
                }
                futures.forEach{ it_future ->
                    it_future.get()
                }
                executor.shutdown()
                if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                    executor.shutdownNow()
                }

                var allThreadFilesContent = byteArrayOf(NUM_THREADS.toByte())
                allThreadFilesContent += Paths.get("test_file.txt").readBytes()
                allThreadFilesContent
            } else -> {
                throw IllegalArgumentException("Unknown request type: ${request.type}")
            }
        }
    }
}

fun Boolean.toByte(): Byte {
    return if (this) 1 else 0
}