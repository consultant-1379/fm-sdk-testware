package com.ericsson.oss.mediation.sdk.sdktestware.handlers;

import com.ericsson.oss.mediation.sdk.sdktestware.OperatorException;

import java.nio.file.Path;
import java.util.Map;

public class ChartValues extends BaseYaml {
    public ChartValues(Path path) throws OperatorException {
        super(path);
    }

    public String getImageCredentialsRepoPath() {
        return this.getImageCredentials().get("repoPath").toString();
    }

    public String getImageName(final String imageId) {
        return getImage(imageId).get("name").toString();
    }

    public String getImageTag(final String imageId) {
        return getImage(imageId).get("tag").toString();
    }

    private Map<?, ?> getImageCredentials() {
        return (Map<?, ?>) data.get("imageCredentials");
    }

    private Map<?, ?> getImages() {
        return (Map<?, ?>) data.get("images");
    }

    private Map<?, ?> getImage(final String imageId){
        return (Map<?, ?>) getImages().get(imageId);
    }
}
