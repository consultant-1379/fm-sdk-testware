package com.ericsson.oss.mediation.sdk.sdktestware;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.cifwk.taf.handlers.netsim.CommandOutput;
import com.ericsson.cifwk.taf.handlers.netsim.NetSimCommand;
import com.ericsson.cifwk.taf.handlers.netsim.NetSimCommandHandler;
import com.ericsson.cifwk.taf.handlers.netsim.NetSimResult;
import com.ericsson.cifwk.taf.handlers.netsim.NetSimSession;
import com.ericsson.cifwk.taf.handlers.netsim.commands.NetSimCommands;
import com.ericsson.cifwk.taf.tools.http.HttpResponse;
import com.ericsson.cifwk.taf.tools.http.HttpTool;
import com.ericsson.cifwk.taf.tools.http.HttpToolBuilder;
import com.ericsson.cifwk.taf.tools.http.RequestBuilder;
import com.ericsson.cifwk.taf.tools.http.constants.ContentType;
import com.ericsson.cifwk.taf.tools.http.constants.HttpStatus;
import com.ericsson.oss.mediation.sdk.pmsdk.subscription.Counter;
import com.ericsson.oss.mediation.sdk.pmsdk.subscription.Node;
import com.ericsson.oss.mediation.sdk.pmsdk.subscription.PmsdkStatisticalSubscription;
import com.ericsson.oss.testware.hostconfigurator.HostConfigurator;

public class SdkEnmCliOperator {
    private final static Logger LOGGER = LoggerFactory.getLogger(SdkEnmCliOperator.class);


    private static final Host HAPROXY = HostConfigurator.getApache();
    private final List<Host> netSimsList = HostConfigurator.getAllNetsimHosts();
    


    private static final String ID_TOKEN_1 = "IDToken1";
    private static final String ID_TOKEN_2 = "IDToken2";
    private static final String USER_LOGIN_URI = "/login";
    private static final String LOGOUT_URI = "/logout";
    private final static String SCRIPT_ENGINE_POST_URI = "/script-engine/services/command";
    private final static String SCRIPT_ENGINE_HEAD_URI = SCRIPT_ENGINE_POST_URI + "/status";
    private static final String REQUEST_ID_KEY = "request_id";
    private static final String RESPONSE_SIZE = "ResponseSize";
    private static final String COMMAND_DIR = "command";
    private static final String TOR_USER_ID = "TorUserID";
    private static final String DEF_DESTINATION_NAME="FM_SDK_TAF";
    private static final String PROTOCOL="netconf_https_http_prot";
    private static final String NOTIFICATION_TYPE="2";// Defaults to 2 for Trap type
    private static final String TRAP_PORT="162";// Defaults to 162 for Trap Port
    private static final String GET_PO_ID="persistentObject/fdn/NetworkElement=";
    private static final String STATISTICAL="STATISTICAL";
    private static final String SUBSCRIPTION_NAME="PM_TAF";
    private static final String ADMINISTRATIONSTATE="INACTIVE";
    private final static String CREATE_SUBSCRIPTION = "/pm-service/rest/subscription";
    private final static String GET_SUBSCRIPTION = "/pm-service/rest/subscription/findByType?Type=STATISTICAL";
    private final static String ACT_SUBSCRIPTION = "pm-service/rest/subscription/STATISTICAL/activate?Name=PM_TAF";
    private final static String ADMINISTRATOR = "administrator";
    private final static String NODE_TYPE = "NODE_TYPE";
    private final static String PM_FUNCTION = "ON";
    private final static String CLASS = "statistical";
    private final static String USERTYPE = "USER_DEF";
    private final static String TASKSTATUS = "OK";
    private final static String ROP = "ONE_MIN";//FIFTEEN_MIN
    private final static String CHECK_FILE = "file/v1/files?filter=dataType==PM_STATISTICAL";
    private final static String DEACTIVATE_SUBSCRIPTION = "pm-service/rest/subscription/{ID}/deactivate";
    private final static String DELETE_SUBSCRIPTION="pm-service/rest/subscription/{ID}";




    private HttpTool httpTool;


    public void verifyModels(final SdkType sdkType) throws OperatorException {
        try {
            final Map<String, Map<String, Object>> data = SdkType.loadArchetypes(sdkType);
            final String sdkNeType = data.get("node-model-common-archetype").get("nodeType").toString();

            login();
            LOGGER.info("Asserting " + sdkType + " model '" + sdkNeType + "' exists.");
            final boolean doesModelExist = modelExists(sdkNeType);
            AssertLog.assertTrue(doesModelExist, "Model '" + sdkNeType + "' not found!");
            LOGGER.info("Model " + sdkNeType + " found.");
        } finally {
            logout();
        }
    }

    public void login() throws OperatorException {
        final String userName = "Administrator";
        final String passWord = "TestPassw0rd";

        final HttpResponse response = getHttpTool().request().
                body(ID_TOKEN_1, userName).
                body(ID_TOKEN_2, passWord).
                post(USER_LOGIN_URI);

        LOGGER.info("ENM login {} -> {}", HAPROXY.getIp(), response.getBody());
        if (response.getResponseCode().getCode() == HttpStatus.OK.getCode()) {
            getHttpTool().addCookie(TOR_USER_ID, userName);
        } else {
            throw new OperatorException("Failed to log in to " + HAPROXY.getIp() + " [" +
                    response.getResponseCode().getCode() + "] " + response.getBody());
        }
    }

