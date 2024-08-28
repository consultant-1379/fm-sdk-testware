FROM maven:3.6.3-jdk-8

ARG UNAME=sdk
ARG UID=1000
ARG GID=1000
ARG USER_HOME_DIR=/home/$UNAME

ENV MAVEN_CONFIG=/home/$UNAME/.m2

RUN apt-get update && apt-get install -y python3 rpm && \
    groupadd -g $GID -o $UNAME && \
    useradd -m -u $UID -g $GID -o -s /bin/bash $UNAME

COPY --chown=$UNAME:$UNAME sdk_maven/generate_sdk_artifacts.py /home/$UNAME/

RUN chmod +x /home/$UNAME/generate_sdk_artifacts.py
USER $UNAME
