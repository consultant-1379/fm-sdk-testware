package com.ericsson.oss.mediation.sdk.sdktestware;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class EnvironmentOperator {
    private final static Logger LOGGER = LoggerFactory.getLogger(EnvironmentOperator.class);
    public static void display() throws OperatorException {
        final Properties sysProps = System.getProperties();
        final List<Object> sysKeys = new ArrayList<>(sysProps.keySet());
        sysKeys.sort(Comparator.comparing(Object::toString));
        LOGGER.info("Environment JVM Properties:");
        for(Object key : sysKeys){
            LOGGER.info(key + "=" + sysProps.get(key));
        }

        final Map<String, String> envVars = System.getenv();
        final List<String> envKeys = new ArrayList<>(envVars.keySet());
        envKeys.sort(Comparator.comparing(Object::toString));
        LOGGER.info("Environment Variables:");
        for(String key : envKeys){
            LOGGER.info(key + "=" + envVars.get(key));
        }

        DockerOperator.version();
        DockerOperator.images();
//        DockerOperator.volumes();
        PipOperator.version();
    }
}
