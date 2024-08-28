package com.ericsson.oss.mediation.sdk.fmsdktestware;

import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.cifwk.taf.data.Ports;
import com.ericsson.oss.testware.hostconfigurator.HostConfigurator;
import com.ericsson.oss.testware.remoteexecution.operators.PemFileUtilities;
import com.jcraft.jsch.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

public class RemoteAccessOperator implements AutoCloseable {
    private final static Logger LOGGER = LoggerFactory.getLogger(RemoteAccessOperator.class);
    private final JSch jsch = new JSch();
    private File clusterPem;
    private Session session;

    private ChannelSftp sftpChannel;

    private Host directorHost;

    /**
     * Close any open connection
     */
    public void disconnect() {
        session.disconnect();
        session = null;
    }

    /**
     * Get the director node info
     *
     * @return
     */
    Host getDirector() {
        if (directorHost == null) {
            directorHost = HostConfigurator.getClientMachine();
        }
        return directorHost;
    }

    /**
     * Get a PEM file that can be used for any ssh/sftp connections
     *
     * @param hostName
     * @return
     */
    public File getClusterPemFile(final String hostName) {
        if (clusterPem == null) {
            final String contents = PemFileUtilities.getPrivateKey(hostName);
            clusterPem = PemFileUtilities.writePrivateKeyToFile(hostName, contents);
        }
        return clusterPem;
    }

    /**
     * Open a connection to the director node
     *
     * @throws OperatorException Any errors
     */
    private void connect() throws OperatorException
    {
        if (session == null || !session.isConnected())
        {
            LOGGER.info("New director session -> " + getDirector().getIp() + ":" + getDirector().getPort(Ports.SSH));
            final Path knownHosts = Paths.get(Flags.userHome(), ".ssh", "known_hosts");

            final String clusterName = PemFileUtilities.getHostnameOfDeployment();
            final File pemFile = getClusterPemFile(clusterName);

            try
            {
                jsch.setKnownHosts(knownHosts.toString());
                jsch.addIdentity(pemFile.getAbsolutePath());
                session = jsch.getSession(getDirector().getUser(), getDirector().getIp(), getDirector().getPort(Ports.SSH));
                session.setConfig("StrictHostKeyChecking", "no");
                final String userName = getDirector().getUser();
                final String passWord = getDirector().getUser(userName).getPassword();
                if (passWord != null && passWord.length() > 0)
                {
                    session.setPassword(passWord);
                }
                session.connect();
            }
            catch (JSchException e)
            {
                throw new OperatorException(e);
            }
        }
    }

    private ChannelSftp connectSftp() throws OperatorException {
        if (sftpChannel == null || !sftpChannel.isConnected()) {
            try {
                this.sftpChannel = (ChannelSftp) session.openChannel("sftp");
                this.sftpChannel.connect();
            } catch (JSchException e) {
                throw new OperatorException(e);
            }
        }
        return this.sftpChannel;
    }

    /**
     * Execute a command on the director node
     *
     * @param command        Command to execute
     * @param timeoutSeconds Timeout
     * @return exit code of command
     * @throws OperatorException Any errors
     */
    public int execute(final String command, final int timeoutSeconds) throws OperatorException {
        return execute(command, timeoutSeconds, null, null);
    }

    /**
     * Execute a command on the director node
     *
     * @param command        Command to execute
     * @param timeoutSeconds Timeout
     * @param stdout         Buffer to store command output in
     * @param stderr         Buffer to store command error output in (null redirects to stdout)
     * @return exit code of command
     * @throws OperatorException Any errors
     */
    public int execute(final String command, final int timeoutSeconds, final StringBuilder stdout, final StringBuilder stderr) throws OperatorException {
        connect();
        try {
            final ChannelExec ssh = (ChannelExec) session.openChannel("exec");
            LOGGER.info("Executing command (timeout=" + timeoutSeconds + "seconds): " + command);
            ssh.setCommand(command);

            final StringBuilder stdoutLine = new StringBuilder();
            ssh.setOutputStream(new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    final char c = (char) b;
                    if (c == '\n') {
                        LOGGER.info(stdoutLine.toString());
                        if (stdout != null) {
                            stdout.append(stdoutLine);
                        }
                        stdoutLine.delete(0, stdoutLine.length());
                    } else {
                        stdoutLine.append(c);
                    }
                }
            });
            final StringBuilder stderrLine = new StringBuilder();
            ssh.setErrStream(new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    final char c = (char) b;
                    if (c == '\n') {
                        LOGGER.error(stderrLine.toString());
                        if (stderr != null) {
                            stderr.append(stdoutLine);
                        }
                        stderrLine.delete(0, stderrLine.length());
                    } else {
                        stderrLine.append(c);
                    }
                }
            });

            try {
                final long timeoutMilli = timeoutSeconds * 1000L;
                final long timeoutTime = System.currentTimeMillis() + timeoutMilli;
                final long sleepTime = 500;
                ssh.connect();
                while (!ssh.isClosed()) {
                    try {
                        //noinspection BusyWait
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException e) {/**/}
                    if (System.currentTimeMillis() >= timeoutTime) {
                        throw new OperatorException("Command execution timed out, " + timeoutMilli + "mSec has been exceeded");
                    }
                }
                if (stdoutLine.length() > 0) {
                    LOGGER.info(stdoutLine.toString());
                    if (stdout != null) {
                        stdout.append(stdoutLine);
                    }
                }
                if (stderrLine.length() > 0) {
                    LOGGER.error(stderrLine.toString());
                    if (stderr != null) {
                        stderr.append(stderrLine);
                    }
                }
                LOGGER.debug("Command exited: " + ssh.getExitStatus());
                return ssh.getExitStatus();
            } finally {
                ssh.disconnect();
                session.disconnect();
            }
        } catch (JSchException e) {
            throw new OperatorException(e);
        }
    }

    /**
     * Copy a local file to the director
     *
     * @param local  Local file path
     * @param remote Remote file path
     * @throws OperatorException Any errors
     */
    public void put(final String local, final String remote) throws OperatorException {
        connect();
        try {
            final ChannelSftp ftp = connectSftp();
            LOGGER.info("Uploading " + local + " to " + remote);
            final String dir = new File(remote).getParent();

            if (!exists(ftp, dir)) {
                final String seperator = "/";
                final StringBuilder mkPath = new StringBuilder(seperator);
                for (String dirName : dir.split(seperator)) {
                    mkPath.append(dirName);
                    if (!exists(ftp, mkPath.toString())) {
                        ftp.mkdir(mkPath.toString());
                    }
                    mkPath.append(seperator);
                }
            }
            ftp.put(local, remote);
        } catch (SftpException e) {
            throw new OperatorException(e);
        }
    }

    private boolean exists(final ChannelSftp sftp, final String path) throws SftpException {
        try {
            sftp.stat(path);
            return true;
        } catch (final SftpException error) {
            if (error.id == 2) {
                return false;
            } else {
                throw error;
            }
        }
    }

    public void rm(final String path) throws OperatorException {
        connect();
        execute("rm -rf " + path, 10);
    }

    @Override
    public void close() {
        disconnect();
    }
}
