### Design Environment Setup

#### Terminal Server

Since most OpenStack/FlexiKube environments are not directly accessible from your workstation, you can set up tunneling
on a Terminal Server to allow you to access REST/Services hidden behind the director.

See [[1]](#references) on how to create PuTTy tunnels to access the director node via SSH, the ENM UI & Kubernetes

* Verify you can access the Kubernetes cluster using

        kubectl get namespace
        helm list --all

* Verify you can SSH to the director (_using hostnames & ports as used in [[1]](#references)_):
      
        ssh eccd@nodelocal-api.eccd.local -p 2622 -i eccd-keypair_ALL.pem

* Verify you can open the ENM UI using the `global.ingress.enmHost` hostname


#### TAF configuration

Update local TAF properties to override the default `client_machine` IP/port DIT provides:
Create the file: `${HOME}/taf_properties/hosts.properties`

Add the following into it:

      host.emp.node.client_machine.ip=nodelocal-api.eccd.local
      host.emp.node.client_machine.port.ssh=2622
      host.emp.node.client_machine.namespace=<namespace>

### Building base PM `eric-enm-pmsdk` & FM `eric-enm-fmsdk` images

Standard `docker` `build` and `push` commands are used:

      docker build -f Dockerfile -t armdocker.rnd.ericsson.se/proj_oss_releases/enm/<image>:<version> .

      docker push -t armdocker.rnd.ericsson.se/proj_oss_releases/enm/<image>:<version> .


### Building base PM `pm-sdk-templates` & FM `fm-sdk-templates` chart templates

A script exists in the `fm-sdk-templates` and `pm-sdk-templates` repos that can be used to create the template archives

* `fm-sdk-templates` -> `bash prepare_fmsdk_tar.sh -g --version <version>`
* `pm-sdk-templates` -> `bash prepare_pmsdk_tar.sh -g --version <version>`

### Building the cloud-native-enm-sdk archive

The script `prepare_sdk_bm_archive.sh` is used to create the `cloud-native-enm-sdk` archive:

      bash prepare_sdk_bm_archive.sh --help
      -c              Clean build environment
      -p              Prepare files for archive build
      --version       If preparing/generating the archive, use this version for the archive
      -g              Generate FM SDK BuildManager archive (prepare should be executed first)
      --dest          Archive generation destination
      --fmsdk         Path (or url) to the eric-enmsg-custom-fm-oneflow template chart
      --fmsdk_tag     Use provided FM SDK image tag in the docker tar create (defaults to latest)
      --fmsdk_repo    Use provided FM SDK image repo path (defaults to proj-cenm-sdk/proj-cenm-sdk-released)
      --pmsdk         Path (or url) to the eric-enmsg-custom-pm-oneflow template chart
      --pmsdk_tag     Use provided PM SDK image tag in the docker tar create (defaults to latest)
      --pmsdk_repo    Use provided PM SDK image repo path (defaults to proj-cenm-sdk/proj-cenm-sdk-released)
      --light         If generating the archive, use an empty docker.tar

e.g:

      bash prepare_sdk_bm_archive.sh -p -g --version 9.9.9 --light


### Building or running `fm-sdk-testware` locally

Note: TAF suites generate and store items in `/var/tmp/taf_build_dir` on Linux and `System.getProperty("java.io.tmpdir") + "/taf_build_dir"` on Windows

To build the TAF module:

      mvn clean install -DskipTests -DskipTafTests

To run the TAF test suite locally:

      mvn clean install -Dtaf.config.dit.deployment.name=<cluster_id> \
          -DsdkBuildManager=<cloud-native-enm-sdk-archive> \
          -Dintegration_value_type="<integration_value_type>" \
          -Dproduct_set_version="<product_set_version>" \
          -Drepository-url=armdocker.rnd.ericsson.se/proj_oss_releases/enm


where

| Flag                                | Description                                                                                                          |
|-------------------------------------|----------------------------------------------------------------------------------------------------------------------|
| `-Dtaf.config.dit.deployment.name=` | cENM cluster name (as defined in DIT)                                                                                |
| `-DsdkBuildManager=`                | Path to the SDK BuildManager archive (local tar or remote nexus url)                                                 |
| `-Dintegration_value_type=`         | The cENM integration values type used to install cENM, e.g. `eric-enm-single-instance-production-integration-values` |
| `-Dproduct_set_version=`            | ProductSet version of cENM installed on the cluster                                                                  |

#### Extra `mvn install` options:

| Option                            | Value(s)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
|-----------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| -DskipGeneration=<value>          | One or more of the following seperated by a comma <table> <tbody> <tr><td>none</td><td>Dont skip anything (default)</td></tr> <tr><td>all</td><td>will skip generation of everything</td></tr> <tr><td>sdk-extract</td><td>skip the extraction of cloud-native-enm-sdk archive</td></tr> <tr><td>maven</td><td>skip the maven archetype generation and packaging</td></tr> <tr><td>load-csar-images</td><td>skip the sdkBuildManager --load-csar-images action</td></tr> <tr><td>build-load-images</td><td>skip the sdkBuildManager --build-load-images action</td></tr> <tr><td>rebuild-csar</td><td>skip the sdkBuildManager --rebuild-csar action</td></tr> <tr><td>csar-upload</td><td>skip uploading generated CSAR to the director node</td></tr> <tr><td>verify</td><td>skip verification of charts (build and install only)</td></tr> <tr><td>install</td><td>skip build and install and only verify</td></tr> </tbody> </table> |
| -Dhelm.dry-run                    | Setting this (value doesn't matter) will append `--dry-run` to any `helm upgrade --install` commands                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| -Dcsar.light                      | Build a light custom SDK csar                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| -DsdkTypes.test=                  | List of SDK types to test, defaults to `FM,PM`, allows you to test only one chart e.g. `-DsdkTypes.test=PM`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| -DsdkBuildManager.preCommit       | TAF will use the `sdkBuildManager.py` contained in the `cloud-native-enm-sdk` archive but you can override that by settings this to the path of the `sdkBuildManager.py` you want to use instead                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| -D\<cluster>.private.key          | SSH key of the director node, default is to obtain this from DIT                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| -Deric-enmsg-fmsdkexample=\<path> | Path to a FM SDK chart, default is to use the one located in the SDK CSAR                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| -Deric-enmsg-pmsdkexample=\<path> | Path to a PM SDK chart, default is to use the one located in the SDK CSAR                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| -Dhelm.values.\<key>=\<value>     | Add more helm values (or override existing ones) ising '--set' in 'helm update --install' commands                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| -Dmonitoring.image=\<value>       | Override the eric-enm-monitoring-eap7 image (full tag). Setting the value to 'chart' will leave the version as it is set in the template regardless of the spint version used to install the namespace                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |

Note: Monitoring is enabled by default and TAF will look up the version of the eric-enm-monitoring-eap7 image to use. 
It looks at the eric-enm-stateless-integration chart for the ProductSet being tested to see what image the eric-enmsg-mssnmpfm chart is using and updates the `sdk-inputs/sdk/<type>sdk/<chart>/config/build.yaml` file to that version.

The default behaviour can be changed so the template version can be used (regardless of what the eric-enm-stateless-integration ProductSet version is) by setting `-Dmonitoring.image=chart` (_"chart"_ is the value to use)

### Executing SDK suite via Jenkins

TAF can be executed via Jenkins, see [[2]](#references).
Job parameters:

* `cluster_id` The deployment name to run the job against
* `testware_items` Testware to run, change {version} to the version of ERICTAFfmsdk_CXP9042868 you want to run:

        <item timeout-in-seconds="900">
          <name>SDK - insert_name_here</name>
          <component>com.ericsson.oss.mediation.sdk:ERICTAFfmsdk_CXP9042868:{version}</component>
          <suites>suites.xml</suites>
          <env-properties>
              <property type="system" key="sdkBuildManager">{sdkBuildManager}</property>
              <property type="system" key="integration_value_type">{integration_value_type}</property>
              <property type="system" key="product_set_version">{product_set_version}</property>
              <property type="system" key="repository-url">{repository-url}</property>
          </env-properties>
        </item>

e.g.

        <item timeout-in-seconds="900">
            <name>SDK - insert_name_here</name>
            <component>com.ericsson.oss.mediation.sdk:ERICTAFfmsdk_CXP9042868:1.0.2-SNAPSHOT</component>
            <suites>suites.xml</suites>
            <env-properties>
                <property type="system" key="sdkBuildManager">https://arm2s11-eiffel004.eiffel.gic.ericsson.se:8443/nexus/content/repositories/cloud-native-enm-sdk/buildmanager/buildmanager-csar/cloud-native-enm-sdk/1.4.4-0/cloud-native-enm-sdk-1.4.4-0.tar.gz</property>
                <property type="system" key="integration_value_type">eric-enm-single-instance-production-integration-values</property>
                <property type="system" key="product_set_version">22.16.51</property>
                <property type="system" key="repository-url">armdocker.rnd.ericsson.se/proj_oss_releases/enm</property>
            </env-properties>
        </item>

Note: The same 'Extra `mvn install`options' listed above can be used here too e.g:

      <property type="system" key="helm.dry-run">true</property>


If you change any TAF source and want to execute the changes via Jenkins:

      mvn versions:set -DnewVersion=99.0.3-SNAPSHOT
      mvn clean install deploy -DskipTests -DskipTafTests


This will push a SNAPSHOT version to nexus, see the mvn output to get the exact version pushed.
Update the `testware_items` to the SNAPSHOT version and run the Job again.

## References

1. [Kubernetes Cluster access via TerminalServer putty tunnel](https://confluence-oss.seli.wh.rnd.internal.ericsson.com/pages/viewpage.action?pageId=487948222)
2. [cENM_Design_Teams_TAF](https://fem35s11-eiffel004.eiffel.gic.ericsson.se:8443/jenkins/job/cENM_Design_Teams_TAF/)
