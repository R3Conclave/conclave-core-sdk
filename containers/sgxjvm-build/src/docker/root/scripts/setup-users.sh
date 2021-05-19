#!/bin/bash
set -xeuo pipefail

user_ids="1000 1007"

echo "Setting up user accounts..."
for i in $user_ids; do
    groupadd -g $i host_group_$i
    useradd -m -u $i -g $i host_user_$i
    adduser host_user_$i sudo
done