    public void logout() {
        final HttpResponse response = getHttpTool().request().get(LOGOUT_URI);
        if (response.getResponseCode().getCode() == HttpStatus.OK.getCode()) {
            LOGGER.info("ENM logout {} -> {}", HAPROXY.getIp(), HttpStatus.OK);
        } else {
            LOGGER.error(response.getStatusLine());
        }
        getHttpTool().clearCookies();
    }

    private HttpTool getHttpTool() {
        if (httpTool == null) {
            httpTool = HttpToolBuilder.newBuilder(HAPROXY.getIp()).
                    followRedirect(true).
                    useHttpsIfProvided(true).
                    withHttpsPort(HAPROXY.getHttpsPort()).
                    withHttpPort(HAPROXY.getHttpsPort()).
                    trustSslCertificates(true).
                    build();
        }
        return httpTool;
    }

    private boolean modelExists(final String modelName) throws OperatorException {
        final String requestId = sendCommand("cmedit describe --netype " + modelName);
        final String outputId = waitCommandCompletion(requestId);
        final String neTypeData = getCommandJsonOutput(requestId, outputId);

        final ObjectMapper mapper = new ObjectMapper();
        try {
            final JsonNode result = mapper.readTree(neTypeData);

            final Iterator<JsonNode> elements = result.get("responseDto").get("elements").getElements();
            while (elements.hasNext()) {
                final JsonNode element = elements.next();
                final String value = element.get("value").getTextValue();
                if(value==null)
                {
                    continue;
                }
                if (value.contains("Error 1023")) {
                    return false;
                }
            }
        } catch (final IOException e) {
            throw new OperatorException("Error while extracting Response", e);
        }
        return true;
    }

    private String getCommandJsonOutput(final String requestId, final String outputId) {
        final RequestBuilder getRequestBuilder = getHttpTool().request().
                header("Accept", "application/json").
                header(REQUEST_ID_KEY, requestId);
        final HttpResponse getResponse = getRequestBuilder.get(SCRIPT_ENGINE_POST_URI + "/output/0/" + outputId);
        return getResponse.getBody();
    }

    private String sendCommand(final String command) throws OperatorException {
        final RequestBuilder requestBuilder = getHttpTool().request();

        requestBuilder.contentType(ContentType.MULTIPART_FORM_DATA).body(COMMAND_DIR, command);

        LOGGER.info("Sending CM Command {} to {}/{}", command, httpTool.getBaseUrl(), SCRIPT_ENGINE_POST_URI);
        final HttpResponse response = requestBuilder.post(SCRIPT_ENGINE_POST_URI);
        final HttpStatus responseCode = response.getResponseCode();
        if (responseCode != HttpStatus.OK && responseCode != HttpStatus.CREATED) {
            throw new OperatorException("Invalid HTTP status: " + response.getStatusLine());
        }

        return response.getHeaders().get(REQUEST_ID_KEY);
    }

    private String waitCommandCompletion(final String requestId) throws OperatorException {
        final HttpResponse pollResponse = poll(requestId);
        return pollResponse.getHeaders().get(RESPONSE_SIZE);
    }

    private HttpResponse poll(final String requestId) throws OperatorException {
        int nullResponseCounter = 200;
        HttpResponse response;
        do {
            response = getHttpTool().request().header(REQUEST_ID_KEY, requestId).head(SCRIPT_ENGINE_HEAD_URI);
            if (response.getResponseCode() != HttpStatus.OK) {
                throw new OperatorException("Polling failed: Response code " + response.getResponseCode());
            }
            nullResponseCounter--;
        } while (!responseFinished(response) && nullResponseCounter > 0);
        if (nullResponseCounter == 0) {
            throw new OperatorException("No poll completion response for " + requestId);
        }
        return response;
    }

    private boolean responseFinished(final HttpResponse response) {
        final Map<String, String> headers = response.getHeaders();
        String commandStatus = null;
        for (final String header : headers.keySet()) {
            if ("CommandStatus".equalsIgnoreCase(header)) {
                commandStatus = headers.get(header);
                break;
            }
        }
        return "COMPLETE".equals(commandStatus);
    }
    
    public void createNE(String neName, final SdkType sdkType, String ip, String simulation) throws OperatorException
    {
        final Map<String, Map<String, Object>> data = SdkType.loadArchetypes(sdkType);
        final String sdkNeType = data.get("fm-transformation-archetype").get("neTypeUpper").toString();
        final String neVersion = data.get("fm-transformation-archetype").get("NEVersion").toString();
        try
        {
            login();
            clearNe(neName);
            createsubnetwork(neName);
            createnetworkElement(neName, sdkNeType);
            setupConnectivityInfo(neName, neVersion, ip);
            setupCredentials(neName);
            setupHeartBeat(neName);
            setupfmsupervision(neName);
            sendAlarm(neName, simulation);
            /* wait for the alarm to send */
            try
            {
                Thread.currentThread().sleep(60000);
            }
            catch (InterruptedException e)
            {
                throw new OperatorException("Error in createNE", e);
            }
            verifyFAlarmSent(neName);
        }
        finally
        {
            clearNe(neName);
            logout();

        }
    }

