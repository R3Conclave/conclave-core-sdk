#include "vm_enclave_layer.h"
#include "common.hpp"

#include "inmemory_disk.hpp"

namespace conclave {

    InMemoryDisk::InMemoryDisk(const BYTE drive_id, const unsigned long size) :
        FatFsDisk(drive_id, size) {
    }


    InMemoryDisk::~InMemoryDisk() {
        diskStop();
    }


    DRESULT InMemoryDisk::diskRead(BYTE* output_buf, DWORD start, BYTE num_reads) {
        FATFS_DEBUG_PRINT("Read - Start %d num_reads %hhu \n", start, num_reads);               
        const unsigned char* ram_first_ptr = ram_buffer_;
        const unsigned char* read_from_ptr = ram_first_ptr + start * SECTOR_SIZE;
        const unsigned long read_size = num_reads * SECTOR_SIZE;

        if ((uintptr_t)read_from_ptr >= (uintptr_t)ram_first_ptr &&
            (uintptr_t)read_from_ptr + (uintptr_t)read_size < (uintptr_t)ram_first_ptr + (uintptr_t)getDriveSize()) { 
            memcpy(output_buf, read_from_ptr, read_size);
            return RES_OK;
        } else {
            return RES_PARERR;
        }
    }


    DRESULT InMemoryDisk::diskWrite(const BYTE* input_buf, DWORD start, BYTE num) {
        FATFS_DEBUG_PRINT("Write - Start %d num writes %hhu \n", start, num);               
        unsigned char* ram_first_ptr = ram_buffer_;
        unsigned char* write_to_ptr = ram_first_ptr + start * SECTOR_SIZE;
        const unsigned long write_size = num * SECTOR_SIZE;    
    
        if ((uintptr_t)write_to_ptr >= (uintptr_t)ram_first_ptr &&

            (uintptr_t)write_to_ptr + (uintptr_t)write_size < (uintptr_t)ram_first_ptr + (uintptr_t)getDriveSize()) { 
            memcpy(write_to_ptr, input_buf, write_size);
            return RES_OK;
        } else {
            return RES_PARERR;
        }
    }


    DRESULT InMemoryDisk::diskIoCtl(BYTE cmd, void* buf) {
        DRESULT result;

        switch (cmd) {
        case CTRL_SYNC:
            result = RES_OK;
            break;
            
        case GET_BLOCK_SIZE:
            result = RES_PARERR;
            break;

        case GET_SECTOR_SIZE:
            *(WORD*)buf = SECTOR_SIZE;
            result = RES_OK;
            break;

        case GET_SECTOR_COUNT:
            *(LBA_t*)buf = getNumSectors();
            result = RES_OK;
            break;

        default:
            result = RES_ERROR;
            break;
        }
        return result;
    }


    void InMemoryDisk::diskStart() {
        DEBUG_PRINT_FUNCTION;

        ram_buffer_ = (unsigned char*)calloc(getDriveSize(), sizeof(unsigned char));
    
        if (ram_buffer_ == NULL) {
            const std::string message("Could not allocate memory for the RAM disk in the Enclave");
            throw std::runtime_error(message);
        }
    }


    void InMemoryDisk::diskStop() {
        DEBUG_PRINT_FUNCTION;

        if (ram_buffer_ != NULL) {
            free(ram_buffer_);
            ram_buffer_ = NULL;
        }
    }
}
