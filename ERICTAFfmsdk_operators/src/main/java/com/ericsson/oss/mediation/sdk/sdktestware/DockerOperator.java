package com.ericsson.oss.mediation.sdk.sdktestware;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DockerOperator {
    private static final String DOCKER_REGISTRY_IMAGE = "registry:2";
    private static final String DOCKER_REGISTRY_NAME = "SDK_BUILD_REGISTRY";
    private static final int DOCKER_REGISTRY_PORT = 5000;
    private final static Logger LOGGER = LoggerFactory.getLogger(DockerOperator.class);

    private static String getLocalRegistryPrefix() {
        return "localhost:" + DOCKER_REGISTRY_PORT;
    }

    public static String getRegistryPrefix() {
        if (Flags.useLocalDockerRegistry()) {
            return getLocalRegistryPrefix();
        } else {
            return Flags.repositoryUrl();
        }
    }

    public static String getRegistryPrefixHost() {
        return getRegistryPrefix().split("/")[0];
    }

    public static void version() throws OperatorException {
        final LocalProcessOperator process = new LocalProcessOperator();
        process.execute(Arrays.asList("docker", "version"), true);
    }

    public Map<String, String> loadImages(final Path sdkBuildManager, final Path dockerTar, final Path imagesTxt) throws OperatorException {
        if (ArchiveOperator.isTarEmpty(dockerTar)) {
            LOGGER.info("docker.tar is empty, assuming test of light build, loading images from CI");
            return useImagesFromText(imagesTxt);
        } else {
            LOGGER.info("Loading images from docker.tar via sdkBuildManager");
            return loadImagesFromTar(sdkBuildManager, imagesTxt);
        }
    }

    private Map<String, String> loadImagesFromTar(final Path sdkBuildManager, final Path imagesTxt) throws OperatorException {
        if (!Flags.skipSdkManagerCsarLoadImages()) {
            final List<String> command = Arrays.asList(sdkBuildManager.toString(),
                    "--load-csar-images", "--repository-url", getRegistryPrefix(), "-i", imagesTxt.toString());
            final LocalProcessOperator process = new LocalProcessOperator();

            process.execute(Arrays.asList("chmod", "+x", sdkBuildManager.toString()), false);
            process.execute(command, false, sdkBuildManager.getParent());
        } else {
            LOGGER.info("Skipping loading images from " + imagesTxt + ", skip flag set.");
        }

        try {
            final List<String> images = FileUtils.readLines(imagesTxt.toFile());
            final Map<String, String> tagMappings = new HashMap<>();
            for (String image : images) {
                final String devTag = getRetaggedTag(image);
                tagMappings.put(image, devTag);
            }
            return tagMappings;
        } catch (IOException e) {
            throw new OperatorException("Could not load " + imagesTxt, e);
        }
    }

    private Map<String, String> useImagesFromText(final Path imagesTxt) throws OperatorException {
        final List<String> images;
        try {
            images = FileUtils.readLines(imagesTxt.toFile());
        } catch (IOException e) {
            throw new OperatorException("Could not load " + imagesTxt, e);
        }
        final Map<String, String> tagMappings = new HashMap<>();
        for (String image : images) {
            tagMappings.put(image, image);
        }
        return tagMappings;
    }

    public String getRetaggedTag(final String imageTag) {
        final String[] imageTagParts = imageTag.split("/", 2);
        final String[] imageTagPathParts = imageTagParts[1].split("/");

        final String imageTagHost = imageTagParts[0];
        final String imageTagPath = String.join("/", Arrays.copyOfRange(imageTagPathParts, 0, imageTagPathParts.length - 1));
        final String imageTagName = imageTagPathParts[imageTagPathParts.length - 1];

        final String repoUrl = Flags.repositoryUrl();
        final String[] repoUrlTagParts = repoUrl.split("/", 2);

        final String repoUrlTagHost = repoUrlTagParts[0];
        final String repoUrlTagPath;
        if (repoUrlTagParts.length > 1) {
            repoUrlTagPath = repoUrlTagParts[1];
        } else {
            repoUrlTagPath = imageTagPath;
        }

        String retagged = imageTag;
        if (!repoUrlTagHost.equals(imageTagHost) || !imageTagPath.equals(repoUrlTagPath)) {
            retagged = repoUrlTagHost + "/" + repoUrlTagPath + "/" + imageTagName;
        }

        return retagged;
    }

    public boolean exists(final String image) {
        final List<String> command = Arrays.asList("docker", "image", "inspect", image);
        final LocalProcessOperator process = new LocalProcessOperator();
        try {
            process.execute(command, true);
            return true;
        } catch (OperatorException e) {
            return false;
        }
    }

    void pull(final String image) throws OperatorException {
        final List<String> command = Arrays.asList("docker", "pull", image);
        LOGGER.info("Pulling " + image);
        final LocalProcessOperator process = new LocalProcessOperator();
        process.execute(command, true);
    }

    void push(final String image) throws OperatorException {
        final List<String> command = Arrays.asList("docker", "push", image);
        LOGGER.info("Pushing " + image);
        final LocalProcessOperator process = new LocalProcessOperator();
        process.execute(command, true);
    }

    void retag(final String fromTag, final String toTag) throws OperatorException {
        final List<String> command = Arrays.asList("docker", "tag", fromTag, toTag);
        LOGGER.info("Retagging " + fromTag + " as " + toTag);
        final LocalProcessOperator process = new LocalProcessOperator();
        process.execute(command, true);
    }

    public void startRegistry() throws OperatorException {
        final String runningContainerId = getContainerId(true);

        if (runningContainerId.length() == 0) {
            final List<String> command;
            final String stoppedContainerId = getContainerId(false);
            if (stoppedContainerId.length() == 0) {
                command = Arrays.asList("docker", "run", "--detach",
                        "--publish", DOCKER_REGISTRY_PORT + ":" + DOCKER_REGISTRY_PORT,
                        "--name", DOCKER_REGISTRY_NAME, DOCKER_REGISTRY_IMAGE);
            } else {
                command = Arrays.asList("docker", "start", stoppedContainerId);
            }
            final LocalProcessOperator process = new LocalProcessOperator();
            System.out.println(String.join(" ", command));
            LOGGER.info("Starting registry " + DOCKER_REGISTRY_NAME);
            process.execute(command, true);
        } else {
            LOGGER.info("Registry " + DOCKER_REGISTRY_NAME + " already running");
        }
    }

    public void stopRegistry() throws OperatorException {
        final String containerId = getContainerId(true);
        if (containerId.length() > 0) {
            LOGGER.info("Stopping registry " + DOCKER_REGISTRY_NAME);
            final List<String> command = Arrays.asList("docker", "stop", containerId);
            final LocalProcessOperator process = new LocalProcessOperator();
            process.execute(command, true);
        } else {
            LOGGER.info("No registry called " + DOCKER_REGISTRY_NAME + " running, cant stop anything.");
        }
    }

    public void deleteRegistry() throws OperatorException {
        final String containerId = getContainerId(false);
        if (containerId.length() > 0) {
            LOGGER.info("Deleting registry " + DOCKER_REGISTRY_NAME);
            final List<String> command = Arrays.asList("docker", "rm", containerId);
            final LocalProcessOperator process = new LocalProcessOperator();
            process.execute(command, true);
        } else {
            LOGGER.info("No registry called " + DOCKER_REGISTRY_NAME + " exists, cant delete anything.");
        }
    }

    private String getContainerId(final boolean isRunning) throws OperatorException {
        final String allFlag = isRunning ? "" : "--all";
        final List<String> command = Arrays.asList("docker", "ps", "-q", allFlag, "--filter", "name=" + DOCKER_REGISTRY_NAME);
        final LocalProcessOperator process = new LocalProcessOperator();
        return process.execute(command, true);
    }

    public static void images() throws OperatorException {
        final LocalProcessOperator process = new LocalProcessOperator();
        LOGGER.info("Docker images");
        process.execute(Arrays.asList("docker", "image", "ls"), true);
    }

    public static void volumes() throws OperatorException {
        final LocalProcessOperator process = new LocalProcessOperator();
        LOGGER.info("Docker /var/lib/docker");
        process.execute(Arrays.asList("du", "-sh", "/var/lib/docker"), true);
        process.execute(Arrays.asList("df", "h"), true);
    }
}