    private void clearNe(String neName) throws OperatorException
    {

        deactivate(neName, "CmNodeHeartbeatSupervision");
        deactivate(neName, "InventorySupervision");
        deactivate(neName, "FmAlarmSupervision");
        deactivate(neName, "CmFunction");
        deleteNetAndSubNetwork(neName, "NetworkElement");
        deleteNetAndSubNetwork(neName, "SubNetwork");
    }

    private void deactivate(String neName, String superVision) throws OperatorException
    {
        final String requestId;
        if (superVision.equalsIgnoreCase("CmFunction"))
        {
            requestId = sendCommand("cmedit action NetworkElement=" + neName + ",CmFunction=1 deleteNrmDataFromEnm");

        }
        else
        {
            requestId = sendCommand("cmedit set NetworkElement=" + neName + "," + superVision + "=1 active=false");
        }
        final String outputId = waitCommandCompletion(requestId);
        final String neTypeData = getCommandJsonOutput(requestId, outputId);
        String response = parseNeresult(neTypeData);
        if (response.contains("Error"))
        {
            LOGGER.error("Error in deactivating  :" + response);
            throw new OperatorException("Error in deactivating  :" + superVision + ":" + requestId + " Error:" + response);
        }
    }

    private void deleteNetAndSubNetwork(String neName, String networkOrSubnetwork) throws OperatorException
    {
        final String requestId = sendCommand("cmedit delete " + networkOrSubnetwork + "=" + neName + " -ALL --force");
        final String outputId = waitCommandCompletion(requestId);
        final String neTypeData = getCommandJsonOutput(requestId, outputId);
        String response = parseNeresult(neTypeData);
        if (response.contains("Error"))
        {
            LOGGER.error("Error in deactivating network or subnetwork :" + response);
            throw new OperatorException("Error in deactivating  :" + networkOrSubnetwork + ":" + requestId);
        }
    }

    private void createsubnetwork(String neName) throws OperatorException
    {
        final String requestId = sendCommand("cmedit create SubNetwork=" + neName + " SubNetworkId=" + neName + " -namespace=OSS_TOP -version=3.0.0");
        final String outputId = waitCommandCompletion(requestId);
        final String neTypeData = getCommandJsonOutput(requestId, outputId);
        String response = parseNeresult(neTypeData);
        if (response.contains("Error"))
        {
            LOGGER.error("Error in creating subnetwork : " + response);
            throw new OperatorException("Error in creating subnetwork  :" + neName + ":" + requestId + " Error:" + response);
        }
    }

    private void createnetworkElement(String neName, String sdkNeType) throws OperatorException
    {
        final String requestId = sendCommand("cmedit create NetworkElement=" + neName + " networkElementId=" + neName + ",neType=" + sdkNeType + ",ossPrefix=\"SubNetwork=" + neName + ",MeContext=" + neName + "\" -namespace=OSS_NE_DEF -version=2.0.0");
        final String outputId = waitCommandCompletion(requestId);
        final String neTypeData = getCommandJsonOutput(requestId, outputId);
        String response = parseNeresult(neTypeData);
        if (response.contains("Error"))
        {
            LOGGER.error("Error in creating networkElement : " + response);
            throw new OperatorException("Error in creating networkElement  :" + neName + ":" + requestId + " Error:" + response);
        }
    }

    private void setupConnectivityInfo(String neName, String neVersion, String ip) throws OperatorException
    {
        // In Address shuld be replaced in place of ip address
        final String requestId = sendCommand("cmedit create NetworkElement=" + neName + ",GenericSnmpNodeConnectivityInformation=1 GenericSnmpNodeConnectivityInformationId=\"1\", ipAddress=" + ip
                + ", snmpVersion=SNMP_V2C, snmpAgentPort=161, snmpWriteCommunity=\"public\", snmpReadCommunity=\"public\" -ns GEN_SNMP_MED --version " + neVersion);
        final String outputId = waitCommandCompletion(requestId);
        final String neTypeData = getCommandJsonOutput(requestId, outputId);
        String response = parseNeresult(neTypeData);
        if (response.contains("Error"))
        {
            LOGGER.error("Error in creating ConnectivityInfo : " + response);
            throw new OperatorException("Error in creating ConnectivityInfo  :" + neName + ":" + requestId + " Error:" + response);
        }
    }

    private void setupCredentials(String neName) throws OperatorException
    {
        // In Address shuld be replaced in place of ip address
        final String requestId = sendCommand("secadm credentials create --secureusername netsim --secureuserpassword netsim -n " + neName + "");
        final String outputId = waitCommandCompletion(requestId);
        final String neTypeData = getCommandJsonOutput(requestId, outputId);
        String response = parseNeresult(neTypeData);
        if (response.contains("Error"))
        {
            LOGGER.error("Error in setupCredentials : " + response);
            throw new OperatorException("Error in setupCredentials  :" + neName + ":" + requestId + " Error:" + response);
        }
    }

    private void setupHeartBeat(String neName) throws OperatorException
    {
        // In Address shuld be replaced in place of ip address
        final String requestId = sendCommand("cmedit set NetworkElement=" + neName + ",CmNodeHeartbeatSupervision=1 active=true");
        final String outputId = waitCommandCompletion(requestId);
        final String neTypeData = getCommandJsonOutput(requestId, outputId);
        String response = parseNeresult(neTypeData);
        if (response.contains("Error"))
        {
            LOGGER.error("Error in setting up heartbeat  : " + response);
            throw new OperatorException("Error in setting up heartbeat  :" + neName + ":" + requestId + " Error:" + response);
        }
    }

