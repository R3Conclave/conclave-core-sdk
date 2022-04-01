#include <algorithm>

#include "diskio_ext.hpp"
#include "disk.hpp"
#include "fatfs_file_manager.hpp"

static const std::string kRootPath = "/";

namespace conclave {

    static std::unordered_map<int, BYTE> createFlagMap() {
        DEBUG_PRINT_FUNCTION;
        /*
          http://elm-chan.org/fsw/ff/doc/open.html
          POSIX FatFs
          "r"   FA_READ
          "r+"  FA_READ | FA_WRITE
          "w"   FA_CREATE_ALWAYS | FA_WRITE
          "w+"  FA_CREATE_ALWAYS | FA_WRITE | FA_READ
          "a"   FA_OPEN_APPEND | FA_WRITE
          "a+"  FA_OPEN_APPEND | FA_WRITE | FA_READ
          "wx"  FA_CREATE_NEW | FA_WRITE
          "w+x" FA_CREATE_NEW | FA_WRITE | FA_READ
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
        DEBUG_PRINT_FUNCTION;
        //  We only allow a specific set of Posix flag
        const int mask = O_RDONLY |O_RDWR | O_WRONLY | O_CREAT | O_TRUNC | O_APPEND | O_EXCL;
        const static auto flags = createFlagMap();
        const int masked_fatfs_flag = mask & posix_flag;
        const BYTE fatfs_flag = flags.at(masked_fatfs_flag);
        FATFS_DEBUG_PRINT("Converted from Posix %d, %04X to fatfs flag %d\n",
                          posix_flag, posix_flag, (unsigned int)fatfs_flag);
        return fatfs_flag;
    }


    static std::unordered_map<std::string, BYTE> createPosixModeFlagMap() {
        DEBUG_PRINT_FUNCTION;
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


    FatFsFileManager::~FatFsFileManager() {
        DEBUG_PRINT_FUNCTION;
        disk_handler_->diskStop();
        disk_stop(disk_handler_->getDriveId(), drive_text_id_);
        
        for (auto& kv : files_) {
            FIL* fil_ptr = kv.second;

            if (fil_ptr != nullptr) {
                delete(kv.second);
            }
        }
        
        for (auto& kv : dirents_) {
            struct dirent* dir_ptr = kv.second;

            if (dir_ptr != nullptr) {
                free(dir_ptr);
            }
        }

        for (auto& kv : dirents64_) {
            struct dirent64* dir_ptr = kv.second;

            if (dir_ptr != nullptr) {
                free(dir_ptr);
            }
        }
    };
           
    
    FatFsFileManager::FatFsFileManager(const int first_handle,
                                       const int max_handle,
                                       const unsigned char* encryption_key,
                                       const std::string& mount_path,
                                       const std::shared_ptr<FatFsDisk>& disk_handler):
        first_handle_(first_handle),
        next_handle_(first_handle),
        last_handle_(max_handle),
        mount_path_(mount_path) {

        disk_handler_ = disk_handler;
        drive_text_id_ = std::to_string(disk_handler_->getDriveId()) + ":";
    }


    std::string FatFsFileManager::getMountPath() {
        return mount_path_;
    }
   

    FatFsResult FatFsFileManager::init(const DiskInitialization init_type) {
        disk_handler_->diskStart();

        /*
          diskio.cpp->disk_start is the FatFs related call to register functions, 
          run mkfs (if required) and mount the filesystem.
          Given that we do not want to change the FatFs code much, we prefer to pass the disk_handler_
          shared pointer to disk_start function, even if we lose a bit of encapsulation.
          diskio.cpp->disk_* functions consist in the bridge between FatFs abstraction
          and in-memory/persistent filesystems.
        */
        FatFsResult res_disk_start = disk_start(disk_handler_, init_type);
        
        if (res_disk_start != FatFsResult::OK) {
            return res_disk_start;
        }
        if (mount_path_ != kRootPath) {
            const int res_mkdir = mkdir(mount_path_.c_str(), 0);
            
            if (res_mkdir != 0) {
                return FatFsResult::ROOT_DIRECTORY_MOUNT_FAILED;
            }
        }
        return FatFsResult::OK;
    }

    
    bool FatFsFileManager::isPathOwner(const std::string& path) {
        return path.length() > 0 &&
            (path.find(mount_path_, 0) == 0 ||  //  Path starts with mount_path
             path == mount_path_.substr(0, mount_path_.size() - 1));  // Path is the mount_path (without / at the end)
    }


