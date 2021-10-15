#include <algorithm>
#include <string.h>

#include "fatfs_file_manager.hpp"
#include "fatfs_ram.hpp"

#include "common.hpp"

namespace conclave {

    static std::unordered_map<int, BYTE> createFlagMap() {
	/*
	  http://elm-chan.org/fsw/ff/doc/open.html
	  POSIX	FatFs
	  "r"	FA_READ
	  "r+"	FA_READ | FA_WRITE
	  "w"	FA_CREATE_ALWAYS | FA_WRITE
	  "w+"	FA_CREATE_ALWAYS | FA_WRITE | FA_READ
	  "a"	FA_OPEN_APPEND | FA_WRITE
	  "a+"	FA_OPEN_APPEND | FA_WRITE | FA_READ
	  "wx"	FA_CREATE_NEW | FA_WRITE
	  "w+x"	FA_CREATE_NEW | FA_WRITE | FA_READ
	*/

	std::unordered_map<int, BYTE> flags;
	//  "r"
	flags[O_RDONLY]                      = FA_READ;
	//  "r+"
	flags[O_RDWR]                        = FA_READ | FA_WRITE;
	//  "w"
	flags[O_WRONLY | O_CREAT | O_TRUNC]  = FA_CREATE_ALWAYS | FA_WRITE;
	//  "w+"
	flags[O_RDWR | O_CREAT | O_TRUNC]    = FA_CREATE_ALWAYS | FA_WRITE | FA_READ;
	//  "a"
	flags[O_WRONLY | O_CREAT | O_APPEND] = FA_OPEN_APPEND | FA_WRITE;
	//  "a+"
	flags[O_RDWR | O_CREAT | O_APPEND]   = FA_OPEN_APPEND | FA_WRITE | FA_READ;
	//  "wx"
	flags[O_WRONLY | O_CREAT | O_EXCL]   = FA_CREATE_NEW | FA_WRITE;
	//  "w+x"
	flags[O_RDWR | O_CREAT | O_EXCL]     = FA_CREATE_NEW | FA_WRITE | FA_READ;

	//  "w"   Default to open always when neither O_TRUNC nor O_APPEND are set
	flags[O_WRONLY | O_CREAT ] = FA_OPEN_ALWAYS | FA_WRITE;
	//  "w+"  Default to open always when neither O_TRUNC nor O_APPEND are set
	flags[O_RDWR | O_CREAT ]   = FA_OPEN_ALWAYS | FA_WRITE | FA_READ;
	return flags;	    
    }

    
    static BYTE convertFlag(const int posix_flag) {
	//  We only allow a specific set of Posix flag
	const int mask = O_RDONLY |O_RDWR | O_WRONLY | O_CREAT | O_TRUNC | O_APPEND | O_EXCL;	
	const static auto flags = createFlagMap();
	const int masked_fatfs_flag = mask & posix_flag;
	const BYTE fatfs_flag = flags.at(masked_fatfs_flag);
	FATFS_DEBUG_PRINT("Converted from original Posix flag: %d, %04X\n" \
			  "  to masked Posix flag: %d, %04X and then fatfs flag %d\n",
			  posix_flag, posix_flag,
			  masked_fatfs_flag, masked_fatfs_flag, (unsigned int)fatfs_flag);
	return fatfs_flag;
    }


    static std::unordered_map<std::string, BYTE> createPosixModeFlagMap() {
	std::unordered_map<std::string, BYTE> flags;
	flags["r"]   = FA_READ;
	flags["r+"]  = FA_READ | FA_WRITE;
	flags["w"]   = FA_CREATE_ALWAYS | FA_WRITE;
	flags["w+"]  = FA_CREATE_ALWAYS | FA_WRITE | FA_READ;
	flags["a"]   = FA_OPEN_APPEND | FA_WRITE;
	flags["a+"]  = FA_OPEN_APPEND | FA_WRITE | FA_READ;
	flags["wx"]  = FA_CREATE_NEW | FA_WRITE;
	flags["w+x"] = FA_CREATE_NEW | FA_WRITE | FA_READ;
	return flags;
    }    


