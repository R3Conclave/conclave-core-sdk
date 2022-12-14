#include <cmath>
#include <random>
#include <vector>
#include <algorithm>

#include "vm_enclave_layer.h"
#include "common.hpp"

#include "persistent_disk.hpp"

namespace conclave {

    void getHashFromKey(const char* derivation_text,
                        const unsigned char* key,
                        const unsigned int key_size,
                        sgx_sha256_hash_t* p_hash) {

        const size_t derivation_text_size = strlen(derivation_text);
        sgx_sha_state_handle_t hash_context = {0};
        
        if (sgx_sha256_init(&hash_context) != SGX_SUCCESS ||
            sgx_sha256_update((const uint8_t *)derivation_text, derivation_text_size, hash_context) != SGX_SUCCESS ||
            sgx_sha256_update((const uint8_t *)key, key_size, hash_context) != SGX_SUCCESS ||
            sgx_sha256_get_hash(hash_context, p_hash) != SGX_SUCCESS ||
            sgx_sha256_close(hash_context) != SGX_SUCCESS) {
            const std::string message = "Error in deriving persistence key";
            throw std::runtime_error(message);
        }
    };
    
    
    PersistentDisk::PersistentDisk(const BYTE drive,
                                   const unsigned long size,
                                   const unsigned char* encryption_key) :
        FatFsDisk(drive, size) {

        sgx_sha256_hash_t hash_encryption_key;
        getHashFromKey("R3 persistent filesystem I",
                       encryption_key,
                       sizeof(sgx_aes_gcm_128bit_key_t),
                       &hash_encryption_key);
        memcpy(&encryption_key_, &hash_encryption_key, sizeof(sgx_aes_gcm_128bit_key_t));
    };


    PersistentDisk::~PersistentDisk() {
    }


#if ENCRYPTION
    int PersistentDisk::encrypt(const unsigned long sector_id,
                                const BYTE* input_buf,
                                BYTE* output_buf) {
        //  Here we assume that IV_SIZE is bigger than the size of sector_id
        uint8_t iv[IV_SIZE] = {0};
        memcpy(iv, &sector_id, sizeof(unsigned long));
        const sgx_status_t res = sgx_rijndael128GCM_encrypt(&encryption_key_,
                                                            input_buf,
                                                            SECTOR_SIZE,
                                                            output_buf,
                                                            iv,
                                                            IV_SIZE,
                                                            NULL,
                                                            0,
                                                            (sgx_aes_gcm_128bit_tag_t*) (output_buf + SECTOR_SIZE));
        if (res != SGX_SUCCESS) {
            FATFS_DEBUG_PRINT("Error: could not encrypt to the filesystem, error code: %d\n", res);
            return -1;
        }
        return 0;
    }


    int PersistentDisk::decrypt(const unsigned long sector_id,
                                const BYTE* input_buf,
                                BYTE* output_buf) {
        //  Here we assume that IV_SIZE is bigger than the size of sector_id
        uint8_t iv[IV_SIZE] = {0};
        memcpy(iv, &sector_id, sizeof(unsigned long));
        const sgx_status_t res = sgx_rijndael128GCM_decrypt(&encryption_key_,
                                                            input_buf,
                                                            SECTOR_SIZE,
                                                            output_buf,
                                                            iv,
                                                            IV_SIZE,
                                                            NULL,
                                                            0,
                                                            (sgx_aes_gcm_128bit_tag_t*) (input_buf + SECTOR_SIZE));
        if (res != SGX_SUCCESS) {
            FATFS_DEBUG_PRINT("Error: could not decrypt from the filesystem, error code: %d\n", res);
            return -1;
        }
        return 0;
    }

#endif  //  End of ENCRYPTION

#if SECTOR_SHUFFLING
    void PersistentDisk::prepareSectorTables() {
        const unsigned long num_sectors = getNumSectors();
        const unsigned long square_root = std::ceil(std::sqrt(num_sectors));

        //  The number of sectors can't be bigger than 2^32 - 1 (see GET_SECTOR_COUNT in diskIoCtl below)
        //  Hence the sector tables can just be of LBA_t type (currently "unsigned int").
        //  This is to save memory when the tables are big.
        const LBA_t size_table_1 = square_root - (square_root % SECTOR_SIZE) + SECTOR_SIZE;
        //  We do not want this value to be zero in case the number of sectors is very small
        const LBA_t size_table_2 = std::max(num_sectors / size_table_1, 1ul);
        sectors_table_1_.reserve(size_table_1);
        sectors_table_2_.reserve(size_table_2);

        unsigned long seed = 0;
        sgx_sha256_hash_t hash_seed;

        getHashFromKey("R3 persistent filesystem II",
                       encryption_key_,
                       sizeof(sgx_aes_gcm_128bit_key_t),
                       &hash_seed);
        memcpy(&seed, &hash_seed, sizeof(unsigned long));

        for (LBA_t i = 0; i < size_table_1; ++i) {
            sectors_table_1_.push_back(i);
        }

        for (LBA_t i = 0; i < size_table_2; ++i) {
            sectors_table_2_.push_back(i);
        }   
        std::shuffle(sectors_table_1_.begin(), sectors_table_1_.end(), std::default_random_engine(seed));
        std::shuffle(sectors_table_2_.begin(), sectors_table_2_.end(), std::default_random_engine(seed));
    }


