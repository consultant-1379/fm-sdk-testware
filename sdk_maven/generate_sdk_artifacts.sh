#!/bin/bash

CD=cd
DIRNAME=dirname
DOCKER=docker
ECHO=echo
REALPATH=realpath
RM=rm
MKDIR=mkdir

SCRIPT_DIR=$( ${CD} -- "$( ${DIRNAME} -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
SCRIPT_DIR=$(${REALPATH} ${SCRIPT_DIR})
BASE_DIR=$(${DIRNAME} ${SCRIPT_DIR})

SDK_MAVEN="sdk_maven"
BUILD_DIR="${BASE_DIR}/${SDK_MAVEN}/build"
ARTIFACT_DATA=${BASE_DIR}/ERICTAFfmsdk_CXP9042868/src/main/resources/data


usage() {
  ${ECHO} "$0 Usage: [-p|-f] [-c]"
  ${ECHO} -e "\t-p: Build PM"
  ${ECHO} -e "\t-p: Build FM"
  ${ECHO} -e "\t-v: Version"
  ${ECHO} -e "\t-c: Clean build directory"
  exit 2
}

clean(){
  if [[ -d ${BUILD_DIR} ]]; then
    ${RM} -rf ${BUILD_DIR}
  fi
}

build() {
  local _type_=${1}
  local _data_=${2}
  local _version_install_=${3}
  local _version_uninstall_=${4}
  if [[ ! -d ${BUILD_DIR} ]]; then
    ${MKDIR} -p ${BUILD_DIR}
  fi
  ${DOCKER} run --name ${SDK_MAVEN} -it --${RM} -u $(id -u)\
      -v "${HOME}/.m2":/home/${USER}/.m2 \
      -v ${ARTIFACT_DATA}:/home/${USER}/data \
      -v ${BUILD_DIR}:/home/${USER}/build \
      ${SDK_MAVEN}:latest /home/${USER}/generate_sdk_artifacts.py \
      -a ${_data_} -t ${_type_} -i ${_version_install_} -u ${_version_uninstall_}\
      -d /home/${USER}/build
}

if [[ $# -eq 0 ]]; then
  usage
fi

BUILD_FM=false
BUILD_PM=false
VERSION_INSTALL="1.0.0"
VERSION_UNINSTALL="1.0.1"
while getopts "fpchi:u:" o; do
    case "${o}" in
      f)
        BUILD_FM=true;;
      p)
        BUILD_PM=true;;
      i)
        VERSION_INSTALL="${OPTARG}";;
      u)
        VERSION_UNINSTALL="${OPTARG}";;
      c)
        clean
        exit 0;;
      *)
        usage;;
    esac
done

if ${BUILD_FM}; then
  build "FM" data/Archetypes.json ${VERSION_INSTALL} ${VERSION_UNINSTALL}
fi

if ${BUILD_PM}; then
  build "PM" data/Archetypes_PM.json ${VERSION_INSTALL} ${VERSION_UNINSTALL}
fi