    private void setupfmsupervision(String neName) throws OperatorException
    {
        // In Address shuld be replaced in place of ip address
        final String requestId = sendCommand("cmedit set NetworkElement=" + neName + ",FmAlarmSupervision=1 active=true");
        final String outputId = waitCommandCompletion(requestId);
        final String neTypeData = getCommandJsonOutput(requestId, outputId);
        String response = parseNeresult(neTypeData);
        if (response.contains("Error"))
        {
            throw new OperatorException("Error in creating NetworkElement :" + requestId);
        }
    }

    private String parseNeresult(String neTypeData) throws OperatorException
    {
        // always the result comes on the last element
        // example :
        /*
         * { "nonCachableDtos": [], "responseDto": { "dtoType": "ResponseDto", "elements": [ { "dtoType": "line", "value":
         * "FDN : SubNetwork=LTE26dg2ERBS00001" }, { "dtoType": "line", "value": "SubNetworkId : LTE26dg2ERBS00001" }, { "dtoType": "line", "value":
         * "1 instance(s) updated" } ] }
         * 
         * 
         * Failure:
         * 
         * 
         * { "nonCachableDtos": [], "responseDto": { "dtoType": "ResponseDto", "elements": [ { "dtoType": "line", "value":
         * "FDN : SubNetwork=LTE26dg2ERBS00001" }, { "dtoType": "line", "value": "SubNetworkId : LTE26dg2ERBS00001" }, { "dtoType": "line", "value":
         * "Error 1009 : An incorrect value (NEWNETYPE) has been encountered for neType possible value(s" } ] } }
         * 
         * 
         * 
         */
        final ObjectMapper mapper = new ObjectMapper();
        String response = "";
        try
        {
            final JsonNode result = mapper.readTree(neTypeData);

            final Iterator<JsonNode> elements = result.get("responseDto").get("elements").getElements();

            while (elements.hasNext())
            {
                final JsonNode element = elements.next();
                final String value = element.get("value").getTextValue();
                if (value != null)
                {
                    response = value;
                }
            }
            return response;
        }
        catch (final IOException e)
        {
            throw new OperatorException("Error while extracting Response", e);
        }
    }

    public String[] configureNetsims(String fmVipAddress) throws OperatorException
    {
        List<NetSimCommand> commandsToExecute = new ArrayList<NetSimCommand>();
        List<String> dgTwoSimuations = new ArrayList<>();
        String netsimDetails[] = new String[3];

        // Getting a random host
        final Host netsimHost = netSimsList.get(0);
        LOGGER.info("Using NetSim host " + netsimHost);
        netsimHost.setUser("netsim");
        netsimHost.setPass("netsim");
        NetSimSession handler = NetSimCommandHandler.getSession(netsimHost);
        commandsToExecute.add(NetSimCommands.showSimulations());
        NetSimResult simesrsult = handler.exec(commandsToExecute);
        CommandOutput[] result = simesrsult.getOutput();
        LOGGER.info("show simulations --> {} : ", result[0].asColumns());
        List<Map<String, String>> simulations = result[0].asColumns();
        for (Map<String, String> simulationEntry : simulations)
        {
            for (Map.Entry<String, String> entry : simulationEntry.entrySet())
            {
                String value = simulationEntry.get(entry.getKey());
                if (value.contains("DG2"))
                {
                    LOGGER.info("Dg2 simumations --> {} : ", value);
                    dgTwoSimuations.add(value);
                }
            }
        }
        /* use any on of DG2 simulation as of now using last index */
        LOGGER.info("DG2 simulation : " + dgTwoSimuations.get(dgTwoSimuations.size() - 1));
        commandsToExecute.add(NetSimCommands.open(dgTwoSimuations.get(dgTwoSimuations.size() - 1)));
        handler.exec(commandsToExecute);

        /* Create default destination with ip and port */
        createDefaultDestination(commandsToExecute, handler, fmVipAddress);

        commandsToExecute.add(NetSimCommands.showSimnes());
        NetSimResult simnessResult = handler.exec(commandsToExecute);
        LOGGER.info("show simness : " + simnessResult.getRawOutput());
        CommandOutput[] simNessCommandResult = simnessResult.getOutput();
        List<Map<String, String>> simNessList = simNessCommandResult[simNessCommandResult.length-1].asColumns();

        // taking the first value from the list
        /*
         *
         * example : [{Default dest.=public v3+v2+v1 .128.0.0.193.1.30.16.149.215 mediation authpass privpass none none  [TLS], In
         * Address=30.16.149.215 161, NE Name=LTE26dg2ERBS00001, Server=ieatnetsimv18082, Type=LTE MSRBS-V2 19-Q3-V10}, {Default dest.=public v3+v2+v1
         * .128.0.0.193.1.30.16.149.218 mediation authpass privpass none none  [TLS], In Address=30.16.149.218 161, NE Name=LTE26dg2ERBS00002,
         * Server=ieatnetsimv18082, Type=LTE MSRBS-V2 19-Q3-V10}, {Default dest.=public v3+v2+v1 .128.0.0.193.1.30.16.149.232 mediation authpass
         * privpass none none  [TLS] 10.150.43.37:162, In Address=30.16.149.232 161, NE Name=LTE26dg2ERBS00003, Server=ieatnetsimv18082, Type=LTE
         * MSRBS-V2 19-Q3-V10}, {Default dest.=public v3+v2+v1 .128.0.0.193.1.30.16.149.233 mediation authpass privpass none none  [TLS], In
         * Address=30.16.149.233 161, NE Name=LTE26dg2ERBS00004, Server=ieatnetsimv18082, Type=LTE MSRBS-V2 19-Q3-V10}, {Default dest.=public v3+v2+v1
         * .128.0.0.193.1.30.16.149.237 mediation authpass privpass none none  [TLS], In Address=30.16.149.237 161, NE Name=LTE26dg2ERBS00005,
         * Server=ieatnetsimv18082, Type=LTE MSRBS-V2 19-Q3-V10}]
         * 
         */

        final String neName = Flags.getFmNeSimName();
        LOGGER.info("Using netsim node " + neName);
        String neIpAddress = null;
        for(Map<String, String> sim : simNessList){
            if(sim.get("NE Name").equals(neName)){
                neIpAddress = sim.get("In Address").split("\\s+")[0];
                break;
            }
        }
        if(neIpAddress == null){
            throw new OperatorException("Could not find a sim for node " + neName);
        }

        LOGGER.info(neName + " -> " + neIpAddress);

        netsimDetails[0] = neName;
        netsimDetails[1] = neIpAddress;
        netsimDetails[2] = dgTwoSimuations.get(dgTwoSimuations.size() - 1);

        // have to use the next NE if it fails as of now using the first one
        commandsToExecute.add(NetSimCommands.select(neName));
        commandsToExecute.add(NetSimCommands.stop());

        commandsToExecute.add(NetSimCommands.setExternal(DEF_DESTINATION_NAME));
        commandsToExecute.add(NetSimCommands.setSave());

        commandsToExecute.add(NetSimCommands.start());
        handler.exec(commandsToExecute);
        LOGGER.info("Ne is Started Fine ready to send Alarm");
        return netsimDetails;

        // assert the result

    }

