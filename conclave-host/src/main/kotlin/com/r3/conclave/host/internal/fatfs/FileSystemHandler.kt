package com.r3.conclave.host.internal.fatfs

import com.r3.conclave.common.EnclaveMode
import java.io.RandomAccessFile
import java.nio.file.Path
import kotlin.io.path.pathString

/*
This is a class to manage files that represent FatFs persistent-encrypted
  filesystems in the Host.

The paths of these files are provided as argument when calling EnclaveHost.start function.
At the moment, we only have 1 file->1 persistent-encrypted filesystem.
The read/write functions are called by C++ Host JNI code (cpp/fatfs/host/persistent_disk.cpp) to read and write
 encrypted and shuffled sectors (i.e. fixed-size chunks) of bytes into the files representing the filesystems.
The getDriveSize function is also called by the C++ Host JNI code during the initialization of the filesystem in the Enclave,
this would indicate that a persistentFileSystemSize bigger than 0 has been provided in the Enclave configuration.

Note that while the creation of these files is directly handled by the Host during the startup of the Enclave,
   the getDriveSize/read/write calls are triggered only by OCalls from the Enclave.
As the Enclave configuration cannot be easily linked to the Host, we are using getDriveSize call to validate that the
Host-Enclave configurations are correct, see all the branch conditions in that function below.
*/

class FileSystemHandler(enclaveFileSystemFilePaths: List<Path>, private val enclaveMode: EnclaveMode) {

    private class FileSystemFile(val file: RandomAccessFile) {
        //  This is needed when using read/write functions
        //  as the header bytes are not included in the offset provided as input of those functions
        var headerSize: Int = 0
    }

    companion object {
        //                                Header size      Header version    EnclaveMode       FileSystem size
        const val VERSION_1_HEADER_SIZE = Int.SIZE_BYTES + Byte.SIZE_BYTES + Byte.SIZE_BYTES + Long.SIZE_BYTES
    }

    private val filesystemFiles: MutableList<FileSystemFile>

    //  setup() and cleanup() are native calls implemented in JNI C++ layer (cpp/fatfs/host/persistent_disk.cpp)
    //  to register and clean up the global reference of JavaVM in C++ .
    private external fun setup()
    private external fun cleanup()

    init {
        setup()
        filesystemFiles = mutableListOf()

        enclaveFileSystemFilePaths.forEach { path ->
            filesystemFiles.add(FileSystemFile(RandomAccessFile(path.pathString, "rws")))
        }
    }


    fun close() {
        filesystemFiles.forEach { it.file.close() }
        cleanup()
    }


    @Suppress("unused")
    @Synchronized
    fun getDriveSize(drive: Int, enclaveFileSystemSizeFromConfig: Long): Long {
        //  Calling this function can only happen when persistentFileSystemSize in the Enclave
        //  configuration is bigger than 0, i.e. when the Enclave wants a persisted filesystem
        val filesystemFile: FileSystemFile? = filesystemFiles.getOrNull(drive)

        checkNotNull(filesystemFile) {
            //  The Host has not provided a path in the EnclaveHost.start call and no file could be generated
            //    in the init of this class.
            "The enclave has been configured to use the persistent filesystem " +
                    "but no storage file was provided in EnclaveHost.start(...)."
        }

        if (filesystemFile.file.length() == 0L) {
            //  The Host has correctly provided a path and hence an empty file was generated in the init.
            //    We store EnclaveMode and enclaveFileSystemSize in the header and return 0, to tell the Enclave to
            //    format the filesystem file.
            filesystemFile.headerSize = VERSION_1_HEADER_SIZE
            filesystemFile.file.writeInt(VERSION_1_HEADER_SIZE)
            filesystemFile.file.write(1)
            filesystemFile.file.write(enclaveMode.ordinal)
            filesystemFile.file.writeLong(enclaveFileSystemSizeFromConfig)
            return 0
        } else {
            //  The Host has correctly provided a path and such file already existed, so it was only opened in the init.
            //  We read EnclaveMode and enclaveFileSystemSize from the header to check that they are
            //  consistent, we throw otherwise.
            //  We return the fileSystemSize to tell the Enclave that it can mount the filesystem without formatting it.
            val headerSize = filesystemFile.file.readInt()
            val version = filesystemFile.file.read()

            check(version == 1) {
                "The filesystem file is set with a non valid version"
            }
            val fileEnclaveModeByte = filesystemFile.file.read()
            val fileSystemSizeFromHeader = filesystemFile.file.readLong()

            filesystemFile.headerSize = headerSize
            val fileEnclaveMode = EnclaveMode.values()[fileEnclaveModeByte]
            check(fileEnclaveMode == enclaveMode) {
                "The persisted filesystem file has been created with the enclave mode " +
                        "$fileEnclaveMode which is different from the current enclave mode $enclaveMode"
            }

            check(fileSystemSizeFromHeader == enclaveFileSystemSizeFromConfig) {
                "The enclave's persistent filesystem size configuration has changed." +
                        " Once set this cannot change. Please revert the value back to $fileSystemSizeFromHeader"
            }
            return fileSystemSizeFromHeader
        }
    }


    @Suppress("unused")
    @Synchronized
    fun read(drive: Int, sectorId: Long, numSectors: Int, sectorSize: Int): ByteArray {
        val fileSystemFile = filesystemFiles[drive]

        val position = sectorId * sectorSize
        val readSize = numSectors * sectorSize
        val buffer = ByteArray(readSize)

        with(fileSystemFile.file) {
            seek(position + fileSystemFile.headerSize)
            readFully(buffer)
        }
        return buffer
    }


    @Suppress("unused")
    @Synchronized
    fun write(drive: Int, inputBuffer: ByteArray, sectorSize: Int, sector: Long): Int {
        val fileSystemFile = filesystemFiles[drive]

        with(fileSystemFile.file) {
            val position = sector * sectorSize
            seek(position + fileSystemFile.headerSize)
            write(inputBuffer, 0, sectorSize)
        }
        return sectorSize
    }
}
