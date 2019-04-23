#!/usr/bin/env bash
set -e

echo ${PAYARA_DAS_HOST}
echo ${PAYARA_DAS_PORT}
echo ${PAYARA_NODE_NAME}
echo ${PAYARA_INSTANCE_NAME}

# Check if instance already created before running command
if [ ! -d "payara5/glassfish/nodes/${PAYARA_NODE_NAME}/${PAYARA_INSTANCE_NAME}" ]; then
    ./payara5/bin/asadmin -H ${PAYARA_DAS_HOST} -p ${PAYARA_DAS_PORT} _create-instance-filesystem --node ${PAYARA_NODE_NAME} ${PAYARA_INSTANCE_NAME}
fi

./payara5/bin/asadmin --passwordfile ${PAYARA_PASSWORD_FILE} start-local-instance ${PAYARA_INSTANCE_NAME}
