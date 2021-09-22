#ifndef _FATFS_RAM
#define _FATFS_RAM

#include "diskio.hpp"

DRESULT ramdisk_start(BYTE drive, unsigned char *data, int numBytes, int mkfs);

DRESULT ramdisk_stop(BYTE drive);

#endif
