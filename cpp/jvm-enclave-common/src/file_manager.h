//
// Manages the emulation of files as required by substrate vm
//
#pragma once

#include "vm_enclave_layer.h"
#include <map>
#include <string>

namespace conclave {

typedef int FileHandle;

// Standard files
extern "C" {
extern FILE* stdout;
extern FILE* stderr;
}

class File {
private:
    int _handle;
    std::string _filename;

protected:
    File(FileHandle h, std::string filename);

public:

    virtual ~File();
    
    //
    // Get a handle for the file. Can be returned as a handle from
    // POSIX functions
    //
    int handle() const;

    //
    // Get the filename that was used to open this file
    //
    std::string filename();


    //
    // Read 'size' * 'count' bytes from the file into buf.
    // Returns the count that was read
    //
    virtual int read(int size, int count, void* buf) const = 0;

    //
    // Write 'size' * 'count' bytes from the buf into the file.
    // Returns the count that was written
    //
    virtual int write(int size, int count, const void* buf) const  = 0;
};

class FileManager {
private:
    std::map<FileHandle, File*> _files;
    long                        _next_handle;
    static FileManager*         _instance;

private:
    FileManager();

public:
    static FileManager& instance();

    //
    // Open a file.
    //
    File* open(std::string filename);
    
    //
    // Close a file
    //
    void close(const File* file);
    void close(int handle);

    //
    // Get a file from its handle
    //
    File* fromHandle(int handle);

    //
    // Get a file from a FILE*
    //
    File* fromFILE(FILE* fp);

    //
    // Check if a file exists
    //
    bool exists(std::string filename);

private:
    int allocateHandle();
};

}