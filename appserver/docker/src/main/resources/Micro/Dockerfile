FROM openjdk:8-jdk-alpine

# TO-DO: Expose based on portbase AND config
EXPOSE 4848 9009 8080 8181

# Create and set the Payara user and working directory owned by the new user
RUN addgroup payara && \
    adduser -D -h ${PAYARA_HOME} -H -s /bin/bash payara -G payara && \
    echo payara:payara | chpasswd && \
    mkdir -p ${PAYARA_HOME}/deployments && \
    chown -R payara:payara ${PAYARA_HOME} && \

USER payara
WORKDIR ${PAYARA_HOME}

# Default command to run
ENTRYPOINT ["java", "-jar", "payara-micro.jar"]
CMD ["--deploymentDir", "/opt/payara/deployments"]

# Download specific
ARG PAYARA_VERSION="5.191"
ENV PAYARA_VERSION="$PAYARA_VERSION"
RUN wget --no-verbose -O ${PAYARA_HOME}/payara-micro.jar http://central.maven.org/maven2/fish/payara/extras/payara-micro/${PAYARA_VERSION}/payara-micro-${PAYARA_VERSION}.jar