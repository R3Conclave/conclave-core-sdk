#include <stdio.h>
#include <string.h>
#include <iostream>
#include "diskio.hpp"

static diskio_fxns drive_fxn_table[FF_VOLUMES] = {
						  {NULL, NULL, NULL, NULL, NULL},
};

DRESULT disk_register(BYTE drive,
		      DSTATUS (*d_init) (BYTE drive),
		      DSTATUS (*d_status) (BYTE drive),
		      DRESULT (*d_read) (BYTE drive, BYTE *buf, DWORD start, BYTE num),
		      DRESULT (*d_write) (BYTE drive, const BYTE *buf, DWORD start, BYTE num),
		      DRESULT (*d_ioctl) (BYTE drive, BYTE cmd, void * buf)) {

    if (drive >= FF_VOLUMES) {
        return RES_PARERR;
    } else {
	drive_fxn_table[drive].d_init = d_init;
	drive_fxn_table[drive].d_status = d_status;
	drive_fxn_table[drive].d_read = d_read;
	drive_fxn_table[drive].d_write = d_write;
	drive_fxn_table[drive].d_ioctl = d_ioctl;
	return RES_OK;
    }
}

DRESULT disk_unregister(BYTE drive) {

    if (drive >= FF_VOLUMES) {
        return RES_PARERR;
    } else {
	drive_fxn_table[drive].d_init = NULL;
	drive_fxn_table[drive].d_status = NULL;
	drive_fxn_table[drive].d_read = NULL;
	drive_fxn_table[drive].d_write = NULL;
	drive_fxn_table[drive].d_ioctl = NULL;
	return RES_OK;
    }
}

DSTATUS disk_initialize(BYTE drive) {

    if (drive >= FF_VOLUMES || drive_fxn_table[drive].d_init == NULL) {
        return RES_PARERR;
    } else {
	return ((*(drive_fxn_table[drive].d_init)) (drive));
    }
}

DSTATUS disk_status(BYTE drive) {

    if (drive >= FF_VOLUMES || drive_fxn_table[drive].d_status == NULL) {
        return RES_PARERR;
    } else {	
	return ((*(drive_fxn_table[drive].d_status)) (drive));
    }
}

DRESULT disk_read(BYTE drive, BYTE * buf, DWORD start, BYTE num) {

    if (drive >= FF_VOLUMES || drive_fxn_table[drive].d_read == NULL) {
        return RES_PARERR;
    } else {
	return ((*(drive_fxn_table[drive].d_read)) (drive, buf, start, num));
    }
}

#if _READONLY == 0
DRESULT disk_write(BYTE drive, const BYTE * buf, DWORD start, BYTE num) {

    if (drive >= FF_VOLUMES || drive_fxn_table[drive].d_write == NULL) {
	return RES_PARERR;
    } else {
	return ((*(drive_fxn_table[drive].d_write)) (drive, buf, start, num));
    }
}
#endif

DRESULT disk_ioctl(BYTE drive, BYTE cmd, void * buf) {

    if (drive >= FF_VOLUMES || drive_fxn_table[drive].d_ioctl == NULL) {
	return RES_PARERR;
    } else {
	return ((*(drive_fxn_table[drive].d_ioctl)) (drive, cmd, buf));
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