    static BYTE parsePosixModeFlag(const std::string mode) {
	DEBUG_PRINT_FUNCTION;
	//  Ignore legacy letter "b", which is ignored on all POSIX systems
	std::string mode_tmp(mode);
	mode_tmp.erase(std::remove(mode_tmp.begin(), mode_tmp.end(), 'b'), mode_tmp.end());
	mode_tmp.erase(std::remove(mode_tmp.begin(), mode_tmp.end(), 'e'), mode_tmp.end());
	mode_tmp.erase(std::remove(mode_tmp.begin(), mode_tmp.end(), 'm'), mode_tmp.end());
	mode_tmp.erase(std::remove(mode_tmp.begin(), mode_tmp.end(), 'c'), mode_tmp.end());
	const static auto flags = createPosixModeFlagMap();	
	return flags.at(mode_tmp);
    };


    FatFsFileManager& FatFsFileManager::instance(const StorageType type,
						 const unsigned int ramsize) {
	static FatFsFileManager instance(type, ramsize);
	return instance;
    };


    FatFsFileManager::~FatFsFileManager() {
	ramdisk_stop(drive_id_);
	for (auto& kv : files_) {
	    FIL* fil_ptr = kv.second;

	    if (fil_ptr != NULL) {
		delete(kv.second);
	    }
	}

	if (data_ != NULL) {
	    free(data_);
	    data_ = NULL;
	};
    };


    void FatFsFileManager::initRamDisk(const unsigned int disk_size) {
	data_ = (unsigned char*)calloc(disk_size, sizeof(unsigned char));

	if (data_ == NULL) {
	    const std::string message("Could not allocate memory for the RAM disk in the Enclave");
	    throw std::runtime_error(message);
	}
	const DRESULT result = ramdisk_start(drive_id_, data_, disk_size, 1);   

	if (result != RES_OK) {
	    const std::string message("Error in creating the RAM disk in the Enclave");
	    throw std::runtime_error(message);
	}
	FATFS_DEBUG_PRINT("Created RAM disk of size %u bytes\n", disk_size);
    };

    
    FatFsFileManager::FatFsFileManager(const StorageType type,
				       const unsigned int disk_size) {
	if (type == ENCLAVE_MEMORY) {
	    initRamDisk(disk_size);
	} else {
	    const std::string message("Developer error: no other modes available at the moment");
	    throw std::runtime_error(message);
	}
    };


    off_t FatFsFileManager::lseek(int fd, off_t offset, int whence) {
	FATFS_DEBUG_PRINT("lseek fd %d, offset %ld, command %d\n", fd, offset, whence);
	
	if (whence == SEEK_CUR && offset == 0) {
	    //  This is a no-op case, so we return successfully
	    return 0;
	} else {
	    FATFS_DEBUG_PRINT("Error in seeking from handle %d\n", fd);
	    return -1;
	}

	/* 
           The code here should work, but when working with files this does not seem
	   to be executed, so better to return an error at the moment, as this hasn't been
	   really tested.
	   std::lock_guard<std::mutex> lock(file_mutex_);
	   const FileHandle handle = fd; 
	   const auto it = files_.find(handle);

	   if (it == files_.end()) {
	   FATFS_DEBUG_PRINT("Error: handle not found: %d\n", handle);
	   return -1;
	   }

	   FIL* fil_ptr = it->second;    
	   const FRESULT res = f_lseek(fil_ptr, offset);

	   if (res == FR_OK) {
	   return 0;
	   } else {
	   FATFS_DEBUG_PRINT("Error in seeking from handle %d, result %d\n", handle, res);
	   return -1;
	   }
	*/
    };

  
    int FatFsFileManager::open(const char* path_name, int oflag) {
	FATFS_DEBUG_PRINT("Opening file: %s\n", path_name);

	if (path_name == NULL) {
	    return -1;
	}

	const BYTE fatfs_mode_flag = convertFlag(oflag);
	std::lock_guard<std::mutex> lock(file_mutex_);

	const std::string path_name_str = path_name;
	const auto it = file_paths_.find(path_name_str);

	if (it != file_paths_.end()) {
	    //  When opening again the same file, we do a f_sync (flush), so that we can read it correctly.	    
	    const FileHandle old_handle = it->second;
	    FIL* old_fil = files_.at(old_handle);
	    const FRESULT res_sync = f_sync(old_fil);
	    FATFS_DEBUG_PRINT("File %s, handle %d previously opened, synced with result %d\n", path_name, old_handle, res_sync);
	    
	    if (res_sync != 0) {
		return -1;
	    }	
	}
	FIL* fil_ptr = new FIL();
	const FRESULT res = f_open(fil_ptr, path_name, fatfs_mode_flag);
	    
	if (res != FR_OK) {
	    FATFS_DEBUG_PRINT("File not opened, with failure: %d\n", res);
	    delete(fil_ptr);
	    fil_ptr = NULL;
	    return -1;
	};

	const FileHandle file_handle = createFileHandle(fil_ptr, path_name_str);
	return file_handle;
    };
  
