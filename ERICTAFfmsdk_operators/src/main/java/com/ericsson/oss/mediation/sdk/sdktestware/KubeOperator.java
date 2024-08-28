package com.ericsson.oss.mediation.sdk.sdktestware;

import org.apache.commons.configuration2.JSONConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;

import java.io.StringReader;
import java.util.*;

public class KubeOperator {

    private static final int TIMEOUT = 5;
    private static final String RANGE_SEPERATOR = " ";
    private static final String JSONPATH_METADATA_NAME = "'{range .items[*]}{.metadata.name}{\"" + RANGE_SEPERATOR + "\"}{end}{\"\\n\"}'";

    private static final String JSONPATH_POD_PHASE = "'{range .items[*]}{.metadata.name}{\"=\"}{.status.phase}{\"" + RANGE_SEPERATOR + "\"}{end}{\"\\n\"}'";
    private final RemoteAccessOperator remote;

    public KubeOperator() {
        remote = new RemoteAccessOperator();
    }

    private String executeRemote(final String command) throws OperatorException {
        return executeRemote(command, TIMEOUT);
    }

    private String executeRemote(final String command, final int timeoutSeconds) throws OperatorException {
        final StringBuilder stdout = new StringBuilder();
        final int exitCode = remote.execute(command, timeoutSeconds, stdout, null);
        if (exitCode != 0) {
            throw new OperatorException("Execute failed: " + command);
        }
        return stdout.toString().trim();
    }

    private List<String> getNamedItems(final Kinds itemKind, final String selector) throws OperatorException {
        final String command = getItemNameCommand(itemKind.name(), selector, remote.getDirector().getNamespace());
        final String stdout = executeRemote(command);
        return Arrays.asList(stdout.split(RANGE_SEPERATOR));
    }

    private String getKubeCtl(final String container) {
        final StringBuilder sb = new StringBuilder("kubectl -n ");
        sb.append(remote.getDirector().getNamespace());
        if (container != null) {
            sb.append(" -c ").append(container);
        }
        return sb.toString();
    }

    public void copy(final String filePath, final String pod, final String container, final String podLocation) throws OperatorException {
        final String command = getKubeCtl(container) + " cp " + filePath + " " + pod + ":" + podLocation;
        executeRemote(command);
    }

    public String exec(final String pod, final String container, final String command, final int timeoutSeconds) throws OperatorException {
        final String cmd = getKubeCtl(container) + " exec -it " + pod + " -- " + command;
        return executeRemote(cmd, timeoutSeconds);
    }

    /**
     * Get all Service items types in a namespace
     *
     * @return Lsit of Services in a namespace
     * @throws OperatorException Any errors
     */
    public List<String> svcs() throws OperatorException {
        return getNamedItems(Kinds.Service, null);
    }

    /**
     * Get Pods names that are selected by the Selector
     *
     * @param selector Selector value
     * @return List of pods matching the selector
     * @throws OperatorException Any errors
     */
    public List<String> pods(final String selector) throws OperatorException {
        return getNamedItems(Kinds.Pod, selector);
    }

    /**
     * Get Phase of Pods that are selected by the Selector
     *
     * @param selector Selector value
     * @return List of pods and their phases
     * @throws OperatorException Any errors
     */
    public Map<String, String> getPodPhases(final String selector) throws OperatorException {
        final String command = getPodPhaseCommand(selector, remote.getDirector().getNamespace());
        final String stdout = executeRemote(command);
        final Map<String, String> podPhases = new HashMap<>();
        for (String podPhase : stdout.split(RANGE_SEPERATOR)) {
            final String[] kvPair = podPhase.split("=", 2);
            podPhases.put(kvPair[0], kvPair[1]);
        }
        return podPhases;
    }

    /**
     * Get ReplicaSet names that are selected by the Selector
     *
     * @param selector Selector value
     * @return List of ReplicaSets matching the selector
     * @throws OperatorException Any errors
     */
    public List<String> replicasets(final String selector) throws OperatorException {
        return getNamedItems(Kinds.ReplicaSet, selector);
    }

    /**
     * Get the JSON data for an item
     *
     * @param kind The item kind
     * @param name The item name
     * @return JSON data
     * @throws OperatorException Any errors
     */
    public JSONConfiguration get(final Kinds kind, final String name) throws OperatorException {
        final String command = getGetNamedItemJsonCommand(kind.name(), name, remote.getDirector().getNamespace());
        final String stdout = executeRemote(command);
        final JSONConfiguration json = new JSONConfiguration();
        try {
            json.read(new StringReader(stdout));
        } catch (ConfigurationException e) {
            throw new OperatorException("Error converting kubectl output for YAML", e);
        }
        return json;
    }

    private String getItemNameCommand(final String type, final String selector, final String nameSpace) {
        final List<String> cmd = new ArrayList<>();
        cmd.add("kubectl");
        cmd.add("get");
        cmd.add(type);
        if (selector != null) {
            cmd.add("-l" + selector);
        }
        cmd.add("-n");
        cmd.add(nameSpace);
        cmd.add("-o=jsonpath=" + JSONPATH_METADATA_NAME);
        return String.join(" ", cmd);
    }

    private String getPodPhaseCommand(final String selector, final String nameSpace) {
        final List<String> cmd = new ArrayList<>();
        cmd.add("kubectl");
        cmd.add("get");
        cmd.add(Kinds.Pod.name());
        cmd.add("-l" + selector);
        cmd.add("-n");
        cmd.add(nameSpace);
        cmd.add("-o=jsonpath=" + JSONPATH_POD_PHASE);
        return String.join(" ", cmd);
    }

    private String getGetNamedItemJsonCommand(final String type, final String itemName, final String nameSpace) {
        final List<String> cmd = new ArrayList<>();
        cmd.add("kubectl");
        cmd.add("get");
        cmd.add(type);
        cmd.add("-n");
        cmd.add(nameSpace);
        cmd.add(itemName);
        cmd.add("-o=json");
        return String.join(" ", cmd);
    }

    enum Kinds {
        Service, Pod, ReplicaSet;
    }
}
