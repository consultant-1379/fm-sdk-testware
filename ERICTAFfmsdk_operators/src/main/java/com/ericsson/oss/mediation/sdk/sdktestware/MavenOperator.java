package com.ericsson.oss.mediation.sdk.sdktestware;

import org.apache.commons.io.FileUtils;
import org.apache.commons.text.StringSubstitutor;
import org.apache.maven.shared.invoker.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;

import static java.nio.file.Files.walk;

public class MavenOperator {

    private final static Logger LOGGER = LoggerFactory.getLogger(MavenOperator.class);

    private boolean isRemoveModels=false;

    public boolean isRemoveModels() {
        return isRemoveModels;
    }

    public void setRemoveModels(final boolean isRemoveModels) {
        this.isRemoveModels = isRemoveModels;
    }

    /**
     * Initialize the build env in ${TEMP}
     */
    public void initializeBuild(final Path buildDir) throws OperatorException {
        if (buildDir.toFile().exists()) {
            LOGGER.info("Cleaning " + buildDir.toFile().getAbsolutePath());
            try {
                walk(buildDir).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            } catch (IOException e) {
                throw new OperatorException("Failed to delete build dir " + buildDir.toFile().getAbsolutePath(), e);
            }
        }
        if (!buildDir.toFile().exists()) {
            if (!buildDir.toFile().mkdirs()) {
                throw new OperatorException("Failed to create build dir " + buildDir.toFile().getAbsolutePath());
            }
        }
    }

    /**
     * Run maven archetype:generate on the requested inputs
     *
     * @param archetypes Archetypes to generate
     */
    public void generateArchetypes(final Map<String, Map<String, Object>> archetypes, final Path buildDir) throws OperatorException {
        LOGGER.info("Generating " + archetypes.size() + " artifacts.");
        for (String archetypeName : archetypes.keySet()) {
            if (skipArchetype(archetypeName)) {
                continue;
            }

            final Properties requestProperties = new Properties();
            final StringBuilder cmdString = new StringBuilder("mvn archetype:generate \\\n");
            for (Map.Entry<String, Object> param : archetypes.get(archetypeName).entrySet()) {
                if (param.getKey().startsWith("_")) {
                    continue;
                }
                requestProperties.put(param.getKey(), param.getValue());
                cmdString.append("  -D").append(param.getKey()).append("=").append(param.getValue()).append(" \\\n");
            }
            cmdString.append("  -DinteractiveMode=false");
            LOGGER.info(cmdString.toString());


            final InvocationRequest request = new DefaultInvocationRequest();
            request.setGoals(Collections.singletonList("archetype:generate"));
            request.setProperties(requestProperties);
            request.setBaseDirectory(buildDir.toFile());

            LOGGER.info("Executing invocation request ...");
            final Invoker invoker = new DefaultInvoker();
            try {
                final InvocationResult result = invoker.execute(request);
                if (result.getExitCode() != 0) {
                    throw new OperatorException("Failed to generate archetype '" + archetypeName + "'",
                            result.getExecutionException());
                }
                LOGGER.info("Generate archetype '" + archetypeName + "' successfully");
            } catch (MavenInvocationException e) {
                throw new OperatorException("Failed to generate archetype '" + archetypeName + "'", e);
            }
        }
    }