    unsigned long PersistentDisk::mapSectorId(const unsigned long sector_id) {
        const unsigned long size_table_2 = sectors_table_2_.size();
        const unsigned long bucket_id = sector_id / size_table_2;
        const unsigned long offset_id = sector_id % size_table_2;
        const unsigned long mapped_sector_id = sectors_table_1_[bucket_id] * size_table_2 + sectors_table_2_[offset_id];
        return mapped_sector_id;
    }
#endif  //  End of SECTOR_SHUFFLING
                               

    DRESULT PersistentDisk::flush() {
        int res = -1;
        const unsigned int sector_size = SECTOR_SIZE_AND_MAC;
        host_encrypted_write_ocall(&res,
                                   getDriveId(),
                                   buffer_writes_,
                                   sector_size,
                                   buffer_index_);
        if (res < 0) {
            return RES_ERROR;
        }
        return RES_OK;
    }


    DRESULT PersistentDisk::diskRead(BYTE* output_buf,
                                     LBA_t sector,
                                     BYTE num_reads) {
        DRESULT res_flush = flush();

        if (res_flush != RES_OK) {
            return RES_ERROR;
        }
        int res = 0;
    
        BYTE* p_output_buf = output_buf;
        int i = 0;
    
        while (i < num_reads) {
#if SECTOR_SHUFFLING
            const unsigned long sector_id = mapSectorId(sector + i);
#else
            const unsigned long sector_id = sector + i;
#endif

#if ENCRYPTION
            host_encrypted_read_ocall(&res,
                                      getDriveId(),
                                      sector_id,
                                      1,
                                      SECTOR_SIZE_AND_MAC,
                                      buffer_encryption_,
                                      SECTOR_SIZE_AND_MAC);
            if (res < 0) {
                FATFS_DEBUG_PRINT("Read failed, result: %d\n", res);
                return RES_ERROR;
            }
            const int res_decrypt = decrypt(sector_id, buffer_encryption_, p_output_buf);

            if (res_decrypt != 0) {
                return RES_ERROR;
            }
#else
            host_encrypted_read_ocall(&res,
                                      getDriveId(),
                                      sector_id,
                                      1,
                                      SECTOR_SIZE_AND_MAC,
                                      p_output_buf,
                                      SECTOR_SIZE_AND_MAC);
            if (res < 0) {
                FATFS_DEBUG_PRINT("Read failed, result: %d\n", res);
                return RES_ERROR;
            }
#endif

            p_output_buf += SECTOR_SIZE;
            i++;
        }
        if (res == -1) {
            return RES_ERROR;
        } else {
            return RES_OK;
        }
    }

#if _READONLY == 0
    DRESULT PersistentDisk::diskWrite(const BYTE* input_buf,
                                      LBA_t sector,
                                      BYTE num_writes) {
        const BYTE* p_input_buf = input_buf;
        unsigned int i_num = 0;

        unsigned char* p_output_buf = buffer_writes_;

        while (i_num < num_writes) {
#if SECTOR_SHUFFLING
            const unsigned long sector_id = mapSectorId(sector + i_num);
#else
            const unsigned long sector_id = sector + i_num;
#endif
            buffer_index_ = sector_id;
            
#if ENCRYPTION
            const int res_encrypt = encrypt(sector_id, p_input_buf, p_output_buf);

            if (res_encrypt == -1) {
                return RES_ERROR;
            }
#else
            memcpy(p_output_buf, p_input_buf, SECTOR_SIZE);
#endif
            p_input_buf += SECTOR_SIZE;
        
            DRESULT res = flush();
    
            if (res != RES_OK) {
                return RES_ERROR;
            }
            i_num ++;
        }
        return RES_OK;
    }
#endif


    DRESULT PersistentDisk::diskIoCtl(BYTE cmd, void* buf) {
        DRESULT result;

        switch (cmd) {
        case CTRL_SYNC:
            result = flush();
            break;
            
        case GET_BLOCK_SIZE:
            result = RES_PARERR;
            break;

        case GET_SECTOR_SIZE:
            *((WORD*)buf) = SECTOR_SIZE;
            result = RES_OK;
            break;

        case GET_SECTOR_COUNT:
            //  As LBA_t is currently a 4 bytes value, getNumSectors should not return a value bigger than
            //    2 ^ 32 - 1. This is currently always the case as we throw an exception in
            //    case the related filesystem size requires a bigger number of sectors.
            //  See the const kMaxInMemorySize, kMaxPersistentSize and the JNI setupFileSystems function in api.cpp
            *((LBA_t*)buf) = getNumSectors();
            result = RES_OK;
            break;

        default:
            result = RES_ERROR;
            break;
        }
        return result;
    }


    void PersistentDisk::diskStart() {
        DEBUG_PRINT_FUNCTION;
    
#if SECTOR_SHUFFLING
        prepareSectorTables();
#endif
    }


    void PersistentDisk::diskStop() {
        DEBUG_PRINT_FUNCTION;

#if SECTOR_SHUFFLING
        sectors_table_1_.clear();
        sectors_table_2_.clear();
#endif
    }
}
