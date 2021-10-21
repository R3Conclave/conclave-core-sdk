#ifndef _FATFS_PERSISTENT_DISK
#define _FATFS_PERSISTENT_DISK

#include <vector>
#include <string>

#include "sgx_tcrypto.h"

#include "common.hpp"
#include "diskio.hpp"

#include "disk.hpp"

#define SIZE_BUFFER_WRITES 1

#define ENCRYPTION 1
#define SECTOR_SHUFFLING 1

#if ENCRYPTION
#define SECTOR_SIZE_AND_MAC (SECTOR_SIZE + SGX_AESGCM_MAC_SIZE)
#define IV_SIZE 12
#else
#define SECTOR_SIZE_AND_MAC SECTOR_SIZE
#endif


namespace conclave {

    /*
      This class provides an encrypted persistent FatFs filesystem used
      in the Enclave.
      The member functions of this class trigger OCalls to the Host, in each OCall
      we are passing streams of encrypted bytes representing filesystem "sectors".
      The Host writes such bytes into a a single file according to a path established
      when the Enclave is loaded by the Host itself.
      Encryption and sector shuffling provide further obfuscation.
    */
    class PersistentDisk : public FatFsDisk {

    private:
	std::vector<unsigned long> sectors_table_1_;
	std::vector<unsigned long> sectors_table_2_;

	sgx_aes_gcm_128bit_key_t encryption_key_ = {0};

	unsigned char buffer_encryption_[SECTOR_SIZE_AND_MAC] = {0};

	unsigned char buffer_writes_[SECTOR_SIZE_AND_MAC * SIZE_BUFFER_WRITES] = {0};

	unsigned int buffer_indices_[SIZE_BUFFER_WRITES] = {0};

	unsigned int current_sector_ = 0;

	int encrypt(const unsigned long sector_id,
		    const BYTE* input_buffer,
		    BYTE* output_buffer);

	int decrypt(const unsigned long sector_id,
		    const BYTE* input_buffer,
		    BYTE* output_buffer);

	void prepareSectorTables();

	unsigned long mapSectorId(const unsigned long sector_id);

	DRESULT flush();

    public:
	PersistentDisk(const BYTE drive,
		       const unsigned long size,
		       const unsigned char* encryption_key);

	virtual ~PersistentDisk();

	DRESULT diskRead(BYTE* output_buf,
			 DWORD start,
			 BYTE num_reads) override;

	DRESULT diskWrite(const BYTE* input_buf,
			  DWORD start,
			  BYTE num_writes) override;

	DRESULT diskIoCtl(BYTE cmd, void* buf) override;

	void diskStart() override;

	void diskStop() override;
    };
}
#endif  //  End of _FATFS_PERSISTENT_DISK
