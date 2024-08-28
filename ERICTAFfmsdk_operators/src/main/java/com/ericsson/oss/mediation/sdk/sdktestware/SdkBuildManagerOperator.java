package com.ericsson.oss.mediation.sdk.sdktestware;

import com.ericsson.oss.mediation.sdk.sdktestware.handlers.IntegrationBuildYaml;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.nio.file.Files.walk;

public class SdkBuildManagerOperator {
    private final static Logger LOGGER = LoggerFactory.getLogger(SdkBuildManagerOperator.class);

    private void initializeBuild(final Path buildDir) throws OperatorException {
        if (buildDir.toFile().exists()) {
            LOGGER.info("Cleaning " + buildDir.toFile().getAbsolutePath());
            try {
                walk(buildDir).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            } catch (IOException e) {
                throw new OperatorException("Failed to delete build dir " + buildDir.toFile().getAbsolutePath(), e);
            }
        }
        if (!buildDir.toFile().exists()) {
            if (!buildDir.toFile().mkdirs()) {
                throw new OperatorException("Failed to create build dir " + buildDir.toFile().getAbsolutePath());
            }
        }
    }

    public void setupSdkBuildInputs(final SdkType sdkType, final Path inputsDir, final Map<String, List<File>> packages, final Map<String, String> images) throws OperatorException {
        LOGGER.info(sdkType + "SDK inputs directory : " + inputsDir);
        final Path chartInputsDir = Paths.get(inputsDir.toFile().getAbsolutePath(), sdkType.inputs(), sdkType.chartName());
        if (!Flags.skipSdkManagerBuildLoadImages()) {
            initializeBuild(chartInputsDir);
        }
        copyInputStructure(sdkType, chartInputsDir);
        updateBuildYaml(inputsDir, images);

        copyMavenArtifacts(packages, chartInputsDir);
    }

    public void setupSdkIntegrationCsarInputs(final Path inputsDir) throws OperatorException {
        final String integTemplate = "data/sdk/integration.yaml";
        final URL targetClasses = ClassLoader.getSystemResource(integTemplate);
        final URL targetJar = getClass().getResource("/" + integTemplate);

        final File target = Paths.get(inputsDir.toString(), "sdk", "integration.yaml").toFile();

        if (targetClasses != null) {
            ArchiveOperator.extractFileFromJar(Paths.get(targetClasses.getFile()), integTemplate, target);
        } else if (targetJar != null) {
            final String[] parts = targetJar.getFile().split("!");
            final File container = new File(parts[0].replace("file:", ""));
            ArchiveOperator.extractFileFromJar(container.toPath(), integTemplate, target);
        } else {
            AssertLog.fail("No inputs structure templates found!");
        }
    }

    public File[] buildLoadImages(final Path sdkBuildManager, final Path sdkChart,
                                  final Path sdkInputs, final String repositoryUrl,
                                  final Path outputDir) throws OperatorException {
        try {
            Files.setPosixFilePermissions(sdkBuildManager, getPermission());
        } catch (IOException exception) {
            throw new OperatorException("Failed to execute FilePermissions", exception);
        }

        final List<String> command = Arrays.asList(
                sdkBuildManager.toString(),
                "--build-load-images",
                "--sdk-path", sdkChart.toString(),
                "--sdk-input-path", sdkInputs.toString(),
                "--repository-url", repositoryUrl,
                "--custom-sdk-path", outputDir.toString(),
                "-d"
        );
        if (!Flags.skipSdkManagerBuildLoadImages()) {
            final LocalProcessOperator processOperator = new LocalProcessOperator();
            processOperator.execute(Arrays.asList("chmod", "+x", sdkBuildManager.toString()), false);
            processOperator.execute(command, false);
        }
        final File[] generated = outputDir.toFile().listFiles((dir, name) -> name.toLowerCase().endsWith(".tgz"));
        if (generated == null || generated.length == 0) {
            throw new OperatorException("No charted generated in sdkBuildManager.py --build-load-images");
        }
        return generated;
    }