    private void sendAlarm(String neName, String simulation)
    {
        final Host netsimHost = netSimsList.get(0);
        LOGGER.info("Using NetSim host " + netsimHost);
        netsimHost.setUser("netsim");
        netsimHost.setPass("netsim");
        NetSimSession handler = NetSimCommandHandler.getSession(netsimHost);
        List<NetSimCommand> commandsToExecute = new ArrayList<NetSimCommand>();
        commandsToExecute.add(NetSimCommands.open(simulation));
        commandsToExecute.add(NetSimCommands.select(neName));
        commandsToExecute.add(NetSimCommands.sendalarm().setSpecificProblem("Sent Alarm from TAF"));
        NetSimResult alarmResult = handler.exec(commandsToExecute);
        LOGGER.info("Alarm Result : " + alarmResult);
    }

    private void verifyFAlarmSent(String neName) throws OperatorException
    {
        // In Address shuld be replaced in place of ip address
        final String requestId = sendCommand("alarm get " + neName + " -sp \"Sent Alarm from TAF\"");
        final String outputId = waitCommandCompletion(requestId);
        final String neTypeData = getCommandJsonOutput(requestId, outputId);
        String response = parseAlarmResult(neTypeData);
        LOGGER.info("response from verify Alarm : " + response);
        Assert.assertEquals(response, "Total number of alarms fetched for the given query is :1");
    }

    private void createDefaultDestination(List<NetSimCommand> commandsToExecute, NetSimSession handler, String fmVipAddress)
    {
        // configuration for default destination
        commandsToExecute.add(NetSimCommands.selectConfiguration());
        commandsToExecute.add(NetSimCommands.configAddExternal(DEF_DESTINATION_NAME, PROTOCOL));
        commandsToExecute.add(NetSimCommands.configExternalServers(DEF_DESTINATION_NAME, netSimsList.get(0).getHostname()));
        commandsToExecute.add(NetSimCommands.configExternalAddress(DEF_DESTINATION_NAME, fmVipAddress, TRAP_PORT, NOTIFICATION_TYPE));// need to check
                                                                                                                                      // ip from
        commandsToExecute.add(NetSimCommands.configSave());

        NetSimResult defDestinationResult = handler.exec(commandsToExecute);
        LOGGER.info("result of def destination : " + defDestinationResult.getRawOutput());
    }

    private String parseAlarmResult(String neTypeData) throws OperatorException
    {
        final ObjectMapper mapper = new ObjectMapper();
        String response = "";
        try
        {
            final JsonNode result = mapper.readTree(neTypeData);

            final Iterator<JsonNode> elements = result.get("responseDto").get("elements").getElements();

            while (elements.hasNext())
            {
                final JsonNode element = elements.next();
                final String dtoType = element.get("dtoType").getTextValue();
                if (dtoType != null && dtoType.equalsIgnoreCase("line"))
                {
                    String toAssert = element.get("value").getTextValue();
                    if (toAssert != null && toAssert.startsWith("Total number"))
                    {
                        response = toAssert;
                    }
                }
            }
            return response;
        }
        catch (final IOException e)
        {
            throw new OperatorException("Error while extracting Response", e);
        }
    }

