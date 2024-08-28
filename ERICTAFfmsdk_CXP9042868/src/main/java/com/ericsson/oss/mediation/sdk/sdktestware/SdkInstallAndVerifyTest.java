package com.ericsson.oss.mediation.sdk.sdktestware;

import com.ericsson.cifwk.taf.TafTestBase;
import com.ericsson.cifwk.taf.annotations.TestId;
import com.ericsson.oss.mediation.sdk.sdktestware.handlers.IntegrationBuildYaml;
import com.ericsson.oss.mediation.sdk.sdktestware.handlers.IntegrationValues;
import org.apache.commons.configuration2.JSONConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;


public class SdkInstallAndVerifyTest extends TafTestBase {
    private final static Logger LOGGER = LoggerFactory.getLogger(SdkInstallAndVerifyTest.class);

    private static final String TEST_NAME = "CN-SDK-INSTALL-VERIFY";
    private static final Path SDK_BUILD_DIR = Flags.tempDirectory();
    private static final Path MVN_BUILD_DIR = Paths.get(SDK_BUILD_DIR.toString(), "maven");
    private static final Path MVN_BUILD_DIR_RM_MODELS = Paths.get(MVN_BUILD_DIR.toString(),"removemodels");
    private static final Path INPUTS_DIR = Paths.get(SDK_BUILD_DIR.toString(), "sdk-inputs");
    private static final Path OUTPUT_DIR = Paths.get(SDK_BUILD_DIR.toString(), "custom");

    private static final String REMOTE_SDK_DIR = "/var/tmp/taf-sdk";

    private static void tearDown() throws IOException {
        if (Flags.canCleanBuildDir()) {
            final File tafBuildDir = SDK_BUILD_DIR.toFile();
            if (tafBuildDir.exists()) {
                LOGGER.info("Deleting " + tafBuildDir);
                try (final Stream<Path> entries = Files.walk(SDK_BUILD_DIR)) {
                    entries.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
                }
            }
            if (!tafBuildDir.exists()) {
                tafBuildDir.mkdirs();
                LOGGER.info("Created " + SDK_BUILD_DIR);
            }
        }
    }

    @TestId(id = "SDK-INSTALL-VERIFY", title = "Generate custom FM & PM SDK images and chart and install on a cENM env.")
    @Test
    public void installAndVerify() {
        try {
            if (Flags.displayEnv()) {
                EnvironmentOperator.display();
            }

            tearDown();

            final String sdkBuildManagerArchive = Flags.sdkBuildManager();
            final Path tarGz = FileOperator.getExternalFile(sdkBuildManagerArchive);
            if (!Flags.skipSdkExtraction()) {
                ArchiveOperator.extractArchive(tarGz, SDK_BUILD_DIR);
            } else {
                LOGGER.info("Skipping extraction of " + tarGz + ", skip flag set.");
            }
            sdkBuildManagerPreCommit();

            final List<SdkType> sdkBuildTypes = Flags.getSdkTypesToTest();
            final Path integrationValues = getIntegrationValues();

            final String monitoringImage = getMonitoringImage(integrationValues);
            LOGGER.info("Using monitoring image " + monitoringImage);
            final Map<SdkType, Path> sdkCharts = prepareCharts(sdkBuildTypes, monitoringImage);
            final Path sdkIntegCsar = rebuildCsar(sdkCharts);

            final String remoteCsar = uploadFileToDirector(sdkIntegCsar);
            final String remoteValues = uploadFileToDirector(integrationValues);

            installIntegrationChart(remoteCsar, remoteValues);
            verifyChartInstallation(remoteCsar);

            for (SdkType sdkType : sdkBuildTypes)
            {
                verifyModels(sdkType);
                verifySdkFunction(sdkType);
            }
        } catch (AssertionError error) {
            AssertLog.fail("TestCase:" + TEST_NAME + " ASSERTION-FAILED", error);
            throw error;
        } catch (Throwable error) {
            AssertLog.fail("TestCase:" + TEST_NAME + " OPERATOR-FAILED", error);
            throw new AssertionError(error);
        }
    }

