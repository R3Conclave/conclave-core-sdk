#include <vector>

#include "vm_enclave_layer.h"

#include "disk.hpp"
#include "common.hpp"
#include "diskio.hpp"
#include "diskio_ext.hpp"

static std::vector<std::shared_ptr<conclave::FatFsDisk> > disks(FF_VOLUMES, nullptr);


DRESULT disk_register(const BYTE drive, const std::shared_ptr<conclave::FatFsDisk>& disk) {
    if (drive >= FF_VOLUMES) {
        return RES_PARERR;
    } else {
	disks[drive] = disk;
	return RES_OK;
    }
}


DRESULT disk_unregister(const BYTE drive) {

    if (drive >= FF_VOLUMES) {
        return RES_PARERR;
    } else {	
	disks.erase(disks.begin() + static_cast<int>(drive));
	return RES_OK;
    }
}


DSTATUS disk_initialize(BYTE drive) {

    if (drive >= FF_VOLUMES) {
        return RES_PARERR;
    } else {	
	return disks.at(drive)->diskInitialize();
    }
}


DSTATUS disk_status(BYTE drive) {

    if (drive >= FF_VOLUMES) {
        return RES_PARERR;
    } else {	
	return disks.at(drive)->diskStatus();
    }
}


DRESULT disk_read(BYTE drive, BYTE* buf, DWORD start, BYTE num) {

    if (drive >= FF_VOLUMES) {
        return RES_PARERR;
    } else {	
	return disks.at(drive)->diskRead(buf, start, num);
    }
}

#if _READONLY == 0
DRESULT disk_write(BYTE drive, const BYTE* buf, DWORD start, BYTE num) {

    if (drive >= FF_VOLUMES) {
        return RES_PARERR;
    } else {	
	return disks.at(drive)->diskWrite(buf, start, num);
    }
}
#endif


DRESULT disk_ioctl(BYTE drive, BYTE cmd, void* buf) {

    if (drive >= FF_VOLUMES) {
	return RES_PARERR;
    } else {
	return disks.at(drive)->diskIoCtl(cmd, buf);
    }
}


DWORD get_fattime(void) {
    /* 
       We leave this code commented out and untested as we do not have "localtime" function
       available (time.h has been replaced with a minimal conclave-time.h).
       TO DO: we need to implement localtime by ourselves and check that
       "gettimeofday" works safely (in its implementation we involve the untrusted host).
       struct timeval current_time;
       gettimeofday(&current_time, NULL);
       struct tm* stm = localtime(&current_time.tv_sec);

       return (DWORD)(stm->tm_year - 80) << 25 |
       (DWORD)(stm->tm_mon + 1) << 21 |
       (DWORD)stm->tm_mday << 16 |
       (DWORD)stm->tm_hour << 11 |
       (DWORD)stm->tm_min << 5 |
       (DWORD)stm->tm_sec >> 1;
    */
    return 1;
}

DRESULT disk_start(const std::shared_ptr<conclave::FatFsDisk>& disk_handler,
		   const conclave::DiskInitialization init_type) {
    DEBUG_PRINT_FUNCTION;
    const BYTE drive = disk_handler->getDriveId();

    if (drive >= FF_VOLUMES) {
        return RES_PARERR;
    }

    if (disk_register(drive, disk_handler) != RES_OK) {
	return RES_ERROR;
    }
    
    MKFS_PARM parms;
    parms.fmt = FM_FAT32;
    parms.n_fat = 1;
    const char* drive_text = disk_handler->getDriveTextId().c_str();
    
    if (init_type == conclave::DiskInitialization::FORMAT) {
	BYTE work[FF_MAX_SS * 2];
	FATFS_DEBUG_PRINT("MKFS drive %s\n", drive_text);

        if (f_mkfs(drive_text, &parms, work, sizeof(work)) != FR_OK) {
            return RES_ERROR;
        }
    }

    if (f_mount(disks[drive]->getFileSystem().get(), drive_text, 1) != FR_OK) {
        return RES_ERROR;
    }
    return RES_OK;
}


DRESULT disk_stop(const BYTE drive, const std::string& drive_text_id) {
    DEBUG_PRINT_FUNCTION;

    if (drive >= FF_VOLUMES) {
        return RES_PARERR;
    }
    
    if (disk_unregister(drive) != RES_OK) {
        return RES_ERROR;
    }

    if (f_mount(NULL, drive_text_id.c_str(), 1) != FR_OK) {
        return RES_ERROR;
    }
    return RES_OK;
}
