package com.ericsson.oss.mediation.sdk.sdktestware.handlers;

import com.ericsson.oss.mediation.sdk.sdktestware.OperatorException;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

class BaseYaml {

    private final Path source;

    protected Map<String, Object> data;

    protected BaseYaml(final Path path) throws OperatorException {
        this.source = path;
        try (final InputStream inputStream = Files.newInputStream(this.source)) {
            data = new Yaml().loadAs(inputStream, Map.class);
        } catch (IOException e) {
            throw new OperatorException("Failed to load " + this.source, e);
        }
    }

    public void save() throws OperatorException {
        try (final BufferedWriter writer = new BufferedWriter(new FileWriter(source.toFile()))) {
            final DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            final Yaml yaml = new Yaml(options);
            yaml.dump(data, writer);
        } catch (IOException e) {
            throw new OperatorException(e);
        }
    }
}