    private String getMonitoringImage(final Path integrationValues) throws OperatorException {

        final HelmValuesOperator helm = new HelmValuesOperator();
        final IntegrationValues values = new IntegrationValues(integrationValues);
        final String integrationRegistry = values.getGlobalRegistryUrl();

        return helm.getMonitoringImage(integrationRegistry);
    }

    /**
     * Create Docker images and helm charts for SDK types
     *
     * @param sdkTypes List of SdkType to build charts for
     * @return Maping of SdkTYpe to the CSAR that contains the chart/images
     * @throws OperatorException Any errors creating the images/charts
     */
    private Map<SdkType, Path> prepareCharts(final List<SdkType> sdkTypes, final String monitoringImage) throws OperatorException {
        final Map<SdkType, Map<String, List<File>>> packages = prepareMavenArchetypes(sdkTypes);
        final Map<String, String> images = loadCsarImages();
        images.put(monitoringImage, monitoringImage);
        setupSdkBuildInputs(packages, images);

        final Map<SdkType, Path> sdkCharts = new HashMap<>();
        for (SdkType sdkType : sdkTypes) {
            final Path sdkChart = buildLoadImages(sdkType);
            sdkCharts.put(sdkType, sdkChart);
        }
        return sdkCharts;
    }

    /**
     * Verify a chart is installed, looking at Deployment/Pod
     *
     * @throws OperatorException Any errors while verifying chart
     */
    private void verifyChartInstallation(final String chartCsar) throws OperatorException {
        if (Flags.skipVerify()) {
            LOGGER.info("Skipping verification of charts, skip flag set.");
            return;
        }
        final Path helmPkgDir = Paths.get(Paths.get(chartCsar).getParent().toString(), "Definitions/OtherTemplates");

        final List<String> templateServices = new ArrayList<>();

        // Get the Deployment item from the chart to determine the service_name to verify
        try (final RemoteAccessOperator remote = new RemoteAccessOperator()) {
            final StringBuilder stdout = new StringBuilder();
            remote.execute("ls " + helmPkgDir + "/*.tgz", 5, stdout, null);
            for (String tgzFile : stdout.toString().split("\n")) {
                final String template = HelmExecutorOperator.templateRemote(tgzFile);
                final Yaml yaml = new Yaml();
                for (Object object : yaml.loadAll(template)) {

                    final Map<String, ?> data = (Map<String, ?>) object;
                    final Map<String, ?> metadata = (Map<String, ?>) data.get("metadata");

                    final String itemKind = data.get("kind").toString();
                    final String itemName = metadata.get("name").toString();

                    if (itemKind.equals("Service")) {
                        templateServices.add(itemName);
                    }
                }
            }
        }

        final KubeOperator kubectl = new KubeOperator();
        final List<String> services = kubectl.svcs();

        for (String templateService : templateServices) {
            AssertLog.assertContains(services, templateService);
            LOGGER.info("ASSERTION-PASSED: Expected Service '" + templateService + "' exists");
            final String selector = "app=" + templateService;

            final List<String> replicaSets = kubectl.replicasets(selector);
            AssertLog.assertNotNullOrEmpty(replicaSets, "No ReplicaSet found for selector '" + selector + "'");
            AssertLog.assertSize(replicaSets, 1, "Wrong number of ReplicaSets found for 'selector " + selector + "'");
            final String replicaSet = replicaSets.get(0);
            LOGGER.info("ASSERTION-PASSED: Found one ReplicaSet '" + replicaSet + "' for selector '" + selector + "'");

            final JSONConfiguration json = kubectl.get(KubeOperator.Kinds.ReplicaSet, replicaSet);
            final String keySpecReplicas = "spec.replicas";
            final int expectedPodCount = json.getInt(keySpecReplicas);

            final List<String> pods = kubectl.pods(selector);
            final String rsInfo = replicaSet + "." + keySpecReplicas + "=" + expectedPodCount;
            AssertLog.assertNotNullOrEmpty(pods, "No Pods found for Service selector '" + selector + "'");
            AssertLog.assertSize(pods, expectedPodCount, "Wrong number of Pods found for selector '" + selector + "' and ReplicaSet '" + rsInfo + "'");
            LOGGER.info("ASSERTION-PASSED: Correct number of Pods found (" + expectedPodCount + ") " + "for selector '" + selector + "' and ReplicaSet '" + rsInfo + "'");

            final Map<String, String> podPhases = kubectl.getPodPhases(selector);
            boolean phaseAssertOk = true;
            final String expectedPhase = "Running";
            for (final Map.Entry<String, String> podPhase : podPhases.entrySet()) {
                final String actualPhase = podPhase.getValue();
                final String podName = podPhase.getKey();

                if (actualPhase.equalsIgnoreCase(expectedPhase)) {
                    LOGGER.info("ASSERTION-PASSED: Pod '" + podName + "' in correct phase '" + actualPhase + "'");
                } else {
                    LOGGER.info("ASSERTION-FAILED: Pod '" + podName + "' not in the correct phase, " + "expected:Running actual:" + actualPhase + "'");
                    phaseAssertOk = false;
                }
            }
            AssertLog.assertTrue(phaseAssertOk, "Not all Pods are in the '" + expectedPhase + "' phase");
            LOGGER.info("ASSERTION-PASSED: All Pods are in the correct '" + expectedPhase + "' phase");
        }
    }

