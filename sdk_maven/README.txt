To build the image:
    docker image build . --file=sdk_maven/Dockerfile \
        --build-arg UID=$(id -u) \
        --build-arg GID=$(id -g) \
        --build-arg UNAME=${USER} -t sdk_maven:latest

To generate the maven SDK packages use the 'generate_sdk_artifacts.sh' script:
    sdk_maven/generate_sdk_artifacts.sh Usage: [-p|-f] <-v version>
            -p: Build PM
            -f: Build FM
            -v: Artifact version

Packages to use for an install are in:
    sdk_maven/build/[TYPE]/[VERSION]/install/{jboss,models}
And for uninstall, model packages are in:
    sdk_maven/build/[TYPE]/[VERSION]/uninstall/models

Data used to generate the SDK maven archetype packages is in (same data used by the TAF tests):
    FM -> ERICTAFfmsdk_CXP9042868/src/main/resources/data/Archetypes.json
    PM -> ERICTAFfmsdk_CXP9042868/src/main/resources/data/Archetypes_PM.json