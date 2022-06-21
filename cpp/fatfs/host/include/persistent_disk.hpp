#ifndef _FATFS_DISK
#define _FATFS_DISK

#include <jni.h>

extern "C" {
    JNIEXPORT void JNICALL Java_com_r3_conclave_host_internal_fatfs_FileSystemHandler_setup(JNIEnv* input_env,
                                                                                            jobject input_obj);

    JNIEXPORT void JNICALL Java_com_r3_conclave_host_internal_fatfs_FileSystemHandler_cleanup(JNIEnv* input_env,
                                                                                              jobject input_obj);

}

long host_disk_get_size(const unsigned char drive, const unsigned long persistent_size);

int host_disk_start(const unsigned char drive);

int host_disk_initialize(const unsigned char drive);

int host_disk_read(const unsigned char drive,
                   const unsigned long sector_id,
                   const unsigned char num_sectors,
                   const unsigned int sector_size,
                   unsigned char* buf);

int host_disk_write(const unsigned char drive,
                    const unsigned char* buf,
                    const unsigned int sector_size,
                    const unsigned long sector);

#endif
