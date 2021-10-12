#pragma once
#include <string>

bool checkEnclaveSupport(bool requireHardwareSupport, std::string& message);
bool enableHardwareEnclaveSupport(std::string& message);
