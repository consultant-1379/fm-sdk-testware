package com.ericsson.oss.mediation.sdk.sdktestware;

import org.apache.commons.io.IOUtils;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import java.util.TreeMap;

public enum SdkType {
    FM("data/Archetypes.json",
            "eric-enmsg-fmsdkexample",
            "sdk/fmsdk",
            "fm-sdk-templates",
            "eric-enm-fmsdk"),
    PM("data/Archetypes_PM.json",
            "eric-enmsg-pmsdkexample",
            "sdk/pmsdk",
            "pm-sdk-templates",
            "eric-enm-pmsdk");

    private final String archetypes;
    private final String chartName;
    private final String inputs;

    private final String templatePrefix;
    private final String baseImage;

    SdkType(final String archetypes, final String chartName, final String inputs, final String templatePrefix, final String baseImage) {
        this.archetypes = archetypes;
        this.chartName = chartName;
        this.inputs = inputs;
        this.templatePrefix = templatePrefix;
        this.baseImage = baseImage;
    }

    /**
     * Find and load Archetypes.json
     *
     * @return Information on what Maven artifacts to generate and build
     */
    public static Map<String, Map<String, Object>> loadArchetypes(final SdkType sdkType) throws OperatorException {
        final URL archetypeData = SdkType.class.getClassLoader().getResource(sdkType.archetypes());
        if (archetypeData == null) {
            throw new OperatorException("Could not find '" + sdkType.archetypes() + "' on classpath!");
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
            throw new OperatorException("Failed to load/read " + sdkType.archetypes(), e);
        }
        return archetypes;
    }

    String archetypes() {
        return this.archetypes;
    }

    String chartName() {
        return this.chartName;
    }

    String inputs() {
        return this.inputs;
    }

    String templatePrefix() {
        return this.templatePrefix;
    }

    String baseImage() {
        return this.baseImage;
    }
}