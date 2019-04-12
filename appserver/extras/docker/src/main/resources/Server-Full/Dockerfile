FROM openjdk:8u171-jdk

ENV WORK_DIR=/opt/payara
ENV PAYARA_HOME=${WORK_DIR}/payara5

# Create and set the Payara user and working directory owned by the new user
RUN mkdir ${WORK_DIR} && \
    groupadd -g 1000 payara && \
    useradd -u 1000 -M -s /bin/bash -d ${WORK_DIR} payara -g payara && \
    echo payara:payara | chpasswd && \
    chown -R payara:payara ${WORK_DIR}

USER payara
WORKDIR ${WORK_DIR}

# Install Payara Server
ARG PAYARA_ZIP
COPY ${PAYARA_ZIP} ${WORK_DIR}
RUN unzip -qq payara.zip -d ./ && \
    # Cleanup unused files
    rm -rf payara.zip/ && \
    rm -rf ${PAYARA_HOME}/glassfish/domains/domain1/ && \
    rm -rf ${PAYARA_HOME}/glassfish/domains/production

# Install entrypoint script
ARG ENTRYPOINT_SCRIPT
COPY ${ENTRYPOINT_SCRIPT} ${WORK_DIR}

ENTRYPOINT "${WORK_DIR}/entrypoint.sh"
CMD "${PAYARA_DAS_HOST}", "${PAYARA_DAS_PORT}", "${PAYARA_NODE_NAME}", "${PAYARA_INSTANCE_NAME}"