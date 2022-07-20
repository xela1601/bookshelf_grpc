FROM ubuntu:20.04
ARG VERSION
ENV VERSION=$VERSION
ARG JDK_VERSION
ENV JDK_VERSION=$JDK_VERSION
ARG GRPC_PORT
ENV GRPC_PORT=$GRPC_PORT
WORKDIR /grpc
RUN apt-get update &&  \
    apt-get install -y openjdk-${JDK_VERSION}-jdk &&  \
    rm -rf /var/lib/apt/lists/*

COPY target/bookservice_grpc-${VERSION}-jar-with-dependencies.jar ./bookservice_grpc.jar
ENV JAVA_OPTS -Xmx256m
EXPOSE $GRPC_PORT/tcp
CMD ["java", "-jar", "bookservice_grpc.jar"]

