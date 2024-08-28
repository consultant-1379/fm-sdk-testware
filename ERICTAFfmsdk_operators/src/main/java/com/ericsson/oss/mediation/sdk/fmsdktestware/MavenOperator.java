package com.ericsson.oss.mediation.sdk.fmsdktestware;

import org.apache.commons.io.IOUtils;
import org.apache.commons.text.StringSubstitutor;
import org.apache.maven.shared.invoker.*;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;

import static java.nio.file.Files.walk;

public class MavenOperator {

    private final static Logger LOGGER = LoggerFactory.getLogger(MavenOperator.class);

    private static final File BUILD_DIR = new File(Flags.tempDirectory(), "MavenOperator");
    private static final String ARTIFACTS_JSON = "data/Archetypes.json";

    /**
     * Initialize the build env in ${TEMP}
     */
    public void initializeBuild() throws OperatorException {
        if (BUILD_DIR.exists()) {
            LOGGER.info("Cleaning " + BUILD_DIR.getAbsolutePath());
            try {
                walk(BUILD_DIR.toPath()).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            } catch (IOException e) {
                throw new OperatorException("Failed to delete build dir " + BUILD_DIR.getAbsolutePath(), e);
            }
        }
        if (!BUILD_DIR.exists()) {
            if (!BUILD_DIR.mkdirs()) {
                throw new OperatorException("Failed to create build dir " + BUILD_DIR.getAbsolutePath());
            }
        }
    }

    /**
     * Find and load Archetypes.json
     * @return Information on what Maven artifacts to generate and build
     */
    public Map<String, Map<String, Object>> loadArchetypes() throws OperatorException {
        final URL archetypeData = getClass().getClassLoader().getResource(ARTIFACTS_JSON);
        if (archetypeData == null) {
            throw new OperatorException("Could not find '" + ARTIFACTS_JSON + "' on classpath!");
        }

        final Map<String, Map<String, Object>> archetypes = new TreeMap<>();
        try (InputStream inputStream = archetypeData.openStream()) {

            final String jsonTxt = IOUtils.toString(inputStream, "UTF-8");
            final JSONObject json = new JSONObject(jsonTxt);

            for (final String artifact : json.keySet()) {
                final JSONObject jsonParams = (JSONObject) json.get(artifact);
                archetypes.put(artifact, jsonParams.toMap());
            }
        } catch (IOException e) {
            throw new OperatorException("Failed to load/read " + ARTIFACTS_JSON, e);
        }
        return archetypes;
    }

    /**
     * Run maven archetype:generate on the requested inputs
     *
     * @param archetypes Archetypes to generate
     */
    public void generateArchetypes(final Map<String, Map<String, Object>> archetypes) throws OperatorException {
        LOGGER.info("Generating " + archetypes.size() + " artifacts.");
        for (String archetypeName : archetypes.keySet()) {
            final Properties properties = new Properties();
            LOGGER.info("archetype:generate " + archetypeName);
            for (Map.Entry<String, Object> param : archetypes.get(archetypeName).entrySet()) {
                if (param.getKey().startsWith("_")) {
                    continue;
                }
                LOGGER.info("\t" + param.getKey() + "=" + param.getValue());
                properties.put(param.getKey(), param.getValue());
            }

            final InvocationRequest request = new DefaultInvocationRequest();
            request.setGoals(Collections.singletonList("archetype:generate"));
            request.setProperties(properties);
            request.setBaseDirectory(BUILD_DIR);

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

    /**
     * Build (and package) the requested  archetypes
     *
     * @param archetypes Archetypes to build and package
     */
    public void buildArchetypes(final Map<String, Map<String, Object>> archetypes) throws OperatorException {
        LOGGER.info("Building " + archetypes.size() + " artifacts.");
        for (String artifactNAme : archetypes.keySet()) {
            LOGGER.info("clean install package " + artifactNAme);

            final InvocationRequest request = new DefaultInvocationRequest();
            request.setGoals(Arrays.asList("clean", "install", "package"));

            final Map<String, Object> params = archetypes.get(artifactNAme);
            final String archetypeArtifactId = params.get("artifactId").toString();

            request.setBaseDirectory(new File(BUILD_DIR, archetypeArtifactId));
            LOGGER.info("Executing invocation request ...");
            final Invoker invoker = new DefaultInvoker();
            try {
                final InvocationResult result = invoker.execute(request);
                if (result.getExitCode() != 0) {
                    throw new OperatorException("Failed to build archetype '" + artifactNAme + "'",
                            result.getExecutionException());
                }
                LOGGER.info("Built archetype '" + artifactNAme + "' successfully");
            } catch (MavenInvocationException e) {
                throw new OperatorException("Failed to build archetype '" + artifactNAme + "'", e);
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
    public Map<String, List<File>> getGeneratedArtifacts(final Map<String, Map<String, Object>> archetypes) {

        final Map<String, List<File>> packages = new HashMap<>();

        for (String artifact : archetypes.keySet()) {
            final Map<String, Object> targets = (Map<String, Object>) archetypes.get(artifact).get("_targets");
            for (String targetType : targets.keySet()) {
                if (!packages.containsKey(targetType)) {
                    packages.put(targetType, new ArrayList<>());
                }

                final List<String> typeFiles = (List<String>) targets.get(targetType);
                for (String targetFmt : typeFiles) {
                    final String target = StringSubstitutor.replace(targetFmt, archetypes.get(artifact));
                    final File targetDir = new File(BUILD_DIR, target);

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