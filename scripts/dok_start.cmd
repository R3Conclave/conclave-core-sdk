@echo off

if exist .\dok_start.cmd (
  echo can't run from here. use .\scripts\dok_start.cmd
  exit /b
)

docker ps -aqf name=sgxjvm >%TEMP%\.id.txt
set /p CONTAINER_ID=<%TEMP%\.id.txt

if not .%CONTAINER_ID%==. goto running

if exist "%USERPROFILE%\.oblivium_credentials.cmd" call "%USERPROFILE%\.oblivium_credentials.cmd" 

docker login %OBLIVIUM_CONTAINER_REGISTRY_URL% -u %OBLIVIUM_CONTAINER_REGISTRY_USERNAME% -p "%OBLIVIUM_CONTAINER_REGISTRY_PASSWORD%"
docker pull %OBLIVIUM_CONTAINER_REGISTRY_URL%/com.r3.sgx/sgxjvm-devenv


rem get current working directory(same as 'pwd' on Linux/Unix)
rem convert Windows path to mount point: c:\some\folder -> //c/some/folder
set HOST_CODE_DIR=%CD%
set HOST_CODE_DIR=%HOST_CODE_DIR:\=/%
set HOST_CODE_DIR=//%HOST_CODE_DIR::=%


docker run --name sgxjvm --rm -d --ulimit core=256000000 -p 8000:8000 -p 8001:8001 -ti -v "%HOST_CODE_DIR%":/work -w /work sgxjvm-docker-nightly.software.r3.com/com.r3.sgx/sgxjvm-devenv bash
docker exec -d sgxjvm bash ./scripts/serve-docsites.sh

:running

