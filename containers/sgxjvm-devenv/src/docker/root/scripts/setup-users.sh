#!/bin/bash
set -xeuo pipefail

user_ids=1000

echo "Setting up user accounts..."
for i in $user_ids; do
    groupadd -g $i host_group_$i >& /dev/null
    useradd -u $i -g $i host_user_$i >& /dev/null
    adduser host_user_$i sudo >& /dev/null
done
