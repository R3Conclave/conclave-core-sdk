# SGXJVM Graal
The current method to get the correct version of Graal is to check out the Oracle respository, switch to the required branch then apply a patch.

The current version we build against is 21.2.

## Building Graal
```
mkdir build
cd build
git clone --depth 1 https://github.com/oracle/graal.git -b release/graal-vm/21.2
cd graal
patch -p1 -i ../../graal.patch
```
