#!/usr/bin/env bash
set -e

# Check if instance already created before running command
if [ ! -d "payara5/glassfish/nodes/${3}/${4}" ]; then
    ./payara5/bin/asadmin -H ${1} -p ${2} _create-instance-filesystem --node ${3} ${4}
fi

./payara5/bin/asadmin start-local-instance --verbose ${4}