    ssize_t FatFsFileManager::read(int fd, void* buf, size_t count) {
	DEBUG_PRINT_FUNCTION;

	if (buf == NULL || count == 0) {
	    return 0;
	}
	std::lock_guard<std::mutex> lock(file_mutex_);
	const FileHandle handle = fd; 
	const auto it = files_.find(handle);

	if (it == files_.end()) {
	    FATFS_DEBUG_PRINT("Error: handle not found: %d\n", handle);
	    return -1;
	}

	FATFS_DEBUG_PRINT("Reading from handle: %d, num bytes: %lu\n", handle, count);
	
	FIL* fil_ptr = it->second;    
	UINT read_bytes = 0;
	const FRESULT res = f_read(fil_ptr, buf, count, &read_bytes);

	if (res == FR_OK) {
	    return static_cast<ssize_t>(read_bytes);
	} else {
	    FATFS_DEBUG_PRINT("Error in reading from handle %d, result %d\n", handle, res);
	    return 0;
	}
    };

    size_t FatFsFileManager::fread(void* buf, size_t size, size_t count, FILE* fp) {
	DEBUG_PRINT_FUNCTION;

	if (size == 0 || count == 0 || buf == NULL || fp == NULL) {
	    return 0;
	};

	FIL* fil_ptr = reinterpret_cast<FIL*>(fp);

	UINT read_bytes = 0;
	const FRESULT res = f_read(fil_ptr, buf, count, &read_bytes);

	if (res == FR_OK) {
	    return read_bytes;	
	} else {
	    return 0;
	}
    };


    ssize_t FatFsFileManager::pread(int fd, void* buf, size_t count, off_t offset) {
	DEBUG_PRINT_FUNCTION;

	if (count == 0 || buf == NULL) {
	    return 0;
	};

	std::lock_guard<std::mutex> lock(file_mutex_);
	const FileHandle handle = fd;
	const auto it = files_.find(handle);

	if (it == files_.end()) {
	    return -1;
	}

	FIL* fil_ptr = it->second;    
	fil_ptr->fptr = offset;    
	UINT read_bytes = 0;
	const FRESULT res = f_read(fil_ptr, buf, count, &read_bytes);

	if (res == FR_OK) {
	    return read_bytes;	
	} else {
	    return 0;
	}
    };
    

    FILE* FatFsFileManager::fdopen(int fd, const char *mode) {
	DEBUG_PRINT_FUNCTION;

	if (mode == NULL) {
	    return NULL;
	}
    
	std::lock_guard<std::mutex> lock(file_mutex_);

	const FileHandle handle = fd;	
	const auto it = files_.find(handle);
    
	if (it != files_.end()) {
	    FIL* fil = it->second;
	    return reinterpret_cast<FILE*>(fil);
	} else {
	    return NULL;
	};
    };
    
    
    FILE *FatFsFileManager::fopen(const char *path_name, const char *mode) {

	if (mode == NULL || path_name == NULL) {
	    return NULL;
	}
    
	const BYTE fatfs_mode = parsePosixModeFlag(std::string(mode));

	std::lock_guard<std::mutex> lock(file_mutex_);
	const std::string path_name_str(path_name);

	const auto it = file_paths_.find(path_name_str);

	if (it != file_paths_.end()) {
	    //  When opening again the same file, we do a f_sync (flush), so that we can read it correctly.	    
	    FileHandle old_handle = it->second;
	    FIL* old_fil = files_.at(old_handle);

	    const FRESULT res_sync = f_sync(old_fil);
	    FATFS_DEBUG_PRINT("File %s, handle %d previously opened, synced with result %d\n", path_name, old_handle, res_sync);
	    
	    if (res_sync != 0) {
		return NULL;
	    }	
	}
	
	FIL* fil_ptr = new FIL();
	const FRESULT res = f_open(fil_ptr, path_name, fatfs_mode);  

	if (res != FR_OK) {
	    delete(fil_ptr);
	    fil_ptr = NULL;
	    return NULL;
	};
	
	createFileHandle(fil_ptr, path_name_str);
	return reinterpret_cast<FILE*>(fil_ptr);
    };
   
    
    size_t FatFsFileManager::fwrite(const void* buf, size_t size, size_t count, FILE* fp) {
	DEBUG_PRINT_FUNCTION;

	if (size == 0 || count == 0 || fp == NULL) {
	    return 0;
	};
	FIL* fil = reinterpret_cast<FIL*>(fp);

	UINT written_bytes = 0;
	const FRESULT res = f_write(fil, buf, count, &written_bytes);

	if (res == 0) {
	    return written_bytes;
	} else {
	    return 0;
	}
    };