    public Path rebuildCsar(final Path sdkBuildManager, final Path buildYaml, final Path customBuildDir,
                            final String repositoryUrl, final boolean light) throws OperatorException {
        final List<String> command = new ArrayList<>();
        command.add(sdkBuildManager.toString());
        command.add("--rebuild-csar");
        command.add(buildYaml.toString());
        command.add("--custom-sdk-path");
        command.add(customBuildDir.toString());
        command.add("--repository-url");
        command.add(repositoryUrl);
        command.add("--product-set");
        command.add(Flags.productSetVersion());
        if (light) {
            command.add("--csar-light");
            LOGGER.info("Generating a light CSAR");
        }
        final IntegrationBuildYaml buildData = new IntegrationBuildYaml(buildYaml);
        final String csarFileName = buildData.getName() + "-" + buildData.getVersion();
        final Path csarPath = Paths.get(customBuildDir.toString(), "csar", csarFileName, csarFileName + ".csar");

        if (!Flags.skipSdkManagerRebuildCsar()) {
            final LocalProcessOperator processOperator = new LocalProcessOperator();
            processOperator.execute(Arrays.asList("chmod", "+x", sdkBuildManager.toString()), false);
            processOperator.execute(command, false, sdkBuildManager.getParent());
        }
        AssertLog.assertFileExists(csarPath);
        LOGGER.info("Created SDK CSAR " + csarPath);
        return csarPath;
    }

    public Path getSdkTemplates(final Path buildDir, final SdkType sdkType) throws OperatorException {
        final String devTemplate = Flags.sdkTemplate(sdkType);
        if (devTemplate != null) {
            return Paths.get(devTemplate);
        } else {
            final Path base = Paths.get(buildDir.toString(), "templates", "charts");

            final File[] files = base.toFile().listFiles((dir, name) -> Pattern.compile(
                    sdkType.templatePrefix() + "-.*\\.tar\\.gz").matcher(name).matches());
            if (files == null) {
                throw new OperatorException("No template archives found in " + base);
            }
            if (files.length > 1) {
                throw new OperatorException("More than one chart templates found in " + base);
            }
            return files[0].toPath();
        }
    }

    private Set<PosixFilePermission> getPermission() {
        final Set<PosixFilePermission> perms = new HashSet<>();
        perms.add(PosixFilePermission.OWNER_READ);
        perms.add(PosixFilePermission.OWNER_WRITE);
        perms.add(PosixFilePermission.OWNER_EXECUTE);

        perms.add(PosixFilePermission.OTHERS_READ);
        perms.add(PosixFilePermission.OTHERS_WRITE);
        perms.add(PosixFilePermission.OTHERS_EXECUTE);

        perms.add(PosixFilePermission.GROUP_READ);
        perms.add(PosixFilePermission.GROUP_WRITE);
        perms.add(PosixFilePermission.GROUP_EXECUTE);

        return perms;
    }

