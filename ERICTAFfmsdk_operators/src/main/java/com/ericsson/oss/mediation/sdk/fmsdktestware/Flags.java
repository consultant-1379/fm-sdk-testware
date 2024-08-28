package com.ericsson.oss.mediation.sdk.fmsdktestware;

import java.util.*;

public class Flags {

    private static String getProperty(final String name) {
        return getProperty(name, true);
    }

    private static String getProperty(final String name, final boolean required) {
        final String value = System.getProperty(name);
        if (required) {
            AssertLog.assertNotNull(value, "JVM property " + name + " not set (-D" + name + "=<value>)");
        }
        return value;
    }

    private static List<String> getSkipJvmFlags() {
        return Arrays.asList(System.getProperty("skipGeneration", "").split(","));
    }

    /**
     * Check if maven item generation should be skipped or not
     *
     * @return {@code true} is mvn should be skipped, {@code false} otherwise
     */
    public static boolean skipMavenGeneration() {
        final List<String> jvmFlags = getSkipJvmFlags();
        return jvmFlags.contains("mvn") || jvmFlags.contains("all");
    }

    /**
     * Check if helm item generation should be skipped or not (images/charts)
     *
     * @return {@code true} is helm should be skipped, {@code false} otherwise
     */
    public static boolean skipHelmGeneration() {
        final List<String> jvmFlags = getSkipJvmFlags();
        return jvmFlags.contains("helm") || jvmFlags.contains("all");
    }

    /**
     * Default SDK Docker registry
     *
     * @return URL
     */
    public static String defaultRepositoryUrl() {
        return "armdocker.rnd.ericsson.se";
    }

    /**
     * Get the value of the repository-url JVM property
     *
     * @return URL
     */
    public static String repositoryUrl() {
        return System.getProperty("repository-url");
    }

    /**
     * Get the value of the eric-enm-fmsdk JVM property
     * Error will be thrown if property not defined
     *
     * @return TAG
     */
    public static String ericEnmFmsdkVersion() {
        return getProperty("eric-enm-fmsdk");
    }

    /**
     * Get the value of the sdkBuildManagerJVM property
     * Error will be thrown if property not defined
     *
     * @return URL
     */
    public static String sdkBuildManager() {
        return getProperty("sdkBuildManager");
    }

    /**
     * Get the value of the fmSdkTemplates property
     * Error will be thrown if property not defined
     *
     * @return URL
     */
    public static String fmSdkTemplates() {
        return getProperty("fmSdkTemplates", false);

    }

    /**
     * Get the users HOME directory
     *
     * @return dir
     */
    public static String userHome() {
        return System.getProperty("user.home");
    }

    /**
     * Get the users TEMP directory
     *
     * @return dir
     */
    public static String tempDirectory() {
        return System.getProperty("java.io.tmpdir");
    }

    /**
     * Get OS name
     *
     * @return string
     */
    public static String osName() {
        return System.getProperty("os.name");
    }

    /**
     * Get taf.config.dit.deployment.name
     * Error will be thrown if property not defined
     *
     * @return hostname
     */
    public static String tafConfigDitDeploymentName() {
        return getProperty("taf.config.dit.deployment.name");
    }

    /**
     * Get product_set_version
     * Error will be thrown if property not defined
     *
     * @return version
     */
    public static String productSetVersion() {
        return getProperty("product_set_version");
    }

    /**
     * Get integration_value_type
     * Error will be thrown if property not defined
     *
     * @return filename
     */
    public static String integrationValueType() {
        return getProperty("integration_value_type");
    }

    private static boolean booleanValue(final String jvmParam) {
        final String value = System.getProperty(jvmParam);
        if (value == null) {
            return false;
        } else if (value.length() == 0) {
            return true;
        } else {
            return Boolean.parseBoolean(value);
        }
    }

    /**
     * Check if --dry-run should be used for any helm command
     *
     * @return boolean
     */
    public static boolean helmDryRun() {
        return booleanValue("helm.dry-run");
    }

    /**
     * If {@code true} only run test assertions
     *
     * @return boolean
     */
    public static boolean assertOnly() {
        return booleanValue("assertOnly");
    }

    /**
     * Check if --atomic should be used for any helm command
     *
     * @return boolean
     */
    public static boolean helmAtomic() {
        return Boolean.parseBoolean(System.getProperty("helm.atomic", "true"));
    }

    /**
     * Get any extra helm command values to use during helm commands
     *
     * @return List of key-value pairs to append to helm commands (helm --set key=value ...)
     */
    public static List<String> helmExtraValues() {
        final Properties properties = System.getProperties();
        final List<String> extraValues = new ArrayList<>();
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            String key = entry.getKey().toString();
            if (key.startsWith("helm.values.")) {
                key = key.replace("helm.values.", "");
                final String value = entry.getValue().toString();
                extraValues.add(key + "=" + value);
            }
        }
        return extraValues;
    }
}