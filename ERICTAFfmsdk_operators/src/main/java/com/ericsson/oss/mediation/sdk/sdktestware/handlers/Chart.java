package com.ericsson.oss.mediation.sdk.sdktestware.handlers;

import com.ericsson.oss.mediation.sdk.sdktestware.OperatorException;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class Chart extends BaseYaml {
    public Chart(Path path) throws OperatorException {
        super(path);
    }

    public Map<String, String> getDependency(final String subChartName) {
        for (Map<?, ?> dependency : getDependencies()) {
            if(dependency.get("name").equals(subChartName)){
                return (Map<String, String>) dependency;
            }
        }
        return null;
    }

    private List<Map<?, ?>> getDependencies() {
        return (List<Map<?, ?>>) this.data.get("dependencies");
    }
}