    ssize_t FatFsFileManager::pwrite(int fd, const void *buf, size_t count, off_t offset) {
	DEBUG_PRINT_FUNCTION;

	if (count == 0 || buf == NULL) {
	    return 0;
	};
	std::lock_guard<std::mutex> lock(file_mutex_);

	const FileHandle handle = fd;
	const auto it = files_.find(handle);

	if (it == files_.end()) {
	    return -1;
	}

	FIL* fil_ptr = it->second;    
	fil_ptr->fptr = offset;    
	UINT written_bytes = 0;
	const FRESULT res = f_write(fil_ptr, buf, count, &written_bytes);

	if (res == 0) {
	    return written_bytes;
	} else {
	    return 0;
	}
    };
    

    ssize_t FatFsFileManager::write(int fd, const void *buf, size_t count) {
	FATFS_DEBUG_PRINT("FatFs write %d %lu\n", fd, count);

	std::lock_guard<std::mutex> lock(file_mutex_);

	const FileHandle handle = fd;
	const auto it = files_.find(handle);

	if (it == files_.end()) {	    
	    FATFS_DEBUG_PRINT("Error: handle not found: %d\n", handle);
	    return 1;
	}

	FIL* fil_ptr = it->second;
	UINT written_bytes = 0;

	const FRESULT res = f_write(fil_ptr, buf, count, &written_bytes);

	if (res == 0) {
	    return static_cast<size_t>(written_bytes);
	} else {
	    return 0;
	}
    };


    int FatFsFileManager::fclose(FILE* fp) {
	DEBUG_PRINT_FUNCTION;

	if (fp == NULL) {
	    return EOF;
	}

	std::lock_guard<std::mutex> lock(file_mutex_);
	FIL* fil_ptr = reinterpret_cast<FIL*>(fp);
	return this->closeInternal(fil_ptr);
    };


    static struct timespec convertTime(WORD fdate, WORD ftime) {
	/* We leave this commented out and untested as we do not have "mktime"
	   function available (time.h has been replaced with a minimal conclave-time.h).
	   TO DO: we need to implement mktime by ourselves
	   Note also a similar comment in diskio.cpp->get_fattime

	   fdate
	   bit 15:9 Year origin from 1980 (0..127)
	   bit  8:5 Month (1..12)
	   bit  4:0 Day (1..31)
      
	   ftime
	   bit 15:11 Hour (0..23)
	   bit 10:5  Minute (0..59)
	   bit  4:0  Second / 2 (0..29)

	   tm_sec	int	seconds after the minute	0-61*
	   tm_min	int	minutes after the hour	0-59
	   tm_hour	int	hours since midnight	0-23
	   tm_mday	int	day of the month	1-31
	   tm_mon	int	months since January	0-11
	   tm_year	int	years since 1900	
	   tm_wday	int	days since Sunday	0-6
	   tm_yday	int	days since January 1	0-365
	   tm_isdst	int	Daylight Saving Time flag	
	
	   const unsigned int mask_year = ((1 << 7) - 1) << 9;
	   const unsigned int mask_month = ((1 << 4) - 1) << 5;
	   const unsigned int mask_day = ((1 << 5) - 1);
	   const unsigned int mask_hour = ((1 << 5) - 1) << 11;
	   const unsigned int mask_minute = ((1 << 6) - 1) << 5;
	   const unsigned int mask_second = ((1 << 4) - 1);

	   const unsigned int year = (mask_year & fdate) >> 9;
	   const unsigned int month = (mask_month & fdate) >> 5;
	   const unsigned int day = (mask_day & fdate);
	   const unsigned int hour = (mask_hour & fdate) >> 11;
	   const unsigned int minute = (mask_minute & fdate) >> 5;
	   const unsigned int second = (mask_second & fdate) * 2;

	   struct tm time_str;
	   time_str.tm_year = 1980 - 1900 + year;
	   time_str.tm_mon = month - 1;
	   time_str.tm_mday = day;
	   time_str.tm_hour = hour;
	   time_str.tm_min = minute;
	   time_str.tm_sec = second;
	   time_str.tm_isdst = -1;

	   struct timespec t_timespec;
	   t_timespec.tv_sec = mktime(&time_str);
	   t_timespec.tv_nsec = t_timespec.tv_sec * 1000;
	   FATFS_DEBUG_PRINT("convertTime, %u %u %u %u %u %u\n", day, month, year, hour, minute, second);
	*/