    public void createNetworkelementForPM(SdkType sdkType) throws OperatorException
    {
        String networkElementName="";
        try
        {
            final Map<String, Map<String, Object>> data = SdkType.loadArchetypes(sdkType);
            final String sdkNeType = data.get("pm-mediation-snmp-archetype").get("nodeType").toString();
            String[] netsimsDetails = fetchNetsimsDetails();
            login();
            networkElementName=netsimsDetails[0];
            deleteNetAndSubNetwork(networkElementName,"NetworkElement");
            createnetworkElementPM(netsimsDetails[0], sdkNeType);
            setupConnectivityInfoPM(netsimsDetails[0], "1.0.0", netsimsDetails[1]);
            enablePmfunction(netsimsDetails[0]);
            createAndActivateSubscription(netsimsDetails[0], sdkNeType);
            LOGGER.info("Subscription is created for one min--> Taf Test will wait for a minute to assert file");

            Thread.currentThread().sleep(330000);
            String isFileCreated = genericGet(CHECK_FILE);
            Map map = getObjectMapper().readValue(isFileCreated, Map.class);
            if (map.get("files") == null)
            {
                throw new OperatorException("Error in assert file no files found-->");
            }
            String fileout = map.get("files").toString();
            LOGGER.info("file output : "+isFileCreated);
            Assert.assertTrue(fileout.contains(netsimsDetails[0]));

        }
        catch (InterruptedException e)
        {
            LOGGER.error("Error in assert file ");
            throw new OperatorException("Error in assert file ");
        }
        catch (IOException e)
        {
            LOGGER.error("Error in assert file ");
            throw new OperatorException("Error in assert file ");
        }
        finally
        {
            deactivateSubscription();
            deleteNetAndSubNetwork(networkElementName,"NetworkElement");
            logout();
        }
    }

    private void createnetworkElementPM(String neName, String sdkNeType) throws OperatorException
    {
        final String requestId = sendCommand("cmedit create NetworkElement=" + neName + " networkElementId=" + neName + " ,neType=" + sdkNeType + " -ns=OSS_NE_DEF -v=2.0.0");
        final String outputId = waitCommandCompletion(requestId);
        final String neTypeData = getCommandJsonOutput(requestId, outputId);
        String response = parseNeresult(neTypeData);
        if (response.contains("Error"))
        {
            LOGGER.error("Error in creating networkElement : " + response);
            throw new OperatorException("Error in creating networkElement  :" + neName + ":" + requestId + " Error:" + response);
        }
    }

    private void setupConnectivityInfoPM(String neName, String neVersion, String ip) throws OperatorException
    {
        final String requestId = sendCommand("cmedit create NetworkElement=" + neName + ",GenericSnmpNodeConnectivityInformation=1 GenericSnmpNodeConnectivityInformationId=\"1\", ipAddress=" + ip
                + ", snmpVersion=SNMP_V2C, snmpWriteCommunity=\"public\", snmpReadCommunity=\"public\" -ns=GEN_SNMP_MED --version " + neVersion);
        final String outputId = waitCommandCompletion(requestId);
        final String neTypeData = getCommandJsonOutput(requestId, outputId);
        String response = parseNeresult(neTypeData);
        if (response.contains("Error"))
        {
            LOGGER.error("Error in creating ConnectivityInfo : " + response);
            throw new OperatorException("Error in creating ConnectivityInfo  :" + neName + ":" + requestId + " Error:" + response);
        }
    }

    private void enablePmfunction(String neName) throws OperatorException
    {
        final String requestId = sendCommand("cmedit set NetworkElement=" + neName + ",PmFunction=1 pmEnabled=true --force");
        final String outputId = waitCommandCompletion(requestId);
        final String neTypeData = getCommandJsonOutput(requestId, outputId);
        String response = parseNeresult(neTypeData);
        if (response.contains("Error"))
        {
            LOGGER.error("Error in creating networkElement : " + response);
            throw new OperatorException("Error in creating networkElement  :" + neName + ":" + requestId + " Error:" + response);
        }
    }

    private void deactivateSubscription() throws OperatorException
    {
        String getSubstatus;
        try
        {
            getSubstatus = genericGet(GET_SUBSCRIPTION);
            LOGGER.info("output from get API : " + getSubstatus);
            String[] subscriptionData = getSubscriptionData(getSubstatus);
            String persistenceTime = subscriptionData[0];
            String id = subscriptionData[1];
            String activatepaylod = "{\"persistenceTime\":" + persistenceTime + "}";
            LOGGER.info("de-activate payload : "+activatepaylod+"id: "+id);
            String deactivateResponse = deactivateSubsciption(activatepaylod, id);
            LOGGER.info("output from  deactivateResponse : " + deactivateResponse);
            deleteSubscription(id);
        }
        catch (OperatorException e)
        {
            LOGGER.error("Error in deactivateSubscription : ");
            throw new OperatorException("Error in deactivateSubscription", e);
        }
        catch (IOException e)
        {
            LOGGER.error("Error in deactivateSubscription : ");
            throw new OperatorException("Error in deactivateSubscription", e);
        }

    }

