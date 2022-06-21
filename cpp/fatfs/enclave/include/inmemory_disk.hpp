#ifndef _INMEMORY_DISK
#define _INMEMORY_DISK

#include "diskio.hpp"

#include "disk.hpp"

namespace conclave {
    /*
      This class provides an in-memory volatile FatFs filesystem used
      in the Enclave.
      The member functions of this class are reading/writing streams of
      encrypted bytes representing filesystem "sectors" (FatFs terminology).
      The actual file storage consists in an unsigned char buffer
      which is initialized and resident only in the Enclave.
    */
    class InMemoryDisk : public FatFsDisk {

    private:
        unsigned char* ram_buffer_ = NULL;
   
    public:
        InMemoryDisk(const BYTE drive_id, const unsigned long size);

        virtual ~InMemoryDisk();

        DRESULT diskRead(BYTE* output_buffer, LBA_t sector, BYTE num_reads) override;

        DRESULT diskWrite(const BYTE* content_buf, LBA_t sector, BYTE num_writes) override;

        DRESULT diskIoCtl(BYTE cmd, void * buf) override;

        void diskStart() override;

        void diskStop() override;    
    };
}
#endif  //  End of _INMEMORY_DISK