    bool FatFsFileManager::isHandleOwner(const int handle) {
        return handle != -1 && handle >= first_handle_ && handle <= last_handle_;
    }


    bool FatFsFileManager::isDirOwner(const DIR* dir) {
        return inverse_dir_paths_.find(dir) != inverse_dir_paths_.end();   
    }


    std::string FatFsFileManager::generateFatFsPath(const char* path) {
        //  This functions adds the drive identifier at the beginning of the path.
        //    This is a FatFs requirement to access files in the correct drive.
        //  An example of FatFs path is the following: "0:/mydir/myfile.txt"
        if (path == nullptr) {
            return std::string();
        }
        return drive_text_id_ + std::string(path);
    }
    
  
    int FatFsFileManager::closeInternal(FIL* fil_ptr) {
        const FileHandle file_handle = inverse_files_map_.at(fil_ptr);
        const FRESULT res = f_close(fil_ptr);

        if (res == FR_OK) {
            const std::string path = inverse_file_paths_.at(file_handle);

            files_.erase(file_handle);
            file_paths_.erase(path);
            inverse_files_map_.erase(fil_ptr);
            inverse_file_paths_.erase(file_handle);
            delete(fil_ptr);
            fil_ptr = nullptr;
            FATFS_DEBUG_PRINT("closeInternal successful, removed handle: %d, path: %s\n", file_handle, path.c_str());
            return 0;
        } else {
            FATFS_DEBUG_PRINT("closeInternal error %d\n", res);
            return -1;
        }
    }
    