    private void createAndActivateSubscription(String networkElementName, String neType) throws OperatorException
    {
        try
        {
            String pmsdkSubscriptionJson = createSubscription(networkElementName, neType);
            sendCommandForPm(pmsdkSubscriptionJson);
            // give sometime for the subscription to save
            Thread.currentThread().sleep(5000);
            String getSubstatus = genericGet(GET_SUBSCRIPTION);
            LOGGER.info("output from get API : " + getSubstatus);
            String[] persistenceTime = getSubscriptionData(getSubstatus);
            String activatepaylod = "{\"persistenceTime\":" + persistenceTime[0] + "}";
            String activateresponse = sendCommandForPmactivate(activatepaylod);
            LOGGER.info("output from  activateresponse : " + activateresponse);
        }
        catch (InterruptedException e)
        {
            LOGGER.error("Error in createAndActivateSubscription : ");
            throw new OperatorException("Error in createAndActivateSubscription", e);
        }
        catch (IOException e)
        {
            LOGGER.error("Error in createAndActivateSubscription : ");
            throw new OperatorException("Error in createAndActivateSubscription", e);
        }

    }

    private String[] getSubscriptionData(String subscriptionData) throws IOException
    {
        String subData[] = new String[2];
        PmsdkStatisticalSubscription[] pmsdkresonse = getObjectMapper().readValue(subscriptionData, PmsdkStatisticalSubscription[].class);
        for (PmsdkStatisticalSubscription pmsdksubs : pmsdkresonse)
        {
            if (pmsdksubs.getName().equalsIgnoreCase(SUBSCRIPTION_NAME))
            {
                subData[0] = pmsdksubs.getPersistenceTime();
                subData[1] = (String) pmsdksubs.getId();
                break;
            }
        }
        return subData;

    }

    private String createSubscription(String networkElementName, String neType) throws OperatorException
    {
        PmsdkStatisticalSubscription pmsdkSubscription = new PmsdkStatisticalSubscription();
        String subscription = null;
        try
        {
            final Map<String, Object> map = getNeDetails(networkElementName);
            Integer poId = (Integer) map.get("poId");
            String fdn = (String) map.get("fdn");
            String neVersion = (String) map.get("neVersion");

            pmsdkSubscription.setType(STATISTICAL);
            pmsdkSubscription.setName(SUBSCRIPTION_NAME);
            pmsdkSubscription.setDescription(SUBSCRIPTION_NAME);
            pmsdkSubscription.setAdministrationState(ADMINISTRATIONSTATE);
            pmsdkSubscription.setOwner(ADMINISTRATOR);
            pmsdkSubscription.setNodeFilter(NODE_TYPE);
            ArrayList<String> selectedNeTypes = new ArrayList<String>();
            selectedNeTypes.add(neType);
            pmsdkSubscription.setSelectedNeTypes(selectedNeTypes);

            ArrayList<Node> nodes = new ArrayList<Node>();
            Node node = new Node();
            node.setFdn(fdn);
            node.setId(poId.toString());
            node.setPmFunction(PM_FUNCTION);
            node.setOssPrefix("MeContext=" + networkElementName);
            node.setNeType(neType);
            node.setOssModelIdentity(neVersion);
            nodes.add(node);
            pmsdkSubscription.setNodes(nodes);

            pmsdkSubscription.setNumberOfNodes(1);
            pmsdkSubscription.setClasss(CLASS);
            pmsdkSubscription.setUserType(USERTYPE);
            pmsdkSubscription.setTaskStatus(TASKSTATUS);
            pmsdkSubscription.setRop(ROP);
            pmsdkSubscription.setPnpEnabled(false);
            pmsdkSubscription.setFilterOnManagedElement(false);
            pmsdkSubscription.setFilterOnManagedFunction(false);
            pmsdkSubscription.setCbs(false);

            ObjectMapper objectMapper = getObjectMapper();

            InputStream in = getClass().getResourceAsStream("/defaultcounter.json");
            Counter[] counter = (Counter[]) objectMapper.readValue(in, Counter[].class);
            List<Counter> counterList = Arrays.asList(counter);
            ArrayList<Counter> listOfCounters = new ArrayList<Counter>(counterList);
            pmsdkSubscription.setCounters(listOfCounters);
            subscription = objectMapper.writeValueAsString(pmsdkSubscription);
        }
        catch (IOException e)
        {
            LOGGER.error("Error in create and activate subscription");
            throw new OperatorException("Error in create and activate subscription : " + networkElementName + " Error:" + e.getMessage());
        }
        return subscription;

    }

    private ObjectMapper getObjectMapper()
    {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(SerializationConfig.Feature.FAIL_ON_EMPTY_BEANS, false);
        objectMapper.configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.setSerializationInclusion(Inclusion.NON_NULL);
        return objectMapper;
    }

    private Map getNeDetails(String networkElementName) throws OperatorException
    {
        try
        {
            String fdnResult = getFdn(GET_PO_ID + networkElementName + "?includeNonPersistent=true");
            ObjectMapper om = new ObjectMapper();
            final Map<String, Object> map = om.readValue(fdnResult, Map.class);
            return map;
        }
        catch (IOException e)
        {
            LOGGER.error("Error in getting po id");
            throw new OperatorException("Error in getting po id  :" + networkElementName + " Error:" + e.getMessage());
        }

    }

