package com.ericsson.oss.mediation.sdk.fmsdktestware;

import org.apache.commons.configuration2.JSONConfiguration;
import org.apache.commons.configuration2.YAMLConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.DumperOptions;

import java.io.*;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static java.nio.file.Files.walk;

public class HelmValuesOperator {
    private final static Logger LOGGER = LoggerFactory.getLogger(HelmValuesOperator.class);
    private static final File BUILD_DIR = new File(Flags.tempDirectory(), "HelmValuesOperator");
    private static final String DIT = "http://atvdit.athtem.eei.ericsson.se/";
    private static final String CIFWK = "https://ci-portal.seli.wh.rnd.internal.ericsson.com/";
    private final String environmentName;
    private final String integrationValueType;
    private final String productSetVersion;
    private final String sprintVersion;

    public HelmValuesOperator() {
        environmentName = Flags.tafConfigDitDeploymentName();
        integrationValueType = Flags.integrationValueType();
        productSetVersion = Flags.productSetVersion();

        final String[] pvs = productSetVersion.split("\\.");
        sprintVersion = String.join(".", pvs[0], pvs[1]);
    }

    private void initializeBuild() throws OperatorException {
        if (!Flags.skipHelmGeneration() && BUILD_DIR.exists()) {
            LOGGER.info("Cleaning " + BUILD_DIR.getAbsolutePath());
            try {
                walk(BUILD_DIR.toPath()).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            } catch (IOException e) {
                throw new OperatorException("Failed to delete build dir " + BUILD_DIR.getAbsolutePath(), e);
            }
        }
        if (!BUILD_DIR.exists()) {
            if (!BUILD_DIR.mkdirs()) {
                throw new OperatorException("Failed to create build dir " + BUILD_DIR.getAbsolutePath());
            }
        }
    }

    /**
     * Get the integration values file to use with any helm commands
     * @return Values file path
     * @throws OperatorException Any errors
     */
    public File getHelmInstallValuesFile() throws OperatorException {
        initializeBuild();

        final Map<String, String> environmentDocs = getEnvironmentDetail(environmentName);
        final String integrationValuesTemplate = getIntegrationValues(sprintVersion, productSetVersion);
        final File localFile = new File(BUILD_DIR, new File(integrationValuesTemplate).getName());

        if (!Flags.skipHelmGeneration() || !localFile.exists()) {
            final YAMLConfiguration helmValues = getIntegrationValuesTemplate(integrationValuesTemplate);
            final JSONConfiguration siteSpecificValues = getSiteSpecificValues(environmentDocs);

            // Dunno why this is in the site specific values ....
            siteSpecificValues.clearProperty("content.enabled");
            siteSpecificValues.clearProperty("content.persistence");
            // eric-cnom-document-database-mg.persistence.storageClass: network-block

            mergeSiteSpecificValues(helmValues, siteSpecificValues);

            try (BufferedWriter bw = new BufferedWriter(new FileWriter(localFile))) {
                helmValues.write(bw);
                final StringWriter sw = new StringWriter();
                final DumperOptions options = new DumperOptions();
                options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
                helmValues.dump(sw, options);
                LOGGER.info("Generated " + localFile.getAbsolutePath());
                LOGGER.info(sw.toString());
            } catch (IOException | ConfigurationException e) {
                throw new OperatorException(e);
            }
        }
        return localFile;
    }

    private void mergeSiteSpecificValues(final YAMLConfiguration helmValues, final JSONConfiguration siteSpecificValues) {
        LOGGER.info("Merging site specific info to values template");
        for (final Iterator<String> it = siteSpecificValues.getKeys("content"); it.hasNext(); ) {
            final String siteSpecificKey = it.next();
            final String siteValuesKey = siteSpecificKey.replace("content.", "");
            final Object value = siteSpecificValues.getProperty(siteSpecificKey);

            LOGGER.info("Setting " + siteValuesKey + ": " + helmValues.getProperty(siteValuesKey) + " -> " + value);
            helmValues.setProperty(siteValuesKey, value);
        }
    }