    int FatFsFileManager::unlinkInternal(const std::string& path, int& err) {

        if (path.empty()) {
            return -1;
        }
        const FRESULT res = f_unlink(path.c_str());

        if (res == FR_OK) {
            FATFS_DEBUG_PRINT("Path %s unlinked/removed successfully\n", path.c_str());
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
    

    FRESULT FatFsFileManager::statWithRoot(const char* path_in, FILINFO* info) {
        FATFS_DEBUG_PRINT("statWithRoot %s\n", path_in);

        if (strcmp(path_in, kRootPath.c_str()) == 0) {
            //  FatFs does not accept the root directory as input parameter, so
            //    we do not call f_stat and return a minimal empty structure.
            info->fsize = 0;
            info->fdate = 0;
            info->ftime = 0;
            info->fattrib = AM_DIR;
            return FR_OK;
        } else {
            const std::string path = generateFatFsPath(path_in); 
            return f_stat(path.c_str(), info);
        }
    }


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

           tm_sec       int     seconds after the minute        0-61*
           tm_min       int     minutes after the hour  0-59
           tm_hour      int     hours since midnight    0-23
           tm_mday      int     day of the month        1-31
           tm_mon       int     months since January    0-11
           tm_year      int     years since 1900        
           tm_wday      int     days since Sunday       0-6
           tm_yday      int     days since January 1    0-365
           tm_isdst     int     Daylight Saving Time flag       
        
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


    int FatFsFileManager::statInternal(const char* path_in, struct stat* stat_buf, int& err) {
        FILINFO info;
        const FRESULT res = statWithRoot(path_in, &info);
    
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
    
    int FatFsFileManager::statInternal64(const char* path_in,
                                         struct stat64* stat_buf,
                                         int& err) {
        FILINFO info;
        const FRESULT res = statWithRoot(path_in, &info);
    
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


    off_t FatFsFileManager::lseekInternal(int fd, off_t offset, int whence) {
        FATFS_DEBUG_PRINT("lseekInternal fd %d, offset %ld, command %d\n", fd, offset, whence);
        
        if (whence == SEEK_CUR && offset == 0) {
            //  This is a no-op case, so we return successfully
            return 0;
        }
        const FileHandle handle = fd;
        const auto it = files_.find(handle);

        if (it == files_.end()) {
            FATFS_DEBUG_PRINT("Error: handle not found: %d\n", handle);
            return -1;
        }
        FIL* fil_ptr = it->second;

        if (whence == SEEK_CUR) {
            off_t cur = (off_t)f_tell(fil_ptr);
            offset = cur + offset;
        } else if (whence == SEEK_END) {
            off_t end = (off_t)f_size(fil_ptr);
            offset = end + offset;
        }
        const FRESULT res = f_lseek(fil_ptr, offset);

        if (res == FR_OK) {
            return 0;
        } else {
            FATFS_DEBUG_PRINT("Error in seeking from handle %d, result %d\n", handle, res);
            return -1;
        }
    };
    

    FileHandle FatFsFileManager::getNewHandle() {
        int i = 0;

        if (next_handle_ == last_handle_ + 1) {
            next_handle_ = first_handle_;
        }
        
        const int num_handles = last_handle_ - first_handle_ + 1;

        while (files_.find(next_handle_) != files_.end() && i < num_handles) {
            FATFS_DEBUG_PRINT("Scanning handle %d\n", next_handle_);
            
            if (next_handle_ == last_handle_) {
                next_handle_ = first_handle_;    
            } else {
                next_handle_ ++;
            }
            i++;
        }

        //  This is in case all handles are not available
        if (i == num_handles) {
            FATFS_DEBUG_PRINT("No handles available, returning %d\n", -1);
            return -1;
        }
        FATFS_DEBUG_PRINT("Returning handle %d\n", next_handle_ + 1);
        return next_handle_++;
    };
    

    void FatFsFileManager::insertFileHandle(const FileHandle handle,
                                            FIL* fil_ptr,
                                            const std::string& path) {
        files_[handle] = fil_ptr;
        file_paths_[path] = handle;
        inverse_files_map_[fil_ptr] = handle;
        inverse_file_paths_[handle] = path;
        FATFS_DEBUG_PRINT("Created handle %d for file %s\n", handle, path.c_str());
    }

    void FatFsFileManager::addDirHandle(DIR* dir_ptr, const std::string& path) {
        dir_paths_[path] = dir_ptr;
        inverse_dir_paths_[dir_ptr] = path;
    }
    

    int FatFsFileManager::open(const char* path_in, int oflag, int& err) {
        const std::string path = generateFatFsPath(path_in);
        FATFS_DEBUG_PRINT("Opening file: %s\n", path.c_str());
        std::lock_guard<std::mutex> lock(file_mutex_);

        if (path.empty()) {
            return -1;
        }
        const BYTE fatfs_mode_flag = convertFlag(oflag);
        const std::string path_str = path;
        const auto it = file_paths_.find(path_str);

        if (it != file_paths_.end()) {
            //  When opening again the same file, we do a f_sync (flush), so that we can read it correctly.   
            const FileHandle old_handle = it->second;
            FIL* old_fil = files_.at(old_handle);
            const FRESULT res_sync = f_sync(old_fil);
            FATFS_DEBUG_PRINT("File %s, handle %d previously opened, synced with result %d\n", path.c_str(), old_handle, res_sync);
            
            if (res_sync != 0) {
                err = ENOENT;
                return -1;
            }   
        }
        const FileHandle file_handle = getNewHandle();

        if (file_handle == -1) {
            err = EMFILE;
            return -1;
        }
        FIL* fil_ptr = new FIL();
        const FRESULT res = f_open(fil_ptr, path.c_str(), fatfs_mode_flag);
            
        if (res != FR_OK) {
            FATFS_DEBUG_PRINT("File not opened, with failure: %d\n", res);
            delete(fil_ptr);
            fil_ptr = nullptr;
            err = ENOENT;
            return -1;
        };
        insertFileHandle(file_handle, fil_ptr, path_str);
        return file_handle;
    };
  

    off_t FatFsFileManager::lseek(int fd, off_t offset, int whence) {
        FATFS_DEBUG_PRINT("lseek fd %d, offset %ld, command %d\n", fd, offset, whence);
        std::lock_guard<std::mutex> lock(file_mutex_);

        return lseekInternal(fd, offset, whence);
    };

    
    ssize_t FatFsFileManager::read(int fd, void* buf, size_t count) {
        DEBUG_PRINT_FUNCTION;
        std::lock_guard<std::mutex> lock(file_mutex_);

        if (buf == nullptr || count == 0) {
            return 0;
        }
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
        std::lock_guard<std::mutex> lock(file_mutex_);

        if (size == 0 || count == 0 || buf == nullptr || fp == nullptr) {
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
        std::lock_guard<std::mutex> lock(file_mutex_);

        if (count == 0 || buf == nullptr) {
            return 0;
        };
        const FileHandle handle = fd;
        const auto it = files_.find(handle);

        if (it == files_.end()) {
            return -1;
        }

        FIL* fil_ptr = it->second;    
        FRESULT res = f_lseek(fil_ptr, offset);

        if (res != FR_OK) {
            return 0;
        }
        UINT read_bytes = 0;
        res = f_read(fil_ptr, buf, count, &read_bytes);

        if (res == FR_OK) {
            return read_bytes;
        } else {
            return 0;
        }
    };
    

    FILE* FatFsFileManager::fdopen(int fd, const char *mode) {
        DEBUG_PRINT_FUNCTION;
        std::lock_guard<std::mutex> lock(file_mutex_);

        if (mode == nullptr) {
            return nullptr;
        }
        const FileHandle handle = fd;
        const auto it = files_.find(handle);
    
        if (it != files_.end()) {
            FIL* fil = it->second;
            return reinterpret_cast<FILE*>(fil);
        } else {
            return nullptr;
        };
    };
    
    
    FILE *FatFsFileManager::fopen(const char* path_in, const char *mode, int& err) {
        const std::string path = generateFatFsPath(path_in);
        DEBUG_PRINT_FUNCTION;
        std::lock_guard<std::mutex> lock(file_mutex_);

        if (mode == nullptr || path.empty()) {
            return nullptr;
        }    
        const BYTE fatfs_mode = parsePosixModeFlag(std::string(mode));
        const std::string path_str(path);

        const auto it = file_paths_.find(path_str);

        if (it != file_paths_.end()) {
            //  When opening again the same file, we do a f_sync (flush), so that we can read it correctly.    
            FileHandle old_handle = it->second;
            FIL* old_fil = files_.at(old_handle);
            const FRESULT res_sync = f_sync(old_fil);
            FATFS_DEBUG_PRINT("File %s, handle %d previously opened, synced with result %d\n", path.c_str(), old_handle, res_sync);
            
            if (res_sync != 0) {
                err = ENOENT;
                return nullptr;
            }   
        }       
        const FileHandle file_handle = getNewHandle();

        if (file_handle == -1) {
            err = EMFILE;
            return nullptr;
        }
        FIL* fil_ptr = new FIL();
        const FRESULT res = f_open(fil_ptr, path.c_str(), fatfs_mode);  

        if (res != FR_OK) {
            delete(fil_ptr);
            fil_ptr = nullptr;
            err = ENOENT;
            return nullptr;
        };
        insertFileHandle(file_handle, fil_ptr, path_str);
        return reinterpret_cast<FILE*>(fil_ptr);
    };
   
    
    size_t FatFsFileManager::fwrite(const void* buf, size_t size, size_t count, FILE* fp) {
        DEBUG_PRINT_FUNCTION;
        std::lock_guard<std::mutex> lock(file_mutex_);

        if (size == 0 || count == 0 || fp == nullptr) {
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
        FATFS_DEBUG_PRINT("FatFs pwrite %d %lu %lu \n", fd, count, offset);
        std::lock_guard<std::mutex> lock(file_mutex_);

        if (count == 0 || buf == nullptr) {
            return 0;
        };
        const FileHandle handle = fd;
        const auto it = files_.find(handle);

        if (it == files_.end()) {
            return -1;
        }
        FIL* fil_ptr = it->second;    
        UINT written_bytes = 0;

        if (lseekInternal(fd, offset, SEEK_SET) == -1) {
            return 0;
        };

        FRESULT res = f_write(fil_ptr, buf, count, &written_bytes);

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
        std::lock_guard<std::mutex> lock(file_mutex_);

        if (fp == nullptr) {
            return EOF;
        }
        FIL* fil_ptr = reinterpret_cast<FIL*>(fp);
        return this->closeInternal(fil_ptr);
    };


    int FatFsFileManager::lstat(const char* path_in, struct stat* stat_buf, int& err) {
        DEBUG_PRINT_FUNCTION;
        std::lock_guard<std::mutex> lock(file_mutex_);
        return this->statInternal(path_in, stat_buf, err);
    }


    int FatFsFileManager::isDirOpen(const std::string& path) {
        DEBUG_PRINT_FUNCTION;
        const auto it = dir_paths_.find(path);
        
        if (it != dir_paths_.end()) {
            FATFS_DEBUG_PRINT("The directory %s is currently opened\n", path.c_str());
            return -1;
        }        
        return 0;
    }


    int FatFsFileManager::isFileOpen(const std::string& path) {
        DEBUG_PRINT_FUNCTION;
        const auto it = file_paths_.find(path);

        if (it != file_paths_.end()) {
            FATFS_DEBUG_PRINT("The file %s is currently opened\n", path.c_str());
            return -1;
        }
        return 0;
    }
        

    int FatFsFileManager::isFileInDirOpen(const std::string& path) {
        DEBUG_PRINT_FUNCTION;
        std::string path_dir = path;
        //  We add a '/' at the end, as this is needed in the find here below  file_path.first.find(path_dir)
        //    Specifically, we do not want that a file_path like "/tmpmyfile" and a path_dir "/tmp"
        //      match the condition and return an unexpected error. Therefore we want
        //  "/tmpmyfile" to be validated against "/tmp/" and not "/tmp", so that this does not happen.
        if (path_dir.back() != '/') {
            path_dir = path + "/";
        }
        
        for (auto& file_path: file_paths_) {
            // We check if a file contained in our dir is open. In that case we refuse to rename.
            if (file_path.first.find(path_dir) != std::string::npos) {
                return -1;
            }
        }
        return 0;
    }

    int FatFsFileManager::renameFileInternal(const std::string& oldpath, const std::string& newpath, int& err) {
        DEBUG_PRINT_FUNCTION;

        if (isFileOpen(oldpath) != 0 || isFileOpen(newpath) != 0) {
            err = EBUSY;
            return -1;
        }        
        const FRESULT res = f_rename(oldpath.c_str(), newpath.c_str());
        
        if (res != FR_OK) {
            FATFS_DEBUG_PRINT("File not renamed, with failure: %d\n", res);
            err = ENOENT;
            return -1;
        };    
        return 0;
    }

    int FatFsFileManager::renameDirInternal(const std::string& oldpath, const std::string& newpath, int& err) {
        DEBUG_PRINT_FUNCTION;

        if (isDirOpen(oldpath) != 0 || isDirOpen(newpath) != 0 ||
            isFileInDirOpen(oldpath) != 0 || isFileInDirOpen(newpath) != 0) {
            err = EBUSY;
            return -1;
        }        
        const FRESULT res = f_rename(oldpath.c_str(), newpath.c_str());
        
        if (res != FR_OK) {
            FATFS_DEBUG_PRINT("Dir not renamed, with failure: %d\n", res);
            err = ENOENT;
            return -1;
        };
        FATFS_DEBUG_PRINT("Dir renamed successfully with result %d\n", res);
        return 0;
    }
    
    int FatFsFileManager::rename(const char* oldcpath, const char* newcpath, int& err) {
        DEBUG_PRINT_FUNCTION;
        std::lock_guard<std::mutex> lock(file_mutex_);

        const std::string oldpath = generateFatFsPath(oldcpath);
        const std::string newpath = generateFatFsPath(newcpath);

        if (oldpath.empty() || newpath.empty()) {
            err = ENOENT;
            return -1;
        }
        int stat_err = 0;
        struct stat64 result_stat;
        const int stat_res_old = this->statInternal64(oldcpath, &result_stat, stat_err);
        
        const bool is_file = (stat_res_old == 0 && result_stat.st_mode == S_IFREG);
        const bool is_dir = (stat_res_old == 0 && result_stat.st_mode == S_IFDIR);
    
        FATFS_DEBUG_PRINT("Renaming from %s to %s, %d %d \n", oldpath.c_str(), newpath.c_str(), is_file, is_dir);
        int res = 0;
        
        if (is_file) {
            res = renameFileInternal(oldpath, newpath, err);
            FATFS_DEBUG_PRINT("Renaming completed with result %d\n", res);
        } else if (is_dir) {
            res = renameDirInternal(oldpath, newpath, err);
            FATFS_DEBUG_PRINT("Renaming completed with result %d\n", res);
        } else {
            res = ENOENT;
        }
        return res;
    }   
    
    int FatFsFileManager::lstat64(const char* path_in, struct stat64* stat_buf, int& err) {
        DEBUG_PRINT_FUNCTION;
        std::lock_guard<std::mutex> lock(file_mutex_);
        return this->statInternal64(path_in, stat_buf, err);
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

        if (fil_ptr == nullptr) {
            FATFS_DEBUG_PRINT("Closing handle %d without file\n", fd);
            files_.erase(handle);
            inverse_file_paths_.erase(handle);
            return -1;
        } else {
            return this->closeInternal(fil_ptr);
        }
    };

    int FatFsFileManager::fstat(int ver,
                                int fd,
                                struct stat64* stat_buf,
                                int& err) {
        DEBUG_PRINT_FUNCTION;
        std::lock_guard<std::mutex> lock(file_mutex_);

        const FileHandle handle = fd;
        const auto it = inverse_file_paths_.find(handle);

        if (it == inverse_file_paths_.end()) {
            return -1;
        }

        std::string path = it->second;
        //  Here "path" is a FatFs style path (for example "0:/mydir/myfile.txt"),
        //    thus, to reuse the code below we need to remove the drive identifier,
        //    "0:/" inthe example
        path = path.substr(drive_text_id_.length(), path.length());
        return this->statInternal64(path.c_str(), stat_buf, err);
    };

    
    int FatFsFileManager::stat(int ver,
                               const char* path_in,
                               struct stat64* stat_buf,
                               int& err) {
        std::lock_guard<std::mutex> lock(file_mutex_);
        return this->statInternal64(path_in, stat_buf, err);
    };
   
    
    int FatFsFileManager::mkdir(const char* path_in, mode_t mode) {
        FATFS_DEBUG_PRINT("Mkdir %s with mode %d\n", path_in, mode);
        std::lock_guard<std::mutex> lock(file_mutex_);
        
        if (path_in == nullptr) {
            return -1;
        }

        if (strcmp(path_in, kRootPath.c_str()) == 0) {
            //  FatFs does not accept f_mkdir to be called with the root directory as input.
            //    So we return successfully anyway and prevent the call.
            return 0;
        }
        const std::string path = generateFatFsPath(path_in);
        const FRESULT res = f_mkdir(path.c_str());

        if (res == FR_OK) {
            FATFS_DEBUG_PRINT("Mkdir %s succeeded\n", path.c_str());
            return 0;
        } else {
            FATFS_DEBUG_PRINT("Mkdir %s failed with result %d\n", path.c_str(), res);
            return -1;
        }
    };
    

    int FatFsFileManager::access(const char* path_in, mode_t mode, int& err) {
        FATFS_DEBUG_PRINT("Accessing path %s with mode %d\n", path_in, mode);
        std::lock_guard<std::mutex> lock(file_mutex_);

        if (strcmp(path_in, kRootPath.c_str()) == 0) {
            //  FatFs does not accept f_stat to be called with the root directory as input.
            //    So we return successfully anyway and prevent the call.
            FATFS_DEBUG_PRINT("Path accessed is root directory %s\n", path_in);
            return 0;
        }
        
        if (path_in == nullptr) {
            return -1;
        }

        const std::string path = generateFatFsPath(path_in);
        FILINFO info;
        const FRESULT res = f_stat(path.c_str(), &info);

        if (res == FR_OK) {
            //  We always give access to files or directories if they exist, no
            //    specific user permissions needed.
            return 0;
        } else {
            FATFS_DEBUG_PRINT("Error: failure in accessing path %s with result %d\n", path.c_str(), res);
            err = ENOENT;
            return -1;
        }
    };

    
    int FatFsFileManager::unlink(const char* path_in, int& err) {
        const std::string path = generateFatFsPath(path_in);
        FATFS_DEBUG_PRINT("unlink path %s\n", path.c_str());
        std::lock_guard<std::mutex> lock(file_mutex_);
        return this->unlinkInternal(path, err);
    };
    

    int FatFsFileManager::rmdir(const char* path_in, int& err) {
        const std::string path = generateFatFsPath(path_in);
        FATFS_DEBUG_PRINT("rmdir path %s\n", path.c_str());
        std::lock_guard<std::mutex> lock(file_mutex_);
        return this->unlinkInternal(path, err);
    };


    int FatFsFileManager::remove(const char* path_in, int& err) {
        const std::string path = generateFatFsPath(path_in);
        FATFS_DEBUG_PRINT("remove path %s\n", path.c_str());
        std::lock_guard<std::mutex> lock(file_mutex_);
        return this->unlinkInternal(path, err);
    };
    

    int FatFsFileManager::chdir(const char* path_in) {
        const std::string path = generateFatFsPath(path_in);
        DEBUG_PRINT_FUNCTION;
        std::lock_guard<std::mutex> lock(file_mutex_);

        if (path.empty()) {
            return -1;
        }
        const FRESULT res = f_chdir(path.c_str());

        if (res == FR_OK) {
            return 0;
        } else {
            return -1;
        }
    };


    char* FatFsFileManager::getcwd(char* buf, size_t size) {
        DEBUG_PRINT_FUNCTION;
        std::lock_guard<std::mutex> lock(file_mutex_);

        if (buf == nullptr) {
            return nullptr;
        }
        const FRESULT res = f_getcwd(buf, size);

        if (res == FR_OK) {
            return buf;
        } else {
            return nullptr;
        }
    };


    int FatFsFileManager::dup2(int oldfd, int newfd) {
        DEBUG_PRINT_FUNCTION;
        std::lock_guard<std::mutex> lock(file_mutex_);
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


    void* FatFsFileManager::opendir(const char* path_in, int& err) {
        const std::string path = generateFatFsPath(path_in);
        FATFS_DEBUG_PRINT("Opening dir: %s\n", path.c_str());

        std::lock_guard<std::mutex> lock(file_mutex_);

        if (path.empty()) {
            err = ENOTDIR;
            return nullptr;
        }
        const auto it = dir_paths_.find(path);
        
        if (it != dir_paths_.end()) {
            FATFS_DEBUG_PRINT("Opening the directory twice: %s\n", path.c_str());
            err = EACCES;
            return nullptr;
        }
        DIR* dir_ptr = new DIR();
        FRESULT res = f_opendir(dir_ptr, path.c_str());

        if (res != FR_OK) {
            err = ENOENT;
            delete dir_ptr;
            return nullptr;
        }
        addDirHandle(dir_ptr, path);
        return dir_ptr;
    }
    
#define DT_DIR  0040000 /* Directory.  */
#define DT_REG  0100000 /* Regular file.  */

    struct dirent64* FatFsFileManager::readdir64(void* dirp, int& err) {
        DEBUG_PRINT_FUNCTION;
        std::lock_guard<std::mutex> lock(file_mutex_);

        if (dirp == nullptr) {
            err = EBADF;
            return nullptr;
        }
        DIR* fatfs_dirp = static_cast<DIR*>(dirp);
        const auto it64 = dirents64_.find((DIR*)fatfs_dirp);
        struct dirent64* dirent64_ptr = nullptr;

        if (it64 == dirents64_.end()) {
            //  It is the first time that the user call readdir for this directory
            //    we allocate a scratch space for that directory.
            //  These will be freed by the destructor of the class, whatever might happen.
            dirent64_ptr = static_cast<struct dirent64*>(calloc(1, sizeof(struct dirent64)));
            dirents64_[fatfs_dirp] = dirent64_ptr;
        } else {
            dirent64_ptr = it64->second;
        }
        
        FILINFO info;
        FRESULT res = f_readdir(fatfs_dirp, &info);

        if (res != FR_OK) {
            err = EBADF;
            return nullptr;
        }
        
        if (info.fname[0] == 0) {
            //  This indicates that we have reached the end of the directory
            //    after calling "readdir64" few times (or that the directory is empty).
            //  We do not return an error but just a null pointer as per "readdir" man page.
            return nullptr;
        }
        dirent64_ptr->d_ino = 0;
        dirent64_ptr->d_off = 0;
        dirent64_ptr->d_reclen = strlen(info.fname);
        dirent64_ptr->d_type = (info.fattrib & AM_DIR) ? DT_DIR : DT_REG;
        strcpy(dirent64_ptr->d_name, info.fname);
        FATFS_DEBUG_PRINT("readdir64: %s\n", dirent64_ptr->d_name);
        return dirent64_ptr;
    }
    
    
    struct dirent* FatFsFileManager::readdir(void* dirp, int& err) {
        DEBUG_PRINT_FUNCTION;
        std::lock_guard<std::mutex> lock(file_mutex_);

        if (dirp == nullptr) {
            err = EBADF;
            return nullptr;
        }
        DIR* fatfs_dirp = static_cast<DIR*>(dirp);
        const auto it = dirents_.find(fatfs_dirp);
        struct dirent* dirent_ptr = nullptr;

        if (it == dirents_.end()) {
            //  It is the first time that the user call readdir for this directory
            //    we allocate a scratch space for that directory.
            //  These will be freed by the destructor of the class, whatever might happen.
            dirent_ptr = static_cast<struct dirent*>(calloc(1, sizeof(struct dirent)));
            dirents_[fatfs_dirp] = dirent_ptr;
        } else {
            dirent_ptr = it->second;
        }
        
        FILINFO info;
        FRESULT res = f_readdir(fatfs_dirp, &info);

        if (res != FR_OK) {
            err = EBADF;
            return nullptr;
        }

        if (info.fname[0] == 0) {
            //  This indicates that we have reached the end of the directory
            //    after calling "readdir" few times (or that the directory is empty).
            //  We do not return an error but just a null pointer as per "readdir" man page.
            return nullptr;
        }       
        dirent_ptr->d_ino = 0;
        dirent_ptr->d_off = 0;
        dirent_ptr->d_reclen = strlen(info.fname);
        dirent_ptr->d_type = (info.fattrib & AM_DIR) ? DT_DIR : DT_REG;
        strcpy(dirent_ptr->d_name, info.fname);
        FATFS_DEBUG_PRINT("readdir: %s\n", dirent_ptr->d_name);
        return dirent_ptr;
    }

    
    int FatFsFileManager::closedir(void* dirp, int& err) {
        DEBUG_PRINT_FUNCTION;
        std::lock_guard<std::mutex> lock(file_mutex_);

        if (dirp == nullptr) {
            err = EBADF;
            return -1;
        }
        DIR* fatfs_dirp = static_cast<DIR*>(dirp);
        const auto it = dirents_.find(fatfs_dirp);
        const auto it64 = dirents64_.find(fatfs_dirp);

        if (it == dirents_.end() && it64 == dirents64_.end()) {
            //  Wrong dirp as input
            err = EBADF;
            return -1;
        }

        if (it != dirents_.end()) {
            struct dirent* dirent_ptr = it->second;
            free(dirent_ptr);
            dirents_.erase(it);
        }

        if (it64 != dirents64_.end()) {
            struct dirent64* dirent64_ptr = it64->second;
            free(dirent64_ptr);
            dirents64_.erase(it64);
        }

        if (f_closedir(fatfs_dirp) != FR_OK) {
            err = EBADF;
            return -1;
        }
        const std::string& path = inverse_dir_paths_.at(fatfs_dirp); 
        dir_paths_.erase(path);
        inverse_dir_paths_.erase(fatfs_dirp);
        return 0;
    }


    int FatFsFileManager::ftruncate(int fd, off_t length, int& err) {
        FATFS_DEBUG_PRINT("ftruncate (fd: %d, offs: %lu\n", fd, length);
        std::lock_guard<std::mutex> lock(file_mutex_);

        if (lseekInternal(fd, length, SEEK_SET) == -1) {
            err = EBADF;
            return -1;
        };

        const FileHandle handle = fd;
        const auto it = files_.find(handle);

        if (it == files_.end()) {
            FATFS_DEBUG_PRINT("Error: handle not found: %d\n", handle);
            err = EBADF;
            return -1;
        }
        FIL* fil_ptr = it->second;

        if (f_truncate(fil_ptr) != FR_OK) {
            errno = EINVAL;
            return -1;
        };
        return 0;
    }

    
    int FatFsFileManager::fchown(int fd, uid_t owner, gid_t group, int& err) {
        FATFS_DEBUG_PRINT("fchown fd: %d %ul\n", fd, owner);
        std::lock_guard<std::mutex> lock(file_mutex_);

        const FileHandle handle = fd;
        const auto it = files_.find(handle);

        if (it == files_.end()) {
            FATFS_DEBUG_PRINT("Error: handle not found: %d\n", handle);
            err = EBADF;
            return -1;
        }
        //  We do not change ownership here, as the Conclave user is the only
        //    user of the filesystem in the Enclave.
        //  Hence if the file descriptor is opened, we always succeed.
        return 0;
    }


    int FatFsFileManager::fchmod(int fd, mode_t mode, int& err) {
        FATFS_DEBUG_PRINT("fchmod fd: %d %ul\n", fd, mode);
        std::lock_guard<std::mutex> lock(file_mutex_);

        const FileHandle handle = fd;
        const auto it = files_.find(handle);

        if (it == files_.end()) {
            FATFS_DEBUG_PRINT("Error: handle not found: %d\n", handle);
            err = EBADF;
            return -1;
        }
        //  We do not change permissions, as the Conclave user is the only
        //    user of the filesystem in the Enclave.
        //  Hence if the file descriptor is opened, we always succeed.
        //  Note that the f_chmod call in FatFs is currently disabled with a flag,
        //    we are currently not using it.
        return 0;
    }

    
    int FatFsFileManager::utimes(const char* path_in, const struct timeval times[2], int& err) {
        FATFS_DEBUG_PRINT("utimes %s\n", path_in);
        std::lock_guard<std::mutex> lock(file_mutex_);
        
        const std::string path = generateFatFsPath(path_in);

        if (path.empty()) {
            err = ENOENT;
            return -1;
        }

        //  We currently do not support any time modification, then we do not modify the
        //  times structure as input
        //  Note that the f_utime call in FatFs is currently disabled with a flag,
        //    we are currently not using it.
        //  Here we just check that the file exists and we return accordingly
        FILINFO info;
        const FRESULT res = f_stat(path.c_str(), &info);

        if (res == FR_OK) {
            return 0;
        } else {
            err = ENOENT;
            return -1;            
        }
    }
};
