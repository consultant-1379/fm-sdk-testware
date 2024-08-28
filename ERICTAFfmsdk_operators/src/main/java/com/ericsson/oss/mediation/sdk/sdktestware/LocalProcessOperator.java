package com.ericsson.oss.mediation.sdk.sdktestware;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class LocalProcessOperator {
    private final static Logger LOGGER = LoggerFactory.getLogger(LocalProcessOperator.class);

    public String execute(final List<String> command, final boolean shell) throws OperatorException {
        return execute(command, shell, null);
    }

    public String execute(final List<String> command, final boolean shell, final Path workingDir) throws OperatorException {
        final List<String> pbCommand = new ArrayList<>();

        final boolean isLinux = Flags.osName().startsWith("Linux");
        if (shell) {
            if (isLinux) {
                pbCommand.add("/bin/bash");
                pbCommand.add("-c");
            } else {
                pbCommand.add("cmd");
                pbCommand.add("/c");
            }
            pbCommand.add(StringUtils.join(command, " "));
        } else {
            pbCommand.addAll(command);
        }

        LOGGER.info("Executing command: " + String.join(" ", pbCommand));

        final ProcessBuilder processBuilder = new ProcessBuilder(pbCommand);
        if (workingDir != null) {
            processBuilder.directory(workingDir.toFile());
        }
        processBuilder.redirectErrorStream(true);
        processBuilder.environment().put("PYTHONUNBUFFERED", "x");

        if (workingDir != null) {
            processBuilder.directory(workingDir.toFile());
        }

        final Process process;
        try {
            process = processBuilder.start();
        } catch (IOException e) {
            throw new OperatorException("Failed to start: " + String.join(" ", pbCommand), e);
        }

        final InputStream stdout = process.getInputStream();
        final StringBuffer buffer = new StringBuffer();
        final Thread threadOut = new Thread(() -> {
            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new InputStreamReader(stdout));
                for (String line; ((line = reader.readLine()) != null); ) {
                    LOGGER.info(line);
                    buffer.append(line);
                }
            } catch (IOException e) {
                LOGGER.error("Unexpected I/O exception reading from process.", e);
            } finally {
                try {
                    if (null != reader) reader.close();
                } catch (IOException e) {
                    LOGGER.error("Unexpected I/O exception closing a stream.", e);
                }
            }
        });
        threadOut.start();
        final int exitCode;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException e) {
            throw new OperatorException("Wait failed for: " + String.join(" ", pbCommand), e);
        }
        try {
            threadOut.join();
        } catch (InterruptedException e) {

        }
        if (exitCode != 0) {
            throw new OperatorException("exit-code:" + exitCode);
        }
        return buffer.toString();
    }
}
