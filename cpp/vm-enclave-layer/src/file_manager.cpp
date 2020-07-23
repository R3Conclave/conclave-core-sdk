#include "file_manager.h"
#include <mutex>

namespace conclave {

FileManager* FileManager::_instance;
static std::mutex file_mutex;


// Filenames for our special case files
#define FILENAME_RANDOM     "/dev/random"
#define FILENAME_URANDOM    "/dev/urandom"
#define FILENAME_STDOUT     "stdout"
#define FILENAME_STDERR     "stderr"

///////////////////////////////////////////////////////////////////
// /dev/random and /dev/urandom
class RandomFile : public File {
public:
    RandomFile(FileHandle h, std::string filename) : File(h, filename) {
    }

    virtual int read(int size, int count, void* buf) const {
        if (sgx_read_rand((unsigned char*)buf, size * count) != SGX_SUCCESS) {
            return 0;
        }
        return count;
    }

    virtual int write(int size, int count, const void* buf) const {
        return 0;
    }
};

///////////////////////////////////////////////////////////////////
// Standard output and error
class StdFile : public File {
public:
    StdFile(FileHandle h, std::string filename) : File(h, filename) {
    }

    virtual int read(int size, int count, void* buf) const {
        return 0;
    }

    virtual int write(int size, int count, const void* buf) const {
        debug_print_enclave((const char*)buf, size * count);
        return count;
    }
};

StdFile* file_stdout = nullptr;
StdFile* file_stderr = nullptr;
extern "C" {
FILE* stdout = (FILE*)&file_stdout;
FILE* stderr = (FILE*)&file_stderr;
}


///////////////////////////////////////////////////////////////////
// Abstract File
File::File(FileHandle h, std::string filename) : _handle(h), _filename(filename) {
}

int File::handle() const {
    return _handle;
}

std::string File::filename() {
    return _filename;
}

///////////////////////////////////////////////////////////////////
// File Manager
FileManager::FileManager() : _next_handle(0x10) {
    // Create the standard files. These should never be closed.
    _files[1] = new StdFile(1, FILENAME_STDOUT); 
    _files[2] = new StdFile(2, FILENAME_STDERR);
}

FileManager& FileManager::instance() {
    std::lock_guard<std::mutex> lock(file_mutex);
    if (!_instance) {
        _instance = new FileManager;
    }
    return *_instance;
}

File* FileManager::open(std::string filename) {
    File* file = nullptr;
    if ((filename == FILENAME_RANDOM) ||
        (filename == FILENAME_URANDOM)) {
        std::lock_guard<std::mutex> lock(file_mutex);
        FileHandle handle = allocateHandle();
        file = new RandomFile(handle, filename);
        _files[handle] = file;
    }
    return file;
}

void FileManager::close(const File* file) {
    if (file) {
        close(file->handle());
    }
}

void FileManager::close(int handle) {
    std::lock_guard<std::mutex> lock(file_mutex);
    auto file = _files.find(handle);
    if (file != _files.end()) {
        delete file->second;
        _files.erase(file);
    }
}

File* FileManager::fromHandle(int handle) {
    std::lock_guard<std::mutex> lock(file_mutex);
    auto file = _files.find(handle);
    if (file != _files.end()) {
        return file->second;
    }
    return nullptr;
}

File* FileManager::fromFILE(FILE* fp) {
    // stdout and stderr are special cases. They will point to 'file_std???' defined above
    // which are actually null pointers
    if (fp == stdout) {
        return _files[1];
    }
    else if (fp == stderr) {
        return _files[2];
    }

    // Normal files
    std::lock_guard<std::mutex> lock(file_mutex);
    auto file = std::find_if(_files.begin(), _files.end(), [fp](std::pair<FileHandle, File*> file_entry) {
        return (void*)file_entry.second == (void*)fp;
    });
    return (file == _files.end()) ? nullptr : file->second;
}

bool FileManager::exists(std::string filename) {
    if ((filename == FILENAME_RANDOM) ||
        (filename == FILENAME_URANDOM)) {
        return true;
    }
    return false;
}

int FileManager::allocateHandle() {
    // This variable is unlikely to ever wrap but check just in case.
    while (_files.find(_next_handle) != _files.end()) {
        ++_next_handle;
    }
    return _next_handle++;
}

};