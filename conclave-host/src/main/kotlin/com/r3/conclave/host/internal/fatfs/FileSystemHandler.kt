package com.r3.conclave.host.internal.fatfs

import java.io.RandomAccessFile
import java.nio.file.Path
import kotlin.io.path.pathString

/*
This is a class to manage files that represent FatFs persistent-encrypted
  filesystems in the Host.

The paths of these files are provided as argument when calling EnclaveHost.load function.
At the moment, we only support (see comment at the end) 1 file->1 persistent-encrypted filesystem.
The read/write functions are called by C++ Host JNI code (cpp/fatfs/host/persistent_disk.cpp) to read and write
 encrypted and shuffled sectors (i.e. fixed-size chunks) of bytes into the files representing the filesystems.
The getDriveSize call is also called by the C++ Host JNI code during the initialization of the filesystem in the Enclave.
 Depending on the result of getDriveSize the Enclave will proceed as follows:
  getDriveSize returns -1 -> the file is not available, the Enclave will crash as something wrong has happened
  getDriveSize returns 0 -> the file is available, and it has just been created by the Host; the Enclave will format it
  getDriveSize returns a value bigger than 0 -> the file is available and it has been previously created,
  the Enclave will just use it.

 Note that while the creation of these files is directly handled by the Host during the startup of the Enclave,
   the getDriveSize/read/write calls are triggered only by OCalls from the Enclave.
*/
class FileSystemHandler(enclaveFileSystemFilePaths: List<Path>) {
    private val files: MutableList<RandomAccessFile>

    //  setup() and cleanup() are native calls implemented in JNI C++ layer (cpp/fatfs/host/persistent_disk.cpp)
    //  to register and clean up the global reference of JavaVM in C++ .
    private external fun setup()
    private external fun cleanup()

    init {
        setup()
        files = mutableListOf()

        enclaveFileSystemFilePaths.forEach { path ->
            files.add(RandomAccessFile(path.pathString, "rws"))
        }
    }


    fun close() {
        files.forEach { it.close() }
        cleanup()
    }


    @Suppress("unused")
    @Synchronized
    fun getDriveSize(drive: Int): Long {
        return files.getOrNull(drive)?.length() ?: -1
    }


    @Suppress("unused")
    @Synchronized
    fun read(drive: Int, sectorId: Int, numSectors: Int, sectorSize: Int): ByteArray {
        val randomAccessFile = files[drive]

        val position = sectorId * sectorSize
        val readSize = numSectors * sectorSize
        val buffer = ByteArray(readSize)
        randomAccessFile.seek(position.toLong())
        randomAccessFile.readFully(buffer)
        return buffer
    }


    @Suppress("unused")
    @Synchronized
    fun write(drive: Int, inputBuffer: ByteArray, sectorSize: Int, indices: IntArray): Int {
        val randomAccessFile = files[drive]

        var bufferIt = 0
        val numWrites = indices.size

        for (i in 0 until numWrites) {
            val start = indices[i]
            val position = start * sectorSize
            randomAccessFile.seek(position.toLong())
            randomAccessFile.write(inputBuffer, bufferIt, sectorSize)
            bufferIt += sectorSize
        }
        return sectorSize * numWrites
    }
}
