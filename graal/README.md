# SGXJVM Graal
The current method to get the correct version of Graal is to checkout the Oracle respostory, switch to the required branch then apply a patch.

The current version we build against is 0.21.

## Building Graal
```
mkdir build
cd build
git clone --depth 1 https://github.com/oracle/graal.git -b release/graal-vm/20.2
cd graal
patch -p1 -i ../../graal_20.2.patch
```
