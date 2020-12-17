#include "file_manager.h"

namespace conclave {

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

    virtual size_t read(unsigned char* buf, size_t size, size_t offset = 0) const override {
        if (sgx_read_rand(buf, size) != SGX_SUCCESS) {
            return 0;
        }
        return size;
    }

    virtual size_t write(const unsigned char* buf, size_t size, size_t offset = 0) override {
        return 0;
    }
};

///////////////////////////////////////////////////////////////////
// Standard output and error
class StdFile : public File {
public:
    StdFile(FileHandle h, std::string filename) : File(h, filename) {
    }

    virtual size_t read(unsigned char* buf, size_t size, size_t offset = 0) const override {
        return 0;
    }

    virtual size_t write(const unsigned char* buf, size_t size, size_t offset = 0) override {
        debug_print_enclave((const char*)buf, size);
        return size;
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

File::~File() {
}

int File::handle() const {
    return _handle;
}

const std::string& File::filename() const {
    return _filename;
}

///////////////////////////////////////////////////////////////////
// File Manager
FileManager::FileManager() : next_handle_(0x10) {
    // Create the standard files. These should never be closed.
    files_[1] = new StdFile(1, FILENAME_STDOUT); 
    files_[2] = new StdFile(2, FILENAME_STDERR);
}

FileManager& FileManager::instance() {
    static FileManager instance;
    return instance;
}

File* FileManager::open(const std::string& filename) {
    File* file = nullptr;
    if ((filename == FILENAME_RANDOM) ||
        (filename == FILENAME_URANDOM)) {
        std::lock_guard<std::mutex> lock(file_mutex_);
        FileHandle handle = allocateHandle();
        file = new RandomFile(handle, filename);
        files_[handle] = file;
    }
    return file;
}

int FileManager::close(const File* file) {
    if (file) {
        return close(file->handle());
    }
    return -1;
}

int FileManager::close(int handle) {
    std::lock_guard<std::mutex> lock(file_mutex_);
    auto file = files_.find(handle);
    if (file != files_.end()) {
        delete file->second;
        files_.erase(file);
        return 0;
    }
    return -1;
}

File* FileManager::fromHandle(int handle) {
    std::lock_guard<std::mutex> lock(file_mutex_);
    auto file = files_.find(handle);
    if (file != files_.end()) {
        return file->second;
    }
    return nullptr;
}

File* FileManager::fromFILE(FILE* fp) {
    // stdout and stderr are special cases. They will point to 'file_std???' defined above
    // which are actually null pointers
    if (fp == stdout) {
        return files_[1];
    }
    else if (fp == stderr) {
        return files_[2];
    }

    // Normal files
    std::lock_guard<std::mutex> lock(file_mutex_);
    auto file = std::find_if(files_.begin(), files_.end(), [fp](std::pair<FileHandle, File*> file_entry) {
        return (void*)file_entry.second == (void*)fp;
    });
    return (file == files_.end()) ? nullptr : file->second;
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
    while (files_.find(next_handle_) != files_.end()) {
        ++next_handle_;
    }
    return next_handle_++;
}

};