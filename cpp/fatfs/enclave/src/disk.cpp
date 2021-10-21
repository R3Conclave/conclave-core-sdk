#include "common.hpp"

#include "disk.hpp"

namespace conclave {

    FatFsDisk::FatFsDisk(const unsigned char drive, const unsigned long size) :
	drive_size_(size),
	num_sectors_(size / SECTOR_SIZE) {
    
	if (drive >= FF_VOLUMES) {
	    const std::string message = "Error, wrong drive id provided";
	    throw std::runtime_error(message);
	    
	} else {
	    drive_id_ = drive;
	}
	filesystem_ = std::make_shared<FATFS>();
    }


    FatFsDisk::~FatFsDisk() {
    }


    unsigned char FatFsDisk::getDriveId() {
	return drive_id_;
    }


    unsigned long FatFsDisk::getDriveSize() {
	return drive_size_;
    }


    unsigned long FatFsDisk::getNumSectors() {
	return num_sectors_;
    }


    std::string FatFsDisk::getDriveTextId() {
	return std::to_string(drive_id_) + ":";
    }


    std::shared_ptr<FATFS> FatFsDisk::getFileSystem() {
	return filesystem_;
    }


    DSTATUS FatFsDisk::diskInitialize() {
	return RES_OK;
    }


    DSTATUS FatFsDisk::diskStatus() {
	return RES_OK;
    }
}
