#ifndef FATFS_COMMON
#define FATFS_COMMON

#define SECTOR_SIZE 512

#define FATFS_DEBUG 0

#if FATFS_DEBUG
#define __FILENAME__ (strrchr(__FILE__, '/') ? strrchr(__FILE__, '/') + 1 : __FILE__)
#define DEBUG_PRINT_FUNCTION printf("%s %s\n", __FILENAME__, __PRETTY_FUNCTION__ )
#define FATFS_DEBUG_PRINT(fmt, ...) \
    do { if (FATFS_DEBUG) fprintf(stderr, "%s:%d:%s(): " fmt, __FILENAME__, \
                                __LINE__, __func__, __VA_ARGS__); } while (0)
#else
#define DEBUG_PRINT_FUNCTION
#define FATFS_DEBUG_PRINT(fmt, ...)
#endif  //    End of FATFS_DEBUG


#endif  //    End of FATFS_COMMON