    private JSONConfiguration getSiteSpecificValues(final Map<String, String> environmentDocs) throws OperatorException {
        final String siteValuesDocId = environmentDocs.get("cENM_integration_values");
        final String siteSpecificValuesJson = dit("api/documents/" + siteValuesDocId);
        final JSONConfiguration siteSpecificValues = new JSONConfiguration();
        try {
            siteSpecificValues.read(new StringReader(siteSpecificValuesJson));
            return siteSpecificValues;
        } catch (ConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, String> getEnvironmentDetail(final String environmentName) throws OperatorException {
        final String json = dit("api/deployments/?q=name=" + environmentName);
        final JSONArray rawJson = new JSONArray(json);
        final JSONObject envDetails = (JSONObject) rawJson.get(0);

        final JSONArray documents = envDetails.getJSONArray("documents");
        final Map<String, String> envDocs = new HashMap<>();
        for (int i = 0; i < documents.length(); i++) {
            final JSONObject obj = documents.getJSONObject(i);
            envDocs.put(obj.getString("schema_name"), obj.getString("document_id"));
        }
        return envDocs;
    }

    private String getIntegrationValues(final String sprint, final String productSet) throws OperatorException {
        final String queryUrl = "api/cloudnative/getCloudNativeProductSetContent/" + sprint + "/" + productSet + "/";
        final String json = cifwk(queryUrl);
        final JSONArray rawData = new JSONArray(json);
        Map<String, Object> integrationValuesFileData = null;
        for (int i = 0; i < rawData.length(); i++) {
            final JSONObject entry = rawData.getJSONObject(i);
            final Map<String, Object> entryMap = entry.toMap();
            for (String key : entryMap.keySet()) {
                if ("integration_values_file_data".equalsIgnoreCase(key)) {
                    final JSONObject o = entry.getJSONArray(key).getJSONObject(0);
                    integrationValuesFileData = o.toMap();
                    break;
                }
            }
        }
        if (integrationValuesFileData == null) {
            throw new OperatorException("Could not find any integration_values_file_data for drop " + sprint + ":" + productSet);
        }

        final String valuesFileName = integrationValuesFileData.get("values_file_name").toString();
        final String valuesFileVersion = integrationValuesFileData.get("values_file_version").toString();
        String valuesFileProductionUrl = integrationValuesFileData.get("values_file_production_url").toString().trim();
        if (valuesFileProductionUrl.length() == 0) {
            valuesFileProductionUrl = integrationValuesFileData.get("values_file_dev_url").toString().trim();
        }
        if (valuesFileProductionUrl.length() == 0) {
            throw new OperatorException("No values_file_url could be found for ProductSet " + queryUrl);
        }

        final String prodSetFile = valuesFileName + "-" + valuesFileVersion;
        final String envValuesTemplate = integrationValueType + "-" + valuesFileVersion;

        final String fileName = valuesFileProductionUrl.replace(prodSetFile, envValuesTemplate);
        LOGGER.info(integrationValueType + " -> " + fileName);
        return fileName;
    }

    private YAMLConfiguration getIntegrationValuesTemplate(final String integrationValuesTemplate) throws OperatorException {
        LOGGER.info("Getting integration values template " + integrationValuesTemplate);
        try {
            final String valuesYaml = getUri(integrationValuesTemplate);
            final YAMLConfiguration siteValues = new YAMLConfiguration();
            siteValues.read(new StringReader(valuesYaml));
            return siteValues;
        } catch (ConfigurationException e) {
            throw new OperatorException("Failed to load Values template", e);
        }
    }


    private String dit(final String query) throws OperatorException {
        return getUri(DIT + query);
    }

    private String cifwk(final String query) throws OperatorException {
        return getUri(CIFWK + query);

    }

    private String getUri(final String uri) throws OperatorException {
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            LOGGER.info("GET " + uri);
            final HttpGet httpGet = new HttpGet(uri);
            try (CloseableHttpResponse response = httpclient.execute(httpGet)) {
                if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                    throw new OperatorException(response.getStatusLine().getStatusCode() + " " + response.getStatusLine().getReasonPhrase());
                }
                final HttpEntity entity = response.getEntity();
                final BufferedReader br = new BufferedReader(new InputStreamReader(entity.getContent()));
                String inputLine;
                final StringBuilder buffer = new StringBuilder();
                while ((inputLine = br.readLine()) != null) {
                    buffer.append(inputLine);
                    buffer.append("\n");
                }
                br.close();
                EntityUtils.consume(entity);
                return buffer.toString();
            }
        } catch (IOException e) {
            throw new OperatorException("Failed to GET URL " + uri, e);
        }
    }

}
