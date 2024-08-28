package com.ericsson.oss.mediation.sdk.sdktestware;

import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.oss.testware.hostconfigurator.HostConfigurator;
import com.ericsson.oss.testware.remoteexecution.operators.PemFileUtilities;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class HelmExecutorOperator {
    private final static Logger LOGGER = LoggerFactory.getLogger(HelmExecutorOperator.class);

    public static void template(final String chart) throws OperatorException {
        final LocalProcessOperator operator = new LocalProcessOperator();
        operator.execute(Arrays.asList("helm", "template", chart), false);
    }

    public static String templateRemote(final String helmPackage) throws OperatorException {
        final StringBuilder stdout = new StringBuilder();
        try (final RemoteAccessOperator remote = new RemoteAccessOperator()) {
            remote.execute("helm template " + helmPackage, 600, stdout, null);
        }
        return stdout.toString();
    }

    /**
     * Install a helm chart
     *
     * @param charts List of charts to install
     * @param values Value file to ise
     * @throws OperatorException Any errors
     */
    public void upgradeInstall(final List<String> charts, final String values) throws OperatorException {
        final String clusterName = PemFileUtilities.getHostnameOfDeployment();
        final Host director = HostConfigurator.getClientMachine();
        final String nameSpace = director.getNamespace();

        try (final RemoteAccessOperator remote = new RemoteAccessOperator()) {
            for (String chart : charts) {
                final File chartFile = new File(chart);
                final String releaseName = FilenameUtils.getBaseName(chartFile.getName());

                final String releaseCheckCmd = releaseCheckCommand(nameSpace, releaseName);
                final String upgradeInstallCommand = upgradeInstallCommand(nameSpace, releaseName, chart, values);

                final int releaseCheckResult = remote.execute(releaseCheckCmd, 5);
                if (releaseCheckResult == 0) {
                    final String msg = "A release called " + releaseName + " already exists in " + clusterName + ":" + nameSpace;
                    if(!Flags.skipDuplicateChartCheck()) {
                        LOGGER.error(msg);
                        AssertLog.fail(msg);
                    } else {
                        LOGGER.info(msg);
                    }
                }

                LOGGER.info("Installing " + chartFile.getName());
                final int installCmdResult = remote.execute(upgradeInstallCommand, Flags.helmUpgradeTimeout());
                if (installCmdResult != 0) {
                    AssertLog.fail("Chart installation failed: " + installCmdResult);
                }
            }
        }
    }

    private String releaseCheckCommand(final String nameSpace, final String releaseName) {
        return "helm -n " + nameSpace + " status " + releaseName;
    }

    private String upgradeInstallCommand(final String nameSpace, final String releaseName,
                                         final String chartPath, final String values) {
        final StringBuilder upgradeInstall = new StringBuilder("helm upgrade --install " + releaseName +
                " " + chartPath + " -f " + values + " -n " + nameSpace +
                " --debug --wait --timeout 10m");
        if (Flags.helmAtomic()) {
            upgradeInstall.append(" --atomic");
        }
        if (Flags.helmDryRun()) {
            upgradeInstall.append(" --dry-run");
        }
        for (String setValue : Flags.helmExtraValues()) {
            upgradeInstall.append(" --set ").append(setValue);
        }
        final String buildRepo = Flags.repositoryUrl();
        final String[] fdn = buildRepo.split("/", 2);
        upgradeInstall.append(" --set global.registry.url=").append(fdn[0]);
        if (fdn.length >= 2) {
            upgradeInstall.append(" --set imageCredentials.repoPath=").append(fdn[1]);
        }
        return upgradeInstall.toString();
    }
}