    private void verifySdkFunction(final SdkType sdkType) throws OperatorException {
        if (Flags.skipVerify()) {
            LOGGER.info("Skipping verification of " + sdkType + ", skip flag set.");
            return;
        }
        /* functional test for FM */
        if (sdkType == SdkType.FM) {
            final IntegrationValues integValues = new IntegrationValues(getIntegrationValues());
            final String fmVipAddress = integValues.getFmVipAddress();
            LOGGER.info("loading yaml file to get fm_vip_address");
            /* create network elements */
            final String[] neNameAndIp = configureNetSims(fmVipAddress);
            createNetworkElementAndSendAlarm(sdkType, neNameAndIp);
        } else {
            /*Verification for PM*/
            verifyPMFunctionality(sdkType);
        }
    }

    /**
     * Verify the SDK model has been installed using cmedit
     *
     * @param sdkType The SDK type model to check for
     * @throws OperatorException Ant errors
     */
    private void verifyModels(final SdkType sdkType) throws OperatorException {
        final SdkEnmCliOperator enmCli = new SdkEnmCliOperator();
        enmCli.verifyModels(sdkType);
    }

    /**
     * configures Netsims and sends alarm
     *
     * @throws OperatorException On Error
     */
    private String[] configureNetSims(String fmvipAddress) throws OperatorException {
        SdkEnmCliOperator enmCli = new SdkEnmCliOperator();
        return enmCli.configureNetsims(fmvipAddress);
    }

    private void createNetworkElementAndSendAlarm(final SdkType sdkType, String[] neNameAndIp) throws OperatorException {
        SdkEnmCliOperator enmCli = new SdkEnmCliOperator();
        enmCli.createNE(neNameAndIp[0], sdkType, neNameAndIp[1], neNameAndIp[2]);
    }

    /**
     * Install a helm chart
     *
     * @param remoteCsar   Path to the chart CSAR on the director node
     * @param remoteValues cENM integration values path on the director node.
     * @throws OperatorException Any errors installing the chart
     */
    private void installIntegrationChart(final String remoteCsar, final String remoteValues) throws OperatorException {
        if (Flags.skipInstall()) {
            LOGGER.info("Skipping install of " + remoteCsar + ", skip flag set.");
            return;
        }

        final String workingDir = FilenameUtils.getFullPath(remoteCsar);
        final String remoteChart;
        try (final RemoteAccessOperator remote = new RemoteAccessOperator()) {
            remote.execute("unzip -o " + remoteCsar + " -d " + workingDir, 300);

            final StringBuilder stdout = new StringBuilder();

            final String templatesDir = workingDir + "/Definitions/OtherTemplates/*.tgz";
            remote.execute("ls " + templatesDir, 5, stdout, null);

            final List<String> packages = Arrays.asList(stdout.toString().split("\n"));
            packages.removeAll(Arrays.asList("", null));

            if (packages.isEmpty()) {
                throw new OperatorException("No charts found in " + templatesDir);
            } else if (packages.size() > 1) {
                throw new OperatorException("More than one chart found in " + templatesDir);
            }
            remoteChart = packages.get(0);
        }

        final HelmExecutorOperator helm = new HelmExecutorOperator();
        helm.upgradeInstall(Collections.singletonList(remoteChart), remoteValues);
    }

