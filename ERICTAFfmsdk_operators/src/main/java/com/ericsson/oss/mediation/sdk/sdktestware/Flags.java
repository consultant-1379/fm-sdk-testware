package com.ericsson.oss.mediation.sdk.sdktestware;

import com.ericsson.oss.mediation.sdk.fmsdktestware.AssertLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Flags {
    private final static Logger LOGGER = LoggerFactory.getLogger(Flags.class);

    private static String getProperty(final String name) {
        return getProperty(name, true);
    }

    private static String getProperty(final String name, final String defaultValue) {
        return System.getProperty(name, defaultValue);
    }

    private static String getProperty(final String name, final boolean required) {
        final String value = System.getProperty(name);
        if (required) {
            AssertLog.assertNotNull(value, "JVM property " + name + " not set (-D" + name + "=<value>)");
        }
        return value;
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
     * Get integration_value_type
     * Error will be thrown if property not defined
     *
     * @return filename
     */
    public static String integrationValueType() {
        return getProperty("integration_value_type");
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

    public static String sprintVersion() {
        final String[] pvs = productSetVersion().split("\\.");
        return String.join(".", pvs[0], pvs[1]);
    }

    private static boolean booleanValue(final String jvmParam, final boolean defaultValue) {
        final String value = System.getProperty(jvmParam, Boolean.toString(defaultValue));
        if (value == null) {
            return false;
        } else if (value.length() == 0) {
            return true;
        } else {
            return Boolean.parseBoolean(value);
        }
    }

    private static int intValue(final String jvmParam, final int defaultValue) {
        final String value = System.getProperty(jvmParam);
        if (value == null || value.length() == 0) {
            return defaultValue;
        } else {
            return Integer.getInteger(value);
        }
    }

    public static String sdkBuildManager() {
        return getProperty("sdkBuildManager");
    }

    /**
     * Default SDK Docker registry
     *
     * @return URL
     */
    private static String defaultRepositoryUrl() {
        return "armdocker.rnd.ericsson.se/proj_oss_releases/enm";
    }

    /**
     * Get the value of the repository-url JVM property
     *
     * @return URL
     */
    private static String repositoryUrlProperty() {
        return System.getProperty("repository-url");
    }

    public static String repositoryUrl() {
        return repositoryUrlProperty() == null ? defaultRepositoryUrl() : repositoryUrlProperty();
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
    public static Path tempDirectory() {
        if (Flags.osName().startsWith("Linux")) {
            return Paths.get("/var/tmp/taf_build_dir");
        } else {
            return Paths.get(System.getProperty("java.io.tmpdir"), "taf_build_dir");
        }
    }

    /**
     * Get OS name
     *
     * @return string
     */
    public static String osName() {
        return System.getProperty("os.name");
    }

    private static List<String> getJvmFlagListValue(final String propName, final String defaultValue) {
        return Arrays.asList(System.getProperty(propName, defaultValue).split(","));
    }

    private static List<SkipFlag> getSkipFlags() {
        final List<String> jvmFlagsStrings = getJvmFlagListValue("skipGeneration", "");
        final List<SkipFlag> jvmFlags = new ArrayList<>();
        for (String flagName : jvmFlagsStrings) {
            final SkipFlag flag = SkipFlag.fromString(flagName);
            if (flag != null) {
                jvmFlags.add(flag);
            }
        }
        return jvmFlags;
    }

    private static boolean isSkipFlagSet(final SkipFlag check) {
        final List<SkipFlag> jvmFlags = getSkipFlags();
        return jvmFlags.contains(check) || jvmFlags.contains(SkipFlag.ALL);
    }

    /**
     * Check if maven item generation should be skipped or not
     *
     * @return {@code true} is mvn should be skipped, {@code false} otherwise
     */
    public static boolean skipMavenGeneration() {
        return isSkipFlagSet(SkipFlag.MAVEN);
    }

    /**
     * Check if helm item generation should be skipped or not (images/charts)
     *
     * @return {@code true} is helm should be skipped, {@code false} otherwise
     */
    public static boolean skipSdkManagerBuildLoadImages() {
        return isSkipFlagSet(SkipFlag.BUILD_LOAD_IMAGES);
    }

    public static boolean skipSdkManagerCsarLoadImages() {
        return isSkipFlagSet(SkipFlag.LOAD_CSAR_IMAGES);
    }

    public static boolean skipSdkManagerRebuildCsar() {
        return isSkipFlagSet(SkipFlag.REBUILD_CSAR);
    }

    public static boolean skipVerify() {
        return isSkipFlagSet(SkipFlag.VERIFY);
    }

    public static boolean skipInstall() {
        return isSkipFlagSet(SkipFlag.INSTALL);
    }

    public static boolean skipSdkExtraction() {
        return isSkipFlagSet(SkipFlag.SDK_EXTRACT);
    }

    public static boolean skipCsarUpload() {
        return isSkipFlagSet(SkipFlag.CSAR_UPLOAD);
    }

    public static boolean useLocalDockerRegistry() {
        return booleanValue("docker.local", false);
    }

    public static boolean lightSdkCsar() {
        return booleanValue("csar.light", false);
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
     * Check if --dry-run should be used for any helm command
     *
     * @return boolean
     */
    public static boolean helmDryRun() {
        return booleanValue("helm.dry-run", false);
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

    public static int helmUpgradeTimeout() {
        return intValue("helm.upgrade.timeout", 660);
    }

    public static String sdkBuildManagerPreCommit() {
        return getProperty("sdkBuildManager.preCommit", false);
    }

    public static String directorPem(final String hostName) {
        return System.getProperty(hostName + ".private.key");
    }

    public static String sdkTemplate(final SdkType sdkType) {
        return System.getProperty(sdkType.templatePrefix());
    }

    public static List<SdkType> getSdkTypesToTest() {
        final List<String> list = getJvmFlagListValue("sdkTypes.test", SdkType.FM + "," + SdkType.PM);
        final List<SdkType> testTypes = new ArrayList<>();
        for (String typeName : list) {
            testTypes.add(SdkType.valueOf(typeName));
        }
        return testTypes;
    }

    public static String monitoringImage() {
        return getProperty("monitoring.image", false);
    }

    public static boolean canCleanBuildDir() {
        final List<SkipFlag> jvmSkipFlags = getSkipFlags();
        if (!jvmSkipFlags.isEmpty()) {
            if (jvmSkipFlags.size() == 1 && jvmSkipFlags.contains(SkipFlag.DUPLICATE_CHART)) {
                LOGGER.info("Can clean build dir, only skip flag set is " + SkipFlag.DUPLICATE_CHART);
                return true;
            } else {
                LOGGER.info("Can't clean build dir, skip flags are set " + jvmSkipFlags);
                return false;
            }
        }
        LOGGER.info("Can clean build dir, no skip flags set.");
        return true;
    }

    public static boolean displayEnv() {
        return booleanValue("env.display", false);
    }

    public static boolean skipDuplicateChartCheck() {
        return isSkipFlagSet(SkipFlag.DUPLICATE_CHART);
    }

    public static String getFmNeSimName() {
        return getProperty("taf.fm.ne.sim.name", "LTE04dg2ERBS00004");
    }

    enum SkipFlag {
        ALL("all"),
        MAVEN("maven"),
        BUILD_LOAD_IMAGES("build-load-images"),
        LOAD_CSAR_IMAGES("load-csar-images"),
        REBUILD_CSAR("rebuild-csar"),
        VERIFY("verify"),
        INSTALL("install"),
        SDK_EXTRACT("sdk-extract"),
        CSAR_UPLOAD("csar-upload"),
        DUPLICATE_CHART("dup-charts");

        private final String name;

        SkipFlag(final String name) {
            this.name = name;
        }

        static SkipFlag fromString(final String string) {
            for (SkipFlag value : SkipFlag.values()) {
                if (value.flag().equals(string)) {
                    return value;
                }
            }
            return null;
        }

        String flag() {
            return this.name;
        }
    }
}
