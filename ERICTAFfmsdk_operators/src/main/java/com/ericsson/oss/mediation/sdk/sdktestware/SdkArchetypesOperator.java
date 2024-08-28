package com.ericsson.oss.mediation.sdk.sdktestware;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class SdkArchetypesOperator {
    private final static Logger LOGGER = LoggerFactory.getLogger(SdkArchetypesOperator.class);

    private boolean isRemoveModel=false;

    public boolean isRemoveModel() {
        return isRemoveModel;
    }

    public void setRemoveModel(final boolean isRemoveModel) {
        this.isRemoveModel = isRemoveModel;
    }

    /**
     * Generate the archetypes listed in data/Archetypes.json
     */
    public Map<String, List<File>> generateAndBuild(final SdkType sdkType, final Path buildDir) throws OperatorException {
        final MavenOperator mvn = new MavenOperator();
        mvn.setRemoveModels(isRemoveModel());
        final Map<String, Map<String, Object>> artifacts = SdkType.loadArchetypes(sdkType);

        if (!Flags.skipMavenGeneration() || !buildDir.toFile().exists()) {
            mvn.initializeBuild(buildDir);
            mvn.generateArchetypes(artifacts, buildDir);
            mvn.copyConfig(artifacts, buildDir);
            mvn.buildArchetypes(artifacts, buildDir);
        }

        final Map<String, List<File>> packages = mvn.getGeneratedArtifacts(artifacts, buildDir);
        LOGGER.info("Generated maven artifacts:");
        for (Map.Entry<String, List<File>> generated : packages.entrySet()) {
            LOGGER.info(generated.getKey());
            for (File file : generated.getValue()) {
                LOGGER.info("\t" + file.getAbsolutePath());
            }
        }
        return packages;
    }

    public Map<String, List<File>> generateAndBuildForRemoveModels(final SdkType sdkType, final Path buildDir) throws OperatorException {
        setRemoveModel(true);
        return generateAndBuild(sdkType,buildDir);
    }
}
