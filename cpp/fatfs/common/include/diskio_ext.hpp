#ifndef _DISKIO_EXT
#define _DISKIO_EXT

/*
  This header is not part of diskio.hpp because we want to leave the original
  FatFs code as untouched as possible
*/

#include <memory>
#include <string>

#include "disk.hpp"
#include "diskio.hpp"
#include "fatfs_result.hpp"

FatFsResult disk_register(const BYTE drive,
                          const std::shared_ptr<conclave::FatFsDisk>& disk_handler);

FatFsResult disk_unregister(const BYTE drive);

FatFsResult disk_start(const std::shared_ptr<conclave::FatFsDisk>& disk_handler,
                       const conclave::DiskInitialization init_type);

FatFsResult disk_stop(const BYTE drive, const std::string& drive_text_id);

#endif  //  End of _DISKIO_EXT