	timespec t_timespec = timespec();	
	return t_timespec;
    }

    static FRESULT statWithRoot(const char* path_name, FILINFO* info) {
	DEBUG_PRINT_FUNCTION;

	if (strcmp(path_name, "/") == 0) {
	    //  FatFs does not accept the root directory as input parameter, so
	    //    we do not call f_stat and return a minimal empty structure.
	    info->fsize = 0;
	    info->fdate = 0;
	    info->ftime = 0;
	    info->fattrib = AM_DIR;
	    return FR_OK;
	} else {
	    return f_stat(path_name, info);
	}
    }

    int FatFsFileManager::statInternal(const char* path_name, struct stat* stat_buf, int& err) {
	FILINFO info;	
	const FRESULT res = statWithRoot(path_name, &info);
    
	if (res == FR_OK) {
	    memset(stat_buf, 0, sizeof(struct stat));
	    stat_buf->st_size = info.fsize;
	    stat_buf->st_mtim = convertTime(info.fdate, info.ftime);

	    if (info.fattrib & AM_DIR) {
		stat_buf->st_mode |= S_IFDIR;
	    };
	    if (info.fattrib & AM_ARC & ~AM_DIR & ~AM_HID & ~AM_SYS) {
		stat_buf->st_mode |= S_IFREG;
	    };

	    return 0;
	} else {
	    FATFS_DEBUG_PRINT("Error: statInternal, result: %d\n", res);
	    if (res == FR_NO_FILE || res == FR_NO_PATH || res == FR_INVALID_NAME) {
		err = ENOENT;
	    }
	    return -1;
	}
    }


    int FatFsFileManager::statInternal64(const char* path_name, struct stat64* stat_buf, int& err) {
	FILINFO info;
	const FRESULT res = statWithRoot(path_name, &info);
    
	if (res == FR_OK) {
	    memset(stat_buf, 0, sizeof(struct stat64));
	    stat_buf->st_size = info.fsize;
	    stat_buf->st_mtim = convertTime(info.fdate, info.ftime);

	    if (info.fattrib & AM_DIR) {
		stat_buf->st_mode |= S_IFDIR;
	    };
	    if (info.fattrib & AM_ARC & ~AM_DIR & ~AM_HID & ~AM_SYS) {
		stat_buf->st_mode |= S_IFREG;
	    };

	    return 0;
	} else {
	    FATFS_DEBUG_PRINT("Error: statInternal64, result: %d\n", res);
	    if (res == FR_NO_FILE || res == FR_NO_PATH || res == FR_INVALID_NAME) {
		err = ENOENT;
	    }
	    return -1;
	}
    }