    private String getFdn(final String command) throws OperatorException
    {
        RequestBuilder requestBuilder = getHttpTool().request();
        HttpResponse response = requestBuilder.get(command);
        final HttpStatus responseCode = response.getResponseCode();
        if (responseCode != HttpStatus.OK && responseCode != HttpStatus.CREATED)
        {
            throw new OperatorException("Invalid HTTP status: " + response.getStatusLine());
        }
        return response.getBody().toString();
    }

    private String[] fetchNetsimsDetails() throws OperatorException
    {
        List<NetSimCommand> commandsToExecute = new ArrayList<NetSimCommand>();
        List<String> dgTwoSimuations = new ArrayList<>();
        String netsimDetails[] = new String[3];

        final Host netsimHost = netSimsList.get(0);
        LOGGER.info("Using NetSim host " + netsimHost);
        netsimHost.setUser("netsim");
        netsimHost.setPass("netsim");
        // Getting a random host
        NetSimSession handler = NetSimCommandHandler.getSession(netsimHost);
        commandsToExecute.add(NetSimCommands.showSimulations());
        NetSimResult simesrsult = handler.exec(commandsToExecute);
        CommandOutput[] result = simesrsult.getOutput();
        LOGGER.info("show simulations --> {} : ", result[0].asColumns());
        List<Map<String, String>> simulations = result[0].asColumns();
        for (Map<String, String> simulationEntry : simulations)
        {
            for (Map.Entry<String, String> entry : simulationEntry.entrySet())
            {
                String value = simulationEntry.get(entry.getKey());
                if (value.contains("DG2"))
                {
                    LOGGER.info("Dg2 simumations --> {} : ", value);
                    dgTwoSimuations.add(value);
                }
            }
        }
        /* use any on of DG2 simulation as of now using last index */
        LOGGER.info("DG2 simulation : " + dgTwoSimuations.get(dgTwoSimuations.size() - 1));
        commandsToExecute.add(NetSimCommands.open(dgTwoSimuations.get(dgTwoSimuations.size() - 1)));
        handler.exec(commandsToExecute);

        commandsToExecute.add(NetSimCommands.showSimnes());
        NetSimResult simnessResult = handler.exec(commandsToExecute);
        LOGGER.info("show simness : " + simnessResult.getRawOutput());
        CommandOutput[] simNessCommandResult = simnessResult.getOutput();
        List<Map<String, String>> simNessList = simNessCommandResult[simNessCommandResult.length - 1].asColumns();

        String neName = simNessList.get(2).get("NE Name");
        String neIpAddress = simNessList.get(2).get("In Address").split("\\s+")[0];
        netsimDetails[0] = neName;
        netsimDetails[1] = neIpAddress;
        netsimDetails[2] = dgTwoSimuations.get(dgTwoSimuations.size() - 1);

        return netsimDetails;

    }

    private String sendCommandForPm(final String command) throws OperatorException
    {
        final RequestBuilder requestBuilder = getHttpTool().request();

        requestBuilder.contentType(ContentType.APPLICATION_JSON).body(command);

        final HttpResponse response = requestBuilder.post(CREATE_SUBSCRIPTION);
        final HttpStatus responseCode = response.getResponseCode();
        if (responseCode != HttpStatus.ACCEPTED)
        {
            throw new OperatorException("Invalid HTTP status: " + response.getStatusLine());
        }

        return response.getBody().toString();
    }

    private String deactivateSubsciption(final String command, String subscriptionId) throws OperatorException
    {
        final RequestBuilder requestBuilder = getHttpTool().request();

        requestBuilder.contentType(ContentType.APPLICATION_JSON).body(command);
        String deactivateSubscription = DEACTIVATE_SUBSCRIPTION.replace("{ID}", subscriptionId);

        final HttpResponse response = requestBuilder.post(deactivateSubscription);
        final HttpStatus responseCode = response.getResponseCode();
        if (responseCode != HttpStatus.OK)
        {
            throw new OperatorException("Invalid HTTP status: " + response.getStatusLine());
        }

        return response.getBody().toString();
    }

    private void deleteSubscription(String subscriptionId) throws OperatorException
    {
        String deleteSubscription = DELETE_SUBSCRIPTION.replace("{ID}", subscriptionId);
        getHttpTool().delete(deleteSubscription);
    }

    private String genericGet(final String command) throws OperatorException
    {
        RequestBuilder requestBuilder = getHttpTool().request();
        HttpResponse response = requestBuilder.get(command);
        final HttpStatus responseCode = response.getResponseCode();
        if (responseCode != HttpStatus.OK && responseCode != HttpStatus.CREATED)
        {
            throw new OperatorException("Invalid HTTP status: " + response.getStatusLine());
        }
        return response.getBody().toString();
    }

    private String sendCommandForPmactivate(final String command) throws OperatorException
    {
        final RequestBuilder requestBuilder = getHttpTool().request();
        requestBuilder.contentType(ContentType.APPLICATION_JSON).body(command);
        final HttpResponse response = requestBuilder.post(ACT_SUBSCRIPTION);
        final HttpStatus responseCode = response.getResponseCode();
        if (responseCode != HttpStatus.OK && responseCode != HttpStatus.CREATED)
        {
            throw new OperatorException("Invalid HTTP status: " + response.getStatusLine());
        }
        return response.getBody().toString();
    }
}
