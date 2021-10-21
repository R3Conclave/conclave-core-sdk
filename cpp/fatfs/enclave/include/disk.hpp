#ifndef _FATFS_DISK
#define _FATFS_DISK

#include <string>
#include <memory>

#include "diskio.hpp"

namespace conclave {

    enum DiskInitialization { FORMAT, OPEN, ERROR };

    /*
      Abstract class of the disk handler used in the Enclave as a bridge between
      FatFsFileManager (where the Posix calls are re-implemented) and the
      FatFs drives (in-memory or persistent).
    */
    class FatFsDisk {
    
    private:
	unsigned char drive_id_;
    
	unsigned long drive_size_;

	unsigned long num_sectors_;    
    
	std::string drive_text_id_;

	std::shared_ptr<FATFS> filesystem_;

    public:
	FatFsDisk(const unsigned char drive_id, const unsigned long size);
    
	virtual ~FatFsDisk();
    
	unsigned char getDriveId();
    
	unsigned long getDriveSize();

	unsigned long getNumSectors();

	std::string getDriveTextId();

	std::shared_ptr<FATFS> getFileSystem();

	virtual void diskStart() = 0;
    
	virtual void diskStop() = 0;    

	// Here are the 5 FatFs calls to register
	DSTATUS diskInitialize();

	DSTATUS diskStatus();
    
	virtual DRESULT diskRead(BYTE* input_buffer,
				 DWORD start,
				 BYTE num_reads) = 0;

	virtual DRESULT diskWrite(const BYTE* content_buf,
				  DWORD start,
				  BYTE num_writes) = 0;

	virtual DRESULT diskIoCtl(BYTE cmd, void * buf)  = 0;
    };
}
#endif  //  End of  _FATFS_DISK
