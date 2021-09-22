#include <cstdio>
#include <cassert>
#include <cstring>

#include "vm_enclave_layer.h"

#include "common.hpp"
#include "fatfs_ram.hpp"


static const TCHAR* dummyPath = (const TCHAR*)"";
static FATFS filesystems[FF_VOLUMES];
static unsigned char* ramBuffer[FF_VOLUMES] = { NULL };
static unsigned int numSectors[FF_VOLUMES] = { 0 };
static unsigned int driveSizes[FF_VOLUMES] = { 0 };

//  Assuming that only drive 0 is present
DSTATUS ramdisk_initialize(BYTE drive) {
    DEBUG_PRINT_FUNCTION;

    if (drive >= FF_VOLUMES) {
        return RES_PARERR;
    }

    return RES_OK;
}

DSTATUS ramdisk_status(BYTE drive) {
    DEBUG_PRINT_FUNCTION;

    if (drive >= FF_VOLUMES) {
        return RES_PARERR;
    }

    return RES_OK;
}

DRESULT ramdisk_read(BYTE drive, BYTE* buf, DWORD start, BYTE num) {
    FATFS_DEBUG_PRINT("Read - Start %d num reads %hhu \n", start, num);               

    if (drive >= FF_VOLUMES) {
        return RES_PARERR;
    }

    const unsigned char* ram_first_ptr = ramBuffer[drive];
    const unsigned int drive_size = driveSizes[drive];
    const unsigned char* read_from_ptr = ram_first_ptr + start * SECTOR_SIZE;
    const unsigned int read_size = num * SECTOR_SIZE;

    if ((uintptr_t)read_from_ptr >= (uintptr_t)ram_first_ptr &&
	(uintptr_t)read_from_ptr + (uintptr_t)read_size < (uintptr_t)ram_first_ptr + (uintptr_t)drive_size) { 
	memcpy(buf, read_from_ptr, read_size);
	return RES_OK;
    } else {
	return RES_PARERR;
    }
}

#if _READONLY == 0
DRESULT ramdisk_write(BYTE drive, const BYTE* buf, DWORD start, BYTE num) {
    FATFS_DEBUG_PRINT("Write - Start %d num writes %hhu \n", start, num);               

    if (drive >= FF_VOLUMES) {
        return RES_PARERR;
    }

    const unsigned char* ram_first_ptr = ramBuffer[drive];
    const unsigned int drive_size = driveSizes[drive];
    const unsigned char* write_to_ptr = ram_first_ptr + start * SECTOR_SIZE;
    const unsigned int write_size = num * SECTOR_SIZE;    
    
    if ((uintptr_t)write_to_ptr >= (uintptr_t)ram_first_ptr &&
	(uintptr_t)write_to_ptr + (uintptr_t)write_size < (uintptr_t)ram_first_ptr + (uintptr_t)drive_size) { 
	memcpy(write_to_ptr, buf, write_size);
	return RES_OK;
    } else {
	return RES_PARERR;
    }
    
    return RES_OK;
}
#endif

DRESULT ramdisk_ioctl(BYTE drive, BYTE cmd, void * buf) {
    DEBUG_PRINT_FUNCTION;

    if (drive >= FF_VOLUMES) {
        return RES_PARERR;
    }

    DRESULT result;

    switch (cmd) {
    case CTRL_SYNC:
	result = RES_OK;
	break;
            
    case GET_BLOCK_SIZE:
	result = RES_PARERR;
	break;

    case GET_SECTOR_SIZE:
	*(WORD *)buf = SECTOR_SIZE;
	result = RES_OK;
	break;

    case GET_SECTOR_COUNT:
	*(DWORD *)buf = numSectors[drive];
	result = RES_OK;
	break;

    default:
	result = RES_ERROR;
	break;
    }

    return result;
}


DRESULT ramdisk_start(BYTE drive, unsigned char *data, int num_bytes, int mkfs) {
    DEBUG_PRINT_FUNCTION;

    if (drive >= FF_VOLUMES) {
        return RES_PARERR;
    }

    DRESULT result;

    if (drive >= FF_VOLUMES) {
        return RES_PARERR;
    }

    if (ramBuffer[drive] != NULL) {
        return RES_PARERR;
    }

    if ((result = disk_register(drive,
				ramdisk_initialize,
				ramdisk_status,
                                ramdisk_read,
				ramdisk_write,
				ramdisk_ioctl)) != RES_OK) {
        return result;
    }

    if (mkfs) {
        memset(data, 0, num_bytes);
    }
    ramBuffer[drive] = data;
    driveSizes[drive] = num_bytes;
    numSectors[drive] = num_bytes / SECTOR_SIZE;
    MKFS_PARM parms;
    parms.fmt = FM_FAT32;
    parms.n_fat = 1;
    BYTE work[FF_MAX_SS * 2];
    
    if (mkfs) {

        if (f_mkfs(dummyPath, &parms, work, sizeof(work)) != FR_OK) {
            return RES_ERROR;
        }
    }

    if (f_mount(&(filesystems[drive]), dummyPath, 0) != FR_OK) {
        return RES_ERROR;
    }
    
    return RES_OK;
}

DRESULT ramdisk_stop(BYTE drive) {
    DEBUG_PRINT_FUNCTION;

    if (drive >= FF_VOLUMES) {
        return RES_PARERR;
    }

    DRESULT result;

    if (drive >= FF_VOLUMES) {
        return RES_PARERR;
    }

    ramBuffer[drive] = NULL;
    numSectors[drive] = 0;

    if ((result = disk_unregister(drive)) != RES_OK) {
        return result;
    }

    if (f_mount(NULL, dummyPath, 1) != FR_OK) {
        return RES_ERROR;
    }

    return RES_OK;
}
