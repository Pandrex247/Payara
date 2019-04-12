FROM openjdk:8u171-jdk

# The following environment variables are required
# PAYARA_HOME - Where Payara will be installed
# PAYARA_NODE_DIR - Where the nodes of Payara will be stored
# ASADMIN_LISTENER_PORT - Listener port for admin commands
# HTTP_LISTENER_PORT - Listener port for HTTP connections
# HTTP_SSL_LISTENER_PORT - Listener port for HTTPS connections
# IIOP_LISTENER_PORT - Listener port for IIOP connections
# IIOP_SSL_MUTUALAUTH_PORT - Listener port for IIOP SSL Mutual Auth connections
# IIOP_SSL_LISTENER_PORT - Listener port for IIOP SSL connections
# JMS_PROVIDER_PORT - Listener port for JMS connections
# JMX_SYSTEM_CONNECTOR_PORT - Listener port for JMX connections
# OSGI_SHELL_TELNET_PORT - Listener port for OSGI connections
# JAVA_DEBUGGER_PORT - Listener port for Java Debugger connections

# Payara ports to expose
EXPOSE ${ASADMIN_LISTENER_PORT} ${HTTP_LISTENER_PORT} ${HTTP_SSL_LISTENER_PORT} \
    ${IIOP_LISTENER_PORT} ${IIOP_SSL_MUTUALAUTH_PORT} ${IIOP_SSL_LISTENER_PORT} ${JMS_PROVIDER_PORT} \
    ${JMX_SYSTEM_CONNECTOR_PORT} ${OSGI_SHELL_TELNET_PORT} ${JAVA_DEBUGGER_PORT}

# Payara version
ARG PAYARA_VERSION=5.191
ARG PAYARA_PKG=https://search.maven.org/remotecontent?filepath=fish/payara/distributions/payara/${PAYARA_VERSION}/payara-${PAYARA_VERSION}.zip
ARG PAYARA_SHA1=55d2f40559a4e9a9baa93756213be1488f203f84
ARG TINI_VERSION=v0.18.0

# DAS and instance details
ARG PAYARA_DAS_HOST
ARG PAYARA_DAS_PORT
ARG PAYARA_INSTANCE_NAME
ENV PAYARA_DAS_HOST=$PAYARA_DAS_HOST \
    PAYARA_DAS_PORT=$PAYARA_DAS_PORT \
    PAYARA_INSTANCE_NAME=$PAYARA_INSTANCE_NAME

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

# Download and unzip the Payara distribution
RUN wget --no-verbose -O payara.zip ${PAYARA_PKG} && \
    echo "${PAYARA_SHA1} *payara.zip" | sha1sum -c - && \
    unzip -qq payara.zip -d ./ && \
    # Cleanup unused files
    rm -rf payara.zip/ && \
    rm -rf ${PAYARA_HOME}/glassfish/domains/domain1/ && \
    rm -rf ${PAYARA_HOME}/glassfish/domains/production

RUN ${PAYARA_HOME}/bin/asadmin -H ${PAYARA_DAS_HOST} -p ${PAYARA_DAS_PORT} "create-local-instance" ${PAYARA_INSTANCE_NAME}

ENTRYPOINT ["${PAYARA_HOME}/bin/asadmin", "start-local-instance", "--verbose", "${PAYARA_INSTANCE_NAME}"]