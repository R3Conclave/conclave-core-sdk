//
// Manages the emulation of files as required by substrate vm
//
#pragma once

#include "vm_enclave_layer.h"
#include <map>
#include <string>
#include <mutex>

namespace conclave {

typedef int FileHandle;

// Standard files
extern "C" {
extern FILE* stdout;
extern FILE* stderr;
}

/**
 * Base class for emulating different types of files to support dummy implementation 
 * of C-Runtime file functions.
 */
class File {
private:
    int _handle;
    std::string _filename;

protected:
    File(FileHandle h, std::string filename);

public:

    virtual ~File();
    
    /**
     * Get a handle for the file.
     *
     * @return A handle that can be directly returned from POSIX emulated functions.
     */
    int handle() const;

    /**
     * Get the filename that was used to open this file
     */
    std::string filename();


    /**
     * Read data from an emulated file into a buffer
     *
     * @param buf The buffer to populate.
     * @param size The size in bytes to read.
     * @param offset The offset in bytes from the start of the file to read. The read operation
     *               always populates from the first byte of the buffer regardless of the offset.
     * @return The number of bytes that were read into the buffer.
     */
    virtual size_t read(unsigned char* buf, size_t size, size_t offset = 0) const = 0;

    /**
     * Write data to an emulated file from a buffer
     *
     * @param buf The buffer to use as the source.
     * @param size The size in bytes to write.
     * @param offset The offset in bytes from the start of the file to write. The write operation
     *               always reads from the first byte of the buffer regardless of the offset.
     * @return The number of bytes that were written to the file.
     */
    virtual size_t write(const unsigned char* buf, size_t size, size_t offset = 0) = 0;
};

class FileManager {
private:
    std::map<FileHandle, File*> _files;
    long                        _next_handle;
    std::mutex                  _file_mutex;

private:
    FileManager();
    ~FileManager() = default;
    FileManager(const FileManager &) = delete;
    FileManager &operator=(const FileManager &) = delete;

public:
    static FileManager& instance();

    /**
     * Open a file given a filename.
     *
     * @param filename The filename of the file to open
     * @return File object or NULL if the file could not be opened. The returned
     *         file object should be by calling the one of the close() methods in
     *         this class
     */
    File* open(std::string filename);
    
    /**
     * Close a previously opened file.
     *
     * @param file The file to close.
     */
    void close(const File* file);

    /**
     * Close a previously opened file.
     *
     * @param handle The handle of the file to close.
     */
    void close(int handle);

    /**
     * Get a file from its handle.
     *
     * @param handle The handle of the file to get the file object for.
     * @return The file object or NULL if the file cannot be found.
     */
    File* fromHandle(int handle);

    /**
     * Get a file from a C Runtime FILE*. The file pointer is cast to an integer
     * handle which is used to reference the file.
     *
     * @param fp The file pointer of the file to get the file object for.
     * @return The file object or NULL if the file cannot be found.
     */
    File* fromFILE(FILE* fp);

    /**
    * Check if a file exists.
    *
    * @param filename The filename of the file to check existence of.
    * @return true if the file exists, false if the file does not exist.
    */
    bool exists(std::string filename);

private:
    int allocateHandle();
};

}