    private void updateBuildYaml(final Path inputsDir, final Map<String, String> images) throws OperatorException {
        final List<Path> buildYamls = new ArrayList<>();
        try (Stream<Path> walkStream = Files.walk(inputsDir)) {
            walkStream.filter(p -> p.toFile().isFile()).forEach(f -> {
                if (f.getFileName().toString().equals("build.yaml")) {
                    buildYamls.add(f);
                }
            });
        } catch (IOException e) {
            throw new OperatorException(e);
        }

        final Map<String, Map<String, String>> nameMap = new HashMap<>();

        final List<String> delete = new ArrayList<>();
        for (String localTag : images.values()) {
            final String[] parts = localTag.split("/");
            if (parts.length == 1) {
                delete.add(localTag);
            } else {
                final String[] nameTag = parts[parts.length - 1].split(":");

                final Map<String, String> nameTagData = new HashMap<>();
                nameTagData.put("image-version", nameTag[1]);
                nameTagData.put("image-repository", String.join("/", Arrays.copyOfRange(parts, 0, parts.length - 1)));

                nameMap.put(nameTag[0], nameTagData);
            }
        }

        for (Path buildYaml : buildYamls) {
            final Map<?, ?> data;
            try (final BufferedReader reader = new BufferedReader(new FileReader(buildYaml.toFile()))) {
                data = new Yaml().loadAs(reader, Map.class);

                for (String deleteTag : delete) {
                    data.remove(deleteTag);
                }
                final Set<?> iter = data.keySet();
                for (Object name : iter) {
                    if (nameMap.containsKey(name.toString())) {
                        final Map<String, String> image = (Map<String, String>) data.get(name);
                        image.put("image-version", nameMap.get(name.toString()).get("image-version"));
                        image.put("image-repository", nameMap.get(name.toString()).get("image-repository"));
                    }
                }
            } catch (IOException e) {
                throw new OperatorException(e);
            }

            try (final BufferedWriter writer = new BufferedWriter(new FileWriter(buildYaml.toFile()))) {
                final DumperOptions options = new DumperOptions();
                options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
                final Yaml yaml = new Yaml(options);
                yaml.dump(data, writer);
            } catch (IOException e) {
                throw new OperatorException(e);
            }
        }
    }

    private void copyMavenArtifacts(final Map<String, List<File>> packages, final Path inputsDir) throws OperatorException {
        LOGGER.info("Copying image packages to " + inputsDir);
        for (String pkgType : packages.keySet()) {
            final Path pkgTypeDir = Paths.get(inputsDir.toString(), pkgType);
            LOGGER.info("Creating package directory " + pkgTypeDir);
            if (!pkgTypeDir.toFile().exists() && !pkgTypeDir.toFile().mkdirs()) {
                throw new OperatorException("Failed to create dir " + pkgTypeDir);
            }
            for (File pkg : packages.get(pkgType)) {
                final Path target = Paths.get(pkgTypeDir.toFile().getAbsolutePath(), pkg.getName());
                try {
                    LOGGER.info("Copying " + pkg + " to " + target);
                    Files.copy(pkg.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new OperatorException("Failed to copy " + pkg + " to " + target);
                }
            }
        }
    }

    private void copyInputStructure(final SdkType sdkType, final Path inputsDir) throws OperatorException {
        final String inputsTemplate = "data/" + sdkType.inputs() + "/" + sdkType.chartName();
        final URL targetClasses = ClassLoader.getSystemResource(inputsTemplate);
        final URL targetJar = getClass().getResource("/" + inputsTemplate);

        if (targetClasses != null) {
            copyLocalStructure(new File(targetClasses.getFile()), inputsDir);
        } else if (targetJar != null) {
            final String[] parts = targetJar.getFile().split("!");
            copyJarStructure(new File(parts[0].replace("file:", "")), inputsTemplate + "/", inputsDir);
        } else {
            AssertLog.fail("No inputs structure templates found!");
        }
    }

    private void copyJarStructure(final File testInputs, final String extractRoot, final Path destination) throws OperatorException {
        ArchiveOperator.extractJar(testInputs.toPath(), destination.toString(), extractRoot);
    }

    private void copyLocalStructure(final File testInputs, final Path destination) throws OperatorException {
        LOGGER.info("Using " + testInputs + " as source structure");
        final String[] testData = testInputs.list();
        AssertLog.assertNotNull(testData, "No test data found under " + testInputs);
        assert testData != null;
        for (String dir : testData) {
            final File absPath = new File(testInputs, dir);
            try {
                FileUtils.copyDirectoryToDirectory(absPath, destination.toFile());
            } catch (IOException e) {
                throw new OperatorException("Could not copy test data from " + absPath + " to " + destination, e);
            }
        }
    }
}