    private Path getIntegrationValues() throws OperatorException {
        final HelmValuesOperator helmValuesOperator = new HelmValuesOperator();
        return helmValuesOperator.getHelmInstallValuesFile(SDK_BUILD_DIR);
    }

    /**
     * Get the helm integration values file for the cluster/namespace from DIT and upload to director node.
     *
     * @return Location of values file on director node
     * @throws OperatorException Any errors generating the values file or uploading it
     */
    private String uploadFileToDirector(final Path integValues) throws OperatorException {
        final String remote = REMOTE_SDK_DIR + "/" + integValues.getFileName();
        if (!Flags.skipCsarUpload()) {
            try (final RemoteAccessOperator sftp = new RemoteAccessOperator()) {
                sftp.put(integValues.toString(), remote);
            }
        }
        return remote;
    }

    /**
     * Execute the sdkBuildManager --rebuild-csar
     *
     * @param sdkCharts The SDK types being built
     * @return PAth to generated CSAR
     * @throws OperatorException Any errors
     */
    private Path rebuildCsar(final Map<SdkType, Path> sdkCharts) throws OperatorException {
        Paths.get(INPUTS_DIR.toString(), "sdk", "integration.yaml");
        final SdkBuildManagerOperator sdkBuildManagerOperator = new SdkBuildManagerOperator();
        sdkBuildManagerOperator.setupSdkIntegrationCsarInputs(INPUTS_DIR);

        final Path integFile = Paths.get(INPUTS_DIR.toString(), "sdk", "integration.yaml");
        final List<Map<String, String>> dependencies = new ArrayList<>();

        for (SdkType sdkType : sdkCharts.keySet()) {
            final Path chartPath = sdkCharts.get(sdkType);
            final String baseName = FilenameUtils.getBaseName(chartPath.toString());
            final String version = baseName.replace(sdkType.chartName() + "-", "");
            final Path location = Paths.get(chartPath.getParent().toString(), sdkType.chartName(), "chart", sdkType.chartName());

            final Map<String, String> dependency = new HashMap<>();
            dependency.put("name", sdkType.chartName());
            dependency.put("version", version);
            dependency.put("repository", "file://" + location);
            dependencies.add(dependency);
        }

        final IntegrationBuildYaml integYaml = new IntegrationBuildYaml(integFile);
        integYaml.setDependencies(dependencies);

        integYaml.save();

        return sdkBuildManagerOperator.rebuildCsar(getSdkBuildManagerScript(), integFile, OUTPUT_DIR,
                DockerOperator.getRegistryPrefix(), Flags.lightSdkCsar());
    }

    /**
     * Execute the sdkBuildManager --build-load-images
     *
     * @param sdkType The SDK type being build
     * @throws OperatorException Any errors
     */
    private Path buildLoadImages(final SdkType sdkType) throws OperatorException {
        final SdkBuildManagerOperator sdkBuildManagerOperator = new SdkBuildManagerOperator();

        final Path chartTemplate = sdkBuildManagerOperator.getSdkTemplates(SDK_BUILD_DIR, sdkType);
        final Path buildInputs = Paths.get(INPUTS_DIR.toString(), sdkType.inputs());

        final File[] customCharts = sdkBuildManagerOperator.buildLoadImages(getSdkBuildManagerScript(), chartTemplate,
                buildInputs, DockerOperator.getRegistryPrefix(), Paths.get(OUTPUT_DIR.toString(), sdkType.toString()));
        final File sdkChart = customCharts[0];
        LOGGER.info("Created custom chart " + sdkChart.getAbsolutePath());
        HelmExecutorOperator.template(sdkChart.getAbsolutePath());
        return sdkChart.toPath();
    }