    public void copyConfig(final Map<String, Map<String, Object>> archetypes, final Path buildDir) throws OperatorException {
        if(isRemoveModels())
        {
            return;
        }
        for (String archetypeName : archetypes.keySet()) {
            if (skipArchetype(archetypeName) || !archetypes.get(archetypeName).containsKey("_copy")) {
                continue;
            }
            final List<Map<String, Object>> updates = (List<Map<String, Object>>) archetypes.get(archetypeName).get("_copy");
            for (Map<String, Object> update : updates) {
                final List<String> file = (List<String>) update.get("file");

                final String srcFile = file.get(0);
                final File destFile = new File(
                        buildDir.toFile(),
                        StringSubstitutor.replace(file.get(1), archetypes.get(archetypeName)));
                final URL targetClasses = ClassLoader.getSystemResource(srcFile);
                final URL targetJar = getClass().getResource("/" + srcFile);

                try {
                    if (targetClasses != null) {
                        FileUtils.copyFile(new File(targetClasses.getFile()), destFile);
                    } else if (targetJar != null) {
                        final String[] parts = targetJar.getFile().split("!");
                        final Path archive = new File(parts[0].replace("file:", "")).toPath();
                        ArchiveOperator.extractFileFromJar(archive, parts[1].substring(1), destFile);
                    } else {
                        AssertLog.fail("No inputs structure templates found!");
                    }
                } catch (IOException e) {
                    throw new OperatorException("Failed to extract " + srcFile, e);
                }
                if (update.containsKey("update")) {
                    String contents;
                    try {
                        contents = FileUtils.readFileToString(destFile);
                    } catch (IOException e) {
                        throw new OperatorException("Failed to read " + destFile, e);
                    }
                    final Map<String, String> changes = (Map<String, String>) update.get("update");
                    for (final Map.Entry<String, String> mod : changes.entrySet()) {
                        final String value = StringSubstitutor.replace(mod.getValue(), archetypes.get(archetypeName));
                        contents = contents.replaceAll(mod.getKey(), value);
                    }
                    try {
                        FileUtils.write(destFile, contents);
                    } catch (IOException e) {
                        throw new OperatorException("Failed to write " + destFile, e);
                    }
                }
            }
        }
    }

    private boolean skipArchetype(final String name) {
        return name.startsWith("__");
    }

    /**
     * Build (and package) the requested  archetypes
     *
     * @param archetypes Archetypes to build and package
     */
    public void buildArchetypes(final Map<String, Map<String, Object>> archetypes, final Path buildDir) throws OperatorException {
        LOGGER.info("Building " + archetypes.size() + " artifacts.");
        List<String> goals=Arrays.asList("clean", "install", "package");
        if(isRemoveModels())
        {
            LOGGER.info("Remove models for maven archetype");
            goals=Arrays.asList("versions:set -DnewVersion=2.0.0","clean", "install", "-Premove-models");
        }
        for (String archetypeName : archetypes.keySet()) {
            if (skipArchetype(archetypeName)) {
                continue;
            }
            LOGGER.info("clean install package " + archetypeName);

            final InvocationRequest request = new DefaultInvocationRequest();
            request.setGoals(goals);

            final Map<String, Object> params = archetypes.get(archetypeName);
            final String archetypeArtifactId = params.get("artifactId").toString();

            request.setBaseDirectory(new File(buildDir.toFile(), archetypeArtifactId));
            LOGGER.info("Executing invocation request ...");
            final Invoker invoker = new DefaultInvoker();
            try {
                final InvocationResult result = invoker.execute(request);
                if (result.getExitCode() != 0) {
                    throw new OperatorException("Failed to build archetype '" + archetypeName + "'",
                            result.getExecutionException());
                }
                LOGGER.info("Built archetype '" + archetypeName + "' successfully");
            } catch (MavenInvocationException e) {
                throw new OperatorException("Failed to build archetype '" + archetypeName + "'", e);
            }
        }
    }

    /**
     * Get the packages generated for each archetype
     *
     * @param archetypes Archetypes to get the generated packages for
     * @return Map containing the packages (RPMS) generated. Map will be structured based on the
     * _targets layout in data/Archetypes.json
     */
    public Map<String, List<File>> getGeneratedArtifacts(final Map<String, Map<String, Object>> archetypes, final Path buildDir) {

        final Map<String, List<File>> packages = new HashMap<>();

        for (String artifact : archetypes.keySet()) {
            if (skipArchetype(artifact)) {
                continue;
            }
            final Map<String, Object> targets = (Map<String, Object>) archetypes.get(artifact).get("_targets");
            for (String targetType : targets.keySet()) {
                if (!packages.containsKey(targetType)) {
                    packages.put(targetType, new ArrayList<>());
                }

                final List<String> typeFiles = (List<String>) targets.get(targetType);
                for (String targetFmt : typeFiles) {
                    final String target = StringSubstitutor.replace(targetFmt, archetypes.get(artifact));
                    final File targetDir = new File(buildDir.toFile(), target);

                    final String[] rpms = targetDir.list((dir, name) -> name.endsWith(".rpm"));
                    if (rpms == null || rpms.length == 0) {
                        AssertLog.fail("No packages generated for artifact " + artifact);
                    }

                    for (String rpm : rpms) {
                        final File absPath = new File(targetDir, rpm);
                        packages.get(targetType).add(absPath);
                    }
                }
            }
        }

        return packages;
    }
}