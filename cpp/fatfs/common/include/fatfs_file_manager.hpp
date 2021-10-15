#ifndef _FATFS_FILE_MANAGER
#define _FATFS_FILE_MANAGER

#include <stdio.h>
#include <string.h>
#include <assert.h>
#include <unistd.h>

#include <memory>
#include <mutex>
#include <string>
#include <unordered_map>
#include <limits>
#include <stdexcept>
#include <fcntl.h>

#include "enclave_shared_data.h"
#include "substrate_jvm.h"
#include "sys_stat.h"
#include "graal_isolate.h"
#include "vm_enclave_layer.h"
#include "conclave-stat.h"

#include "ff.hpp"
#include "common.hpp"

enum StorageType {
		  ENCLAVE_MEMORY,
};
namespace conclave {

    typedef uint32_t mode_t;
    typedef int FileHandle;

    class FatFsFileManager {

    private:
	std::unordered_map<FileHandle, FIL* > files_;
	std::unordered_map<std::string, FileHandle> file_paths_;
	std::unordered_map<FIL*, FileHandle> inverse_files_map_;
	std::unordered_map<FileHandle, std::string> inverse_file_paths_;    
	std::mutex file_mutex_;
    
	//  File descriptors 1 and 2 are reserved.
	FileHandle next_available_handle_ = 1000;
	BYTE drive_id_ = 0;
	unsigned char* data_ = NULL;

	FatFsFileManager(const StorageType type, const unsigned int disk_size);
    
	FatFsFileManager() = delete;

	virtual ~FatFsFileManager();

	void initRamDisk(const unsigned int disk_size);

	FileHandle getNewHandle();
	
	int closeInternal(FIL* fil_ptr);

	int unlinkInternal(const char* file_path, int& res);

	int statInternal(const char* path, struct stat* stat_buf, int& err);

	int statInternal64(const char* path, struct stat64* stat_buf, int& err);

	FileHandle createFileHandle(FIL* fil_ptr, const std::string& path); 
	
    public:
   
	static FatFsFileManager& instance(const StorageType type, const unsigned int size);

	off_t lseek(int fd, off_t offset, int whence);
  
	int open(const char* file_path, int oflag);
       
	ssize_t read(int fd, void* buf, size_t count);

	size_t fread(void* ptr, size_t size, size_t nmemb, FILE* stream);

	ssize_t pread(int fd, void* buf, size_t count, off_t offset);
    
	FILE *fdopen(int fd, const char *mode);

	FILE *fopen(const char* path, const char* mode);

	size_t fwrite(const void* buf, size_t size, size_t count, FILE* fp);

	ssize_t pwrite(int fd, const void* buf, size_t count, off_t offset);
	
	ssize_t write(int fd, const void* buf, size_t count);
	
	int fclose(FILE* fp);    
        
	int close(int fd);

	int fstat(int ver, int fd, struct stat64* stat_buf, unsigned int num_bytes, int& err);

	int stat(int ver, const char* path, struct stat64* stat_buf, unsigned int num_bytes, int&err);

	int lstat64(const char* path, struct stat64* stat_buf, int& err);

	int lstat(const char* path, struct stat* stat_buf, int& err);
	
	int mkdir(const char* path, mode_t mode);

	int access(const char* path, mode_t mode, int& err);
	
	int unlink(const char* path, int& res);

	int remove(const char* path_in, int& res);

	int rmdir(const char* path, int& res);

	int chdir(const char* path);

	char *getcwd(char* buf, size_t size);

	int socketpair(int domain, int type, int protocol, int sv[2]);

	int dup2(int oldfd, int newfd);
    };

};

#endif    //  _FATFS_FILE_MANAGER
