package com.ericsson.oss.mediation.sdk.sdktestware.handlers;

import com.ericsson.oss.mediation.sdk.sdktestware.OperatorException;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class IntegrationValues extends BaseYaml {

    public IntegrationValues(Path path) throws OperatorException {
        super(path);
    }

    public String getGlobalRegistryUrl() {
        return getRegistry().get("url").toString();
    }

    private Map<?, ?> getRegistry() {
        return (Map<?, ?>) getGlobal().get("registry");
    }

    private Map<?, ?> getGlobal() {
        return (Map<?, ?>) data.get("global");
    }

    private Map<String, String> getVips(){
        return (Map<String, String>) getGlobal().get("vips");
    }

    public String getFmVipAddress(){
        return getVips().get("fm_vip_address");
    }
}
