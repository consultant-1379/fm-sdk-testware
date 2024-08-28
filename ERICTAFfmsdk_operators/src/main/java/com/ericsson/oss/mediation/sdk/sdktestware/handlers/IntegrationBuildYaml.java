package com.ericsson.oss.mediation.sdk.sdktestware.handlers;

import com.ericsson.oss.mediation.sdk.sdktestware.OperatorException;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class IntegrationBuildYaml extends BaseYaml {
    public IntegrationBuildYaml(Path path) throws OperatorException {
        super(path);
    }

    public void setDependencies(final List<Map<String, String>> dependencies) {
        data.put("dependencies", dependencies);
    }

    public String getName() {
        return (String) data.get("name");
    }

    public String getVersion() {
        return (String) data.get("version");
    }
}