    int FatFsFileManager::lstat(const char* path_name, struct stat* stat_buf, int& err) {
	DEBUG_PRINT_FUNCTION;

	return this->statInternal(path_name, stat_buf, err);
    }

    
    int FatFsFileManager::lstat64(const char* path_name, struct stat64* stat_buf, int& err) {
	DEBUG_PRINT_FUNCTION;

	return this->statInternal64(path_name, stat_buf, err);
    }

    
    int FatFsFileManager::closeInternal(FIL* fil_ptr) {
	const FileHandle file_handle = inverse_files_map_.at(fil_ptr);
	const FRESULT res = f_close(fil_ptr);

	if (res == FR_OK) {
	    const std::string path_name = inverse_file_paths_.at(file_handle);

	    files_.erase(file_handle);
	    file_paths_.erase(path_name);
	    inverse_files_map_.erase(fil_ptr);
	    inverse_file_paths_.erase(file_handle);
	    delete(fil_ptr);
	    fil_ptr = NULL;
	    FATFS_DEBUG_PRINT("closeInternal successful, removed handle: %d, path: %s\n", file_handle, path_name.c_str());
	    return 0;
	} else {
	    FATFS_DEBUG_PRINT("closeInternal error %d\n", res);
	    return -1;
	}
    }
    
    int FatFsFileManager::close(int fd) {
	FATFS_DEBUG_PRINT("Closing file handle %d\n", fd);

	std::lock_guard<std::mutex> lock(file_mutex_);

	const FileHandle handle = fd;   
	auto it = files_.find(handle);

	if (it == files_.end()) {
	    //  Here we are not returning an error as the handle has already
	    //    been closed. This has probably happened because we attempted to
	    //    open the file twice.
	    FATFS_DEBUG_PRINT("Handle not found %d\n", fd);
	    return 0;
	}

	FIL* fil_ptr = it->second;

	if (fil_ptr == NULL) {
	    //  The descriptor id is in the map but with a NULL pointer as a value.
	    //    This is the case of dummy file descriptors used in socketpair.
	    FATFS_DEBUG_PRINT("Closing handle %d without file\n", fd);
	    files_.erase(handle);
	    inverse_file_paths_.erase(handle);
	    return 0;
	} else {
	    return this->closeInternal(fil_ptr);
	}
    };

    int FatFsFileManager::fstat(int ver,
				int fd,
				struct stat64* stat_buf,
				unsigned int num_bytes,
				int& err) {
	DEBUG_PRINT_FUNCTION;

	std::lock_guard<std::mutex> lock(file_mutex_);

	const FileHandle handle = fd;
	const auto it = inverse_file_paths_.find(handle);

	if (it == inverse_file_paths_.end()) {
	    return -1;
	}

	const std::string path_name = it->second;    
	return this->statInternal64(path_name.c_str(), stat_buf, err);
    };

    
    int FatFsFileManager::stat(int ver,
			       const char* path_name,
			       struct stat64* stat_buf,
			       unsigned int num_bytes,
			       int& err) {
	DEBUG_PRINT_FUNCTION;
	return this->statInternal64(path_name, stat_buf, err);
    };
   
    
    int FatFsFileManager::mkdir(const char* pathname, mode_t mode) {
	FATFS_DEBUG_PRINT("Mkdir %s with mode %d\n", pathname, mode);

	if (pathname == NULL) {
	    return -1;
	}

	if (strcmp(pathname, "/") == 0) {
	    //  FatFs does not accept f_mkdir to be called with the root directory as input.
	    //    So we return successfully anyway and prevent the call.
	    return 0;
	}

	const FRESULT res = f_mkdir(pathname);

	if (res == FR_OK) {
	    FATFS_DEBUG_PRINT("Mkdir %s succeeded\n", pathname);
	    return 0;
	} else {
	    FATFS_DEBUG_PRINT("Mkdir %s failed with result %d\n", pathname, res);
	    return -1;
	}
    };


    int FatFsFileManager::access(const char* pathname, mode_t mode, int& err) {
	FATFS_DEBUG_PRINT("Accessing path %s with mode %d\n", pathname, mode);

	if (strcmp(pathname, "/") == 0) {
	    //  FatFs does not accept f_stat to be called with the root directory as input.
	    //    So we return successfully anyway and prevent the call.
	    FATFS_DEBUG_PRINT("Path accessed is root directory %s\n", pathname);
	    return 0;
	}
	
	if (pathname == NULL) {
	    return -1;
	}

	FILINFO info;
	const FRESULT res = f_stat(pathname, &info);

	if (res == FR_OK) {
	    //  We always give access to files or directories if they exist, no
	    //    specific user permissions needed.
	    return 0;
	} else {
	    FATFS_DEBUG_PRINT("Error: failure in accessing path %s with result %d\n", pathname, res);
	    err = ENOENT;
	    return -1;
	}
    };

    
    int FatFsFileManager::unlinkInternal(const char* path, int& err) {

	if (path == NULL) {
	    return -1;
	}

	const FRESULT res = f_unlink(path);

	if (res == FR_OK) {
	    FATFS_DEBUG_PRINT("Path %s unlinked/removed successfully\n", path);
	    return 0;
	} else {
	    if (res == FR_DENIED) {
		err = ENOTEMPTY;
	    } else if (res == FR_NO_PATH) {
		err = ENOENT;    
	    }
	    FATFS_DEBUG_PRINT("Error: unlinking failure with result %d\n", res);
	    return -1;
	}
    };
    
