package com.ericsson.oss.mediation.sdk.fmsdktestware;

import com.ericsson.cifwk.taf.TafTestBase;
import com.ericsson.cifwk.taf.annotations.TestId;
import org.apache.commons.configuration2.JSONConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class FmSdkInstallTest extends TafTestBase {
    public static final String FMSDK_INSTALL = "FMSDK-GENERATE-INSTALL";
    private final static Logger LOGGER = LoggerFactory.getLogger(FmSdkInstallTest.class);
    private static final String REMOTE_TEMP_DIR = "/var/tmp/";
    private Path sdkPath;
    private Path sdkInputsPath;
    private Path sdkBuildMgrPath;

   // @TestId(id = FMSDK_INSTALL, title = "Generate custom FMSDK images and chart and install on a cENM env.")
    //@Test
    public void verifySdkInstall() {
        try {
            final List<File> remoteCharts = generateAndUploadCharts();
            final File valuesFile = generateAndUploadValues();
            if (!Flags.assertOnly()) {
                installCharts(valuesFile, remoteCharts);
            }
            verifyChartInstallation();
        } catch (AssertionError error) {
            AssertLog.fail("TestCase:" + FMSDK_INSTALL + " FAILED", error);
            throw error;
        } catch (Throwable error) {
            AssertLog.fail("TestCase:" + FMSDK_INSTALL + " FAILED", error);
        }
    }

    private void verifyChartInstallation() throws OperatorException {
        final KubeOperator kubectl = new KubeOperator();
        final List<String> services = kubectl.svcs();

        final File[] charts = sdkInputsPath.toFile().listFiles(File::isDirectory);
        AssertLog.assertNotNullOrEmpty(charts, "No input charts found in " + sdkInputsPath);
        //noinspection ConstantConditions
        for (File chart : charts) {
            final String svcName = chart.getName();
            AssertLog.assertContains(services, svcName);
            LOGGER.info("ASSERTION-PASSED: Expected Service '" + svcName + "' exists");
            final String selector = "app=" + svcName;

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
            LOGGER.info("ASSERTION-PASSED: Correct number of Pods found (" + expectedPodCount + ") for selector '" + selector + "' and ReplicaSet '" + rsInfo + "'");

            final Map<String, String> podPhases = kubectl.getPodPhases(selector);
            boolean phaseAssertOk = true;
            final String expectedPhase = "Running";
            for (final Map.Entry<String, String> podPhase : podPhases.entrySet()) {
                final String actualPhase = podPhase.getValue();
                final String podName = podPhase.getKey();

                if (actualPhase.equalsIgnoreCase(expectedPhase)) {
                    LOGGER.info("ASSERTION-PASSED: Pod '" + podName + "' in correct phase '" + actualPhase + "'");
                } else {
                    LOGGER.info("ASSERTION-FAILED: Pod '" + podName + "' not in the correct phase, expected:Running actual:" + actualPhase + "'");
                    phaseAssertOk = false;
                }
            }
            AssertLog.assertTrue(phaseAssertOk, "Not all Pods are in the '" + expectedPhase + "' phase");
            LOGGER.info("ASSERTION-PASSED: All Pods are in the correct '" + expectedPhase + "' phase");
        }
    }

    private List<File> generateAndUploadCharts() throws OperatorException {
        LOGGER.info("Preparing chart inputs");
        final File[] customCharts = prepareCharts();
        AssertLog.assertNotEquals(customCharts.length, 0, "No custom charts generated/found!");
        final List<File> charts = uploadCharts(customCharts);

        // TODO: Temporary step to install model rpms via kubectl, should be removed once model installation
        // can be done via an init container
        uploadModelPackages();

        return charts;
    }

    public void installCharts(final File valuesFile, final List<File> charts) throws OperatorException {

        // TODO: Temporary step to install model rpms via kubectl, should be removed once model installation
        // can be done via an init container
        installModelPackages();

        final HelmExecutorOperator helm = new HelmExecutorOperator();
        helm.installChart(valuesFile, charts);
    }


    private void installModelPackages() throws OperatorException {
        final KubeOperator kubectl = new KubeOperator();
        final String selector = "app=eric-enm-modeldeployservice";
        final List<String> mdsPods = kubectl.pods(selector);
        if (mdsPods.size() == 0) {
            throw new OperatorException("No Pods found for selector " + selector);
        }

        final String mdsPodName = mdsPods.get(0);
        final Map<String, File[]> modelPackages = getModelPackages();
        for (Map.Entry<String, File[]> entrySet : modelPackages.entrySet()) {
            final String chartName = entrySet.getKey();
            final String remotePath = "/var/tmp/" + chartName + "/models/";
            for (File rpm : entrySet.getValue()) {
                kubectl.copy(remotePath + rpm.getName(), mdsPodName, "/ericsson/customModelRpms/");
            }
        }

        //just log the existing reports for troubleshooting if needed,
        kubectl.exec(mdsPodName, "ls -ltr /etc/opt/ericsson/ERICmodeldeployment/data/report/", 5);
        kubectl.exec(mdsPodName, "bash -c \"deploy_rpms.sh allowUnsigned\"", 600);
    }

    private void uploadModelPackages() throws OperatorException {
        final Map<String, File[]> modelPackages = getModelPackages();
        try (final RemoteAccessOperator remote = new RemoteAccessOperator()) {
            for (Map.Entry<String, File[]> entrySet : modelPackages.entrySet()) {
                final File[] rpms = entrySet.getValue();
                final String chartName = entrySet.getKey();
                final String remoteDir = "/var/tmp/" + chartName + "/models/";
                remote.rm(remoteDir);
                for (File rpm : rpms) {
                    final String remoteFile = remoteDir + rpm.getName();
                    remote.put(rpm.getAbsolutePath(), remoteFile);
                }
            }
        }
    }

    private Map<String, File[]> getModelPackages() throws OperatorException {
        final Path inputs = FmSdkBuildManagerOperator.getSdkInputPathDir();
        final File[] customCharts = inputs.toFile().listFiles(File::isDirectory);
        if (customCharts == null || customCharts.length == 0) {
            throw new OperatorException("No custom charts found in " + inputs);
        }
        final Map<String, File[]> modelRpms = new HashMap<>();
        for (File customChart : customCharts) {
            final File jboss = new File(customChart, "models");
            if (jboss.exists()) {
                final File[] rpms = jboss.listFiles((dir, name) -> name.toLowerCase().endsWith(".rpm"));
                modelRpms.put(customChart.getName(), rpms);
            }
        }
        return modelRpms;
    }

    private File generateAndUploadValues() throws OperatorException {
        final HelmValuesOperator helm = new HelmValuesOperator();
        final File helmValues = helm.getHelmInstallValuesFile();
        final String remote = REMOTE_TEMP_DIR + helmValues.getName();

        try (final RemoteAccessOperator sftp = new RemoteAccessOperator()) {
            sftp.put(helmValues.getAbsolutePath(), remote);
            return new File(remote);
        }
    }

    private List<File> uploadCharts(final File[] charts) throws OperatorException {
        final List<File> remoteFiles = new ArrayList<>();
        try (final RemoteAccessOperator sftp = new RemoteAccessOperator()) {
            for (File chartFile : charts) {
                final String remote = REMOTE_TEMP_DIR + chartFile.getName();
                remoteFiles.add(new File(remote));
                sftp.put(chartFile.getAbsolutePath(), remote);
            }
            return remoteFiles;
        }
    }

    private File[] prepareCharts() throws OperatorException {
        final FmSdkArchetypesOperator mvn = new FmSdkArchetypesOperator();
        final Map<String, List<File>> packages = mvn.generateAndBuild();

        final FmSdkBuildManagerOperator buildManager = new FmSdkBuildManagerOperator();
        final Map<String, Path> sdkOptions = buildManager.setupSdkInputs(packages);

        sdkPath = sdkOptions.get("sdk-path");
        sdkInputsPath = sdkOptions.get("sdk-input-path");
        sdkBuildMgrPath = sdkOptions.get("sdk-build-manager");

        return buildManager.buildLoadImages(sdkPath, sdkInputsPath, sdkBuildMgrPath);
    }
}
