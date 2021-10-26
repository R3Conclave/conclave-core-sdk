#ifndef _FATFS_RESULT
#define _FATFS_RESULT

enum class FatFsResult {
                        OK = 0,                       // 0 Succeeded
                        ERROR,                        // 1 General error
                        MOUNT_FAILED,                 // 2 Mount of a FatFs drive has failed
                        UMOUNT_FAILED,                // 3 Umount of a FatFs drive has failed
                        MKFS_GENERIC_ERROR,           // 4 FatFs mkfs failed with an error
                        MKFS_ABORTED,                 // 5 FatFs mkfs failed with a specific FR_MKFS_ABORTED result
                        WRONG_DRIVE_ID,               // 6 A wrong drive id has been passed in
                        DRIVE_REGISTRATION_FAILED ,   // 7 Failure while registering a drive handler
                        DRIVE_UNREGISTRATION_FAILED,  // 8 Failure while unregistering a drive handler
                        ROOT_DIRECTORY_MOUNT_FAILED   // 9 Failure while mounting the root path into the just started FatFs filesystem
};

#endif