    int FatFsFileManager::unlink(const char* path, int& res) {
	DEBUG_PRINT_FUNCTION;
	return this->unlinkInternal(path, res);
    };
    

    int FatFsFileManager::rmdir(const char* path, int& res) {
	DEBUG_PRINT_FUNCTION;
	return this->unlinkInternal(path, res);
    };

    int FatFsFileManager::remove(const char* path, int& res) {
	DEBUG_PRINT_FUNCTION;
        //  FatFs delete files and dir with the same function "unlink"
	return this->unlinkInternal(path, res);
    };
    
    int FatFsFileManager::chdir(const char* path) {
	DEBUG_PRINT_FUNCTION;

	if (path == NULL) {
	    return -1;
	}
	const FRESULT res = f_chdir(path);

	if (res == FR_OK) {
	    return 0;
	} else {
	    return -1;
	}
    };


    char* FatFsFileManager::getcwd(char* buf, size_t size) {
	DEBUG_PRINT_FUNCTION;

	if (buf == NULL) {
	    return NULL;
	}
	const FRESULT res = f_getcwd(buf, size);

	if (res == FR_OK) {
	    return buf;
	} else {
	    return NULL;
	}
    };

    int FatFsFileManager::socketpair(int domain, int type, int protocol, int sv[2]) {
	DEBUG_PRINT_FUNCTION;
	/* 
	   Used in Java File classes C implementation (FileDispatcherImpl.c) in the context of 
	   copying file descriptor to prevent race conditions when closing them (see dup2).
	   What we really need here is a couple of dummy descriptors to make Java happy, nothing else,
	   as we manage descriptors ourselves.
	   We don't even bother to close (i.e. remove from maps) one of the descriptor as in 
	   FileDispatcherImpl.c, we rely on the destructor to do so.
	*/
	std::lock_guard<std::mutex> lock(file_mutex_);

	FileHandle handle1 = getNewHandle();
	FileHandle handle2 = getNewHandle();

	sv[0] = static_cast<int>(handle1);
	sv[1] = static_cast<int>(handle2);

	files_[handle1] = NULL;
	inverse_file_paths_[handle1] = std::string();
	files_[handle2] = NULL;
	inverse_file_paths_[handle2] = std::string();	
	return 0;
	
    }

    int FatFsFileManager::dup2(int oldfd, int newfd) {
	DEBUG_PRINT_FUNCTION;
	/*  
	    In Java File classes, dup2 seems to be used to close the target
	    descriptor in a mechanism to prevent race conditions, where
	    the original file descriptor is copied into a target descriptor
	    and then the original descriptor is closed.

	    As in FatFs we do not have problem of race conditions (we have
	    a mutex lock for each operation), here we can close the second 
	    descriptor and simply skip the copy of the old to the new one.
	*/
	int res = this->close(newfd);
	FATFS_DEBUG_PRINT("dup2 from fd %d to %d\n", oldfd, newfd);
	return res;
    };    


    FileHandle FatFsFileManager::getNewHandle() {
	return next_available_handle_++;
    };
    

    FileHandle FatFsFileManager::createFileHandle(FIL* fil_ptr, const std::string& path) {
	FileHandle handle = getNewHandle();
	files_[handle] = fil_ptr;
	file_paths_[path] = handle;
	inverse_files_map_[fil_ptr] = handle;
	inverse_file_paths_[handle] = path;
	FATFS_DEBUG_PRINT("Created handle %d for file %s\n", handle, path.c_str());
	return handle;
    }
};
