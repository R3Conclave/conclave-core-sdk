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

DRESULT disk_register(const BYTE drive,
		      const std::shared_ptr<conclave::FatFsDisk>& disk_handler);

DRESULT disk_unregister(const BYTE drive);

DRESULT disk_start(const std::shared_ptr<conclave::FatFsDisk>& disk_handler,
		   const conclave::DiskInitialization init_type);

DRESULT disk_stop(const BYTE drive, const std::string& drive_text_id);

#endif  //  End of _DISKIO_EXT
