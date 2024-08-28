package com.ericsson.oss.mediation.sdk.fmsdktestware;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.Map;

public class FmSdkArchetypesOperator {
    private final static Logger LOGGER = LoggerFactory.getLogger(FmSdkArchetypesOperator.class);
    /**
     * Generate the archetypes listed in data/Archetypes.json
     */
    public Map<String, List<File>> generateAndBuild() throws OperatorException {
        final MavenOperator mvn = new MavenOperator();
        final Map<String, Map<String, Object>> artifacts = mvn.loadArchetypes();

        if (!Flags.skipMavenGeneration()) {
            mvn.initializeBuild();
            mvn.generateArchetypes(artifacts);
            mvn.buildArchetypes(artifacts);
        }

        final Map<String, List<File>> packages = mvn.getGeneratedArtifacts(artifacts);
        LOGGER.info("Generated maven artifacts:");
        for (Map.Entry<String, List<File>> generated : packages.entrySet()) {
            LOGGER.info(generated.getKey());
            for (File file : generated.getValue()) {
                LOGGER.info("\t" + file.getAbsolutePath());
            }
        }
        return packages;
    }
}
