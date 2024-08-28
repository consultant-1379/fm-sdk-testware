package com.ericsson.oss.mediation.sdk.fmsdktestware;

import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.oss.testware.hostconfigurator.HostConfigurator;
import com.ericsson.oss.testware.remoteexecution.operators.PemFileUtilities;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

public class HelmExecutorOperator {
    private final static Logger LOGGER = LoggerFactory.getLogger(HelmExecutorOperator.class);

    /**
     * Install a helm chart
     * @param valuesFile Value file to ise
     * @param charts List of charts to install
     * @throws OperatorException Any errors
     */
    public void installChart(final File valuesFile, final List<File> charts) throws OperatorException {
        final String clusterName = PemFileUtilities.getHostnameOfDeployment();
        final Host director = HostConfigurator.getClientMachine();
        final RemoteAccessOperator remote = new RemoteAccessOperator();

        try {
            for (File chart : charts) {
                final String nameSpace = director.getNamespace();

                final String releaseName = FilenameUtils.getBaseName(chart.getName());

                final String releaseCheckCmd = "helm -n " + nameSpace + " status " + releaseName;
                final int releaseCheckResult = remote.execute(releaseCheckCmd, 5);
                if (releaseCheckResult == 0) {
                    final String msg = "A release called " + releaseName + " already exists in " + clusterName + ":" + nameSpace;
                    LOGGER.error(msg);
                    AssertLog.fail(msg);
                }
                final StringBuilder installCmd = new StringBuilder("helm upgrade --install " + releaseName +
                        " " + chart.getPath() + " -f " + valuesFile.getPath() + " -n " + nameSpace +
                        " --debug --wait --timeout 10m");
                if (Flags.helmAtomic()) {
                    installCmd.append(" --atomic");
                }
                if (Flags.helmDryRun()) {
                    installCmd.append(" --dry-run");
                }
                for (String setValue : Flags.helmExtraValues()) {
                    installCmd.append(" --set ").append(setValue);
                }
                final String buildRepo = Flags.repositoryUrl() == null ? Flags.defaultRepositoryUrl() : Flags.repositoryUrl();
                final String[] fdn = buildRepo.split("/", 2);
                installCmd.append(" --set global.registry.url=").append(fdn[0]);
                if (fdn.length >= 2) {
                    installCmd.append(" --set imageCredentials.repoPath=").append(fdn[1]);
                }
                LOGGER.info("Installing " + chart.getName());
                final int installCmdResult = remote.execute(installCmd.toString(), 660);
                if (installCmdResult != 0) {
                    AssertLog.fail("Chart installation failed: " + installCmdResult);
                }
            }
        } finally {
            remote.disconnect();
        }
    }
}

