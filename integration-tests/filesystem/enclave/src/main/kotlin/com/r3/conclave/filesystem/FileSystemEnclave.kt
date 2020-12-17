package com.r3.conclave.filesystem

import com.google.protobuf.Int32Value
import com.google.protobuf.Int64Value
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.filesystem.proto.Request
import com.r3.conclave.filesystem.proto.StringList
import sun.nio.fs.DefaultFileSystemProvider
import java.io.*
import java.lang.IllegalArgumentException
import java.net.URL
import java.nio.channels.ByteChannel
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.nio.file.spi.FileSystemProvider
import java.util.concurrent.ConcurrentHashMap

class FileSystemEnclave : Enclave() {

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

            Request.Type.JIMFS_INPUT_STREAM_OPEN -> {
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

            Request.Type.FILE_SYSTEMS_GET_DEFAULT -> {
                FileSystems.getDefault().toString().toByteArray()
            }
            Request.Type.FILE_SYSTEMS_GET_DEFAULT_PROVIDER -> {
                FileSystems.getDefault().provider().toString().toByteArray()
            }

            Request.Type.FILE_SYSTEM_PROVIDER_INSTALLED_PROVIDERS -> {
                val builder = StringList.newBuilder()
                FileSystemProvider.installedProviders().forEach {
                    builder.addValues(it.toString())
                }
                builder.build().toByteArray()
            }

            Request.Type.DEFAULT_FILE_SYSTEM_PROVIDER_CREATE -> {
                DefaultFileSystemProvider.create().toString().toByteArray()
            }

            else -> {
                throw IllegalArgumentException("Unknown request type: ${request.type}")
            }
        }
    }
}

fun Boolean.toByte(): Byte {
    return if (this) 1 else 0
}