    /**
     * Set up the --sdk-input-path location with test data & copy in maven artifacts
     *
     * @param packages SDK maven packages
     * @param images   Images contained in the sdk-csar-buildmanager archive
     * @throws OperatorException Any errors
     */
    private void setupSdkBuildInputs(final Map<SdkType, Map<String, List<File>>> packages, final Map<String, String> images) throws OperatorException {
        final SdkBuildManagerOperator sdkBuildManagerOperator = new SdkBuildManagerOperator();
        for (SdkType sdkType : packages.keySet()) {
            sdkBuildManagerOperator.setupSdkBuildInputs(sdkType, INPUTS_DIR, packages.get(sdkType), images);
        }
    }

    /**
     * Generate and build the SDK maven archetypes
     *
     * @param sdkTypes The SDK type to prepare
     * @return What RPMs were built and if they're model or service (jboss) rpms
     * @throws OperatorException Any errors
     */
    private Map<SdkType, Map<String, List<File>>> prepareMavenArchetypes(final List<SdkType> sdkTypes) throws OperatorException {
        final Map<SdkType, Map<String, List<File>>> packages = new HashMap<>();

        final SdkArchetypesOperator archetypesOperator = new SdkArchetypesOperator();

        for (SdkType sdkType : sdkTypes) {
            final Path mvnBuildDir = Paths.get(MVN_BUILD_DIR.toString(), sdkType.toString());

            // TODO: Thread this out, speed things up a bit
            final Map<String, List<File>> sdkPackages = archetypesOperator.generateAndBuild(sdkType, mvnBuildDir);
            final Path rmModels = Paths.get(MVN_BUILD_DIR_RM_MODELS.toString(), sdkType.toString());
            final Map<String, List<File>> removeModelsPackage=archetypesOperator.generateAndBuildForRemoveModels(sdkType, rmModels);
            final List<File> removeModelsFile= removeModelsPackage.get("models");
            sdkPackages.get("uninstall").addAll(removeModelsFile);
            packages.put(sdkType, sdkPackages);
        }

        return packages;
    }

    /**
     * Load any images that are in the SDKs docker.tar
     * If the docker.tar is empty i.e. a light build of sdk-csar-buildmanager, nothing gets loaded, just returns the
     * list of images.
     * -
     * If the docker.tar does contain images, sdkBuildManager.py is called with the --load-csar-images flag and the
     * list of images loaded is returned.
     */
    private Map<String, String> loadCsarImages() throws OperatorException {
        final Path sdkBuildManager = getSdkBuildManagerScript();
        final Path dockerTar = Paths.get(SDK_BUILD_DIR.toString(), "docker", "docker.tar");
        final Path imagesTxt = Paths.get(SDK_BUILD_DIR.toString(), "docker", "images.txt");

        final DockerOperator dockerOperator = new DockerOperator();
        if (!Flags.skipSdkManagerCsarLoadImages() && Flags.useLocalDockerRegistry()) {
            dockerOperator.startRegistry();
        }
        return dockerOperator.loadImages(sdkBuildManager, dockerTar, imagesTxt);
    }

    private Path getSdkBuildManagerScript() {
        return Paths.get(SDK_BUILD_DIR.toString(), "scripts", "sdkBuildManager.py");
    }

    /**
     * If -DsdkBuildManager.preCommit is set, copy the file to ${SDK_BUILD_DIR}/scripts/
     *
     * @throws OperatorException Any errors.
     */
    private void sdkBuildManagerPreCommit() throws OperatorException {
        final String sdkBuildManagerPreCommit = Flags.sdkBuildManagerPreCommit();
        if (sdkBuildManagerPreCommit != null) {
            final Path sdkBuildManager = Paths.get(sdkBuildManagerPreCommit);
            final String fileName = sdkBuildManager.getFileName().toString();
            final File toFile = Paths.get(SDK_BUILD_DIR.toString(), "scripts", fileName).toFile();
            try {
                LOGGER.warn("Replacing " + toFile + " with " + sdkBuildManager);
                FileUtils.copyFile(sdkBuildManager.toFile(), toFile, false);
            } catch (IOException e) {
                throw new OperatorException("Could not copy " + sdkBuildManagerPreCommit + " to ", e);
            }
        }
    }

    private void verifyPMFunctionality(final SdkType sdkType) throws OperatorException {
        SdkEnmCliOperator enmCli = new SdkEnmCliOperator();
        enmCli.createNetworkelementForPM(sdkType);
    }
}
