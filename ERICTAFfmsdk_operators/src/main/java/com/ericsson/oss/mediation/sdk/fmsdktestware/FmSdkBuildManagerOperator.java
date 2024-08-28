package com.ericsson.oss.mediation.sdk.fmsdktestware;

import org.apache.commons.configuration2.YAMLConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.nio.file.attribute.PosixFilePermission;

import static java.nio.file.Files.walk;

public class FmSdkBuildManagerOperator {
    private final static Logger LOGGER = LoggerFactory.getLogger(FmSdkBuildManagerOperator.class);
    private static final File BUILD_DIR = new File(Flags.tempDirectory(), "FmSdkBuildManagerOperator");
    private static final String CHART_NAME = "eric-enmsg-fmsdkexample";
    private static final String SDK_BUILD_INPUTS = "sdk/fmsdk";

    public static Path getSdkInputPathDir() {
        return Paths.get(BUILD_DIR.getAbsolutePath(), SDK_BUILD_INPUTS);
    }

    private void initializeBuild() throws OperatorException {
        if (BUILD_DIR.exists()) {
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
     * Set up SDK inputs structure
     *
     * @param packages Any RPMs that should be copied in
     * @return sdk-build-manager, sdk-path & sdk-input-path
     * @throws OperatorException Any errors
     */
    public Map<String, Path> setupSdkInputs(final Map<String, List<File>> packages) throws OperatorException {

        if (!Flags.skipHelmGeneration())
        {
            initializeBuild();
        }
        try {
            execute(Arrays.asList("/usr/bin/which", "python"), false);
            execute(Arrays.asList("/usr/bin/python", "-V"), false);
        } catch (Throwable e) {
            LOGGER.error("python check failed: " + e.getMessage(), e);
        }

        try {
            execute(Arrays.asList("/usr/bin/which", "python3"), false);
            execute(Arrays.asList("/usr/bin/python3", "-V"), false);
        } catch (Throwable e) {
            LOGGER.error("python3 check failed: " + e.getMessage(), e);
        }


        LOGGER.info("FMSDK inputs directory : " + BUILD_DIR);
        final Path inputsDir = Paths.get(BUILD_DIR.getAbsolutePath(), SDK_BUILD_INPUTS, CHART_NAME);
        copyInputStructure(inputsDir);
        copyMavenArtifacts(packages, inputsDir);

        final Path buildManagerBase = getSdkBuildManagerArchive();
        final Path chartTemplates = getFmSdkTemplates(buildManagerBase);

        final Map<String, Path> args = new HashMap<>();
        args.put("sdk-build-manager", buildManagerBase);
        args.put("sdk-path", chartTemplates);
        args.put("sdk-input-path", getSdkInputPathDir());
        return args;
    }

    private URL getUrl(final String path) throws MalformedURLException {
        final Pattern pattern = Pattern.compile("^file:|^https?:", Pattern.CASE_INSENSITIVE);
        final Matcher match = pattern.matcher(path);
        if (match.find()) {
            return new URL(path);
        } else {
            return new File(path).toURI().toURL();
        }
    }

    private Path getFmSdkTemplates(final Path buildManagerBase) throws OperatorException {
        String fmSdkTemplates = Flags.fmSdkTemplates();
        if (fmSdkTemplates == null) {
            final Path base = Paths.get(buildManagerBase.toString(), "templates", "charts");

            final File[] files = base.toFile().listFiles();
            if (files == null) {
                throw new OperatorException("No template archives found in " + base);
            }
            if (files.length > 1) {
                throw new OperatorException("More than one chart templates found in " + base);
            }
            fmSdkTemplates = files[0].getAbsolutePath();
        }
        LOGGER.info("Getting FMSDK template archive: " + fmSdkTemplates);
        return getExternalFile(fmSdkTemplates);
    }

    private Path getSdkBuildManagerArchive() throws OperatorException {
        final String sdkBuildMgr = Flags.sdkBuildManager();
        LOGGER.info("Getting FMSDK BuildManager archive: " + sdkBuildMgr);
        final Path targz = getExternalFile(sdkBuildMgr);
        final String extractDirTar = FilenameUtils.getBaseName(targz.toString());
        final String extractDirPath = FilenameUtils.getBaseName(extractDirTar);
        final Path extractDir = Paths.get(BUILD_DIR.getPath(), extractDirPath);
        ArchiveOperator.extractArchive(targz, extractDir);
        return extractDir;
    }

    private Path getExternalFile(final String externalFilePath) throws OperatorException {
        try {
            final URL sdkBuildMgrCsarUrl = getUrl(externalFilePath);
            final Path destination = Paths.get(BUILD_DIR.getPath(), new File(sdkBuildMgrCsarUrl.getFile()).getName());
            final InputStream inputStream = sdkBuildMgrCsarUrl.openStream();
            Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
            return destination;
        } catch (MalformedURLException e) {
            throw new OperatorException("Invalid file path " + externalFilePath, e);
        } catch (IOException e) {
            throw new OperatorException("Could not download " + externalFilePath, e);
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

    private void copyInputStructure(final Path inputsDir) throws OperatorException {
        final String inputsTemplate = "data/" + SDK_BUILD_INPUTS + "/" + CHART_NAME;
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

        // Update build.yaml with eric-enm-fmsdk image version
        final String ericEnmFmsdk = Flags.ericEnmFmsdkVersion();
        final Pattern pattern = Pattern.compile("(.*)\\/(.*):(.*)");
        final Matcher matcher = pattern.matcher(ericEnmFmsdk);
        if (!matcher.matches()) {
            throw new OperatorException("eric-enm-fmsdk value not valid, format->{hostname}/{path}/{image_name}:{version}");
        }

        final String repoPath = matcher.group(1);
        final String version = matcher.group(3);

        final YAMLConfiguration buildYaml = new YAMLConfiguration();
        final File buildYamlFile = Paths.get(inputsDir.toString(), "config", "build.yaml").toFile();
        try (final BufferedReader br = new BufferedReader(new FileReader(buildYamlFile))) {
            buildYaml.read(br);

            String key = "eric-enm-fmsdk.image-version";
            LOGGER.info("Setting " + key + "=" + version);
            buildYaml.setProperty(key, version);

            key = "eric-enm-fmsdk.image-repository";
            LOGGER.info("Setting " + key + "=" + repoPath);
            buildYaml.setProperty(key, repoPath);

        } catch (IOException | ConfigurationException e) {
            throw new OperatorException("Failed to update build.yaml version info", e);
        }
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(buildYamlFile))) {
            buildYaml.write(bw);
        } catch (IOException | ConfigurationException e) {
            throw new OperatorException(e);
        }
    }

    private void copyLocalStructure(final File testInputs, final Path destination) throws OperatorException {
        LOGGER.info("Using " + testInputs + " as source structure");
        final String[] testData = testInputs.list();
        AssertLog.assertNotNull(testData, "No test data found under " + testInputs);
        for (String dir : testData) {
            final File absPath = new File(testInputs, dir);
            try {
                FileUtils.copyDirectoryToDirectory(absPath, destination.toFile());
            } catch (IOException e) {
                throw new OperatorException("Could not copy test data from " + absPath + " to " + destination, e);
            }
        }
    }

    private void copyJarStructure(final File testInputs, final String extractRoot, final Path destination) throws OperatorException {
        ArchiveOperator.extractJar(testInputs.toPath(), destination.toString(), extractRoot);
    }

    /**
     * Execute the sdkBuildManager --build-load-images stage
     *
     * @param sdkPath         --sdk-path value
     * @param sdkInputsPath   --sdk-input-path value
     * @param sdkBuildMgrPath sdkBuildManager root path
     * @return List of generatet charts
     * @throws OperatorException Any errors
     */
    public File[] buildLoadImages(final Path sdkPath, final Path sdkInputsPath,
                                  final Path sdkBuildMgrPath) throws OperatorException {
        final File customDir = new File(BUILD_DIR, "custom");
        final String scriptPath = Paths.get(sdkBuildMgrPath.toString(), "scripts", "sdkBuildManager.py").toString();

        try
        {

            Files.setPosixFilePermissions(Paths.get(scriptPath), getPermission());
        }
        catch (IOException exception)
        {

            throw new OperatorException("Failed to execute FilePermissions", exception);
        }
        final String dockerImages = Paths.get(sdkBuildMgrPath.toString(), "docker", "images.txt").toString();
 

        final String repositoryUrl = Flags.repositoryUrl() == null ? Flags.defaultRepositoryUrl() : Flags.repositoryUrl();

        List<String> command = Arrays.asList(
                scriptPath,
                "--build-load-images",
                "--sdk-path", sdkPath.toString(),
                "--sdk-input-path", sdkInputsPath.toString(),
                "--repository-url", repositoryUrl,
                "--custom-sdk-path", customDir.getAbsolutePath(),
                "-d"
        );
        if (!Flags.skipHelmGeneration()) {
            try {
                execute(Arrays.asList("chmod", "+x", scriptPath), false);
                execute(command, false);
            } catch (IOException | InterruptedException e) {
                throw new OperatorException("Failed to execute --build-load-images", e);
            }
        }
        final File[] generated = customDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".tgz"));
        if (generated == null || generated.length == 0) {
            throw new OperatorException("No charted generated in sdkBuildManager.py --build-load-images");
        }

        generateCsar(sdkInputsPath, dockerImages, scriptPath, repositoryUrl, customDir, sdkBuildMgrPath);

        return generated;
    }

    private void generateCsar(final Path sdkInputsPath,
                              final String dockerImages, final String scriptPath, final String repositoryUrl, final File customDir, final Path sdkBuildMgrPath)
            throws OperatorException
    {
        final List<String> csarCommand = Arrays.asList(scriptPath, "--rebuild-csar", "--custom-sdk-path", customDir.getAbsolutePath(), "--csar-name-version", "fm-sdk-csar-1.0.0", "-t", dockerImages, "--repository-url", repositoryUrl, "--sdk-input-path", sdkInputsPath.toString());

        if (!Flags.skipHelmGeneration())
        {
            try
            {
                execute(csarCommand, true, Paths.get(sdkBuildMgrPath.toString(), "scripts"));
            }
            catch (IOException | InterruptedException e)
            {
                throw new OperatorException("Failed to execute --rebuild-csar", e);
            }
        }
        final File[] generatedCsar = customDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".csar"));
        if (generatedCsar == null || generatedCsar.length == 0)
        {
            throw new OperatorException("No Csar found for the custom chart after CSAR generation!");
        }

    }

    private Set<PosixFilePermission> getPermission()
    {
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

    private void execute(final List<String> command, final boolean shell) throws IOException, InterruptedException, OperatorException {
        List<String> pbCommand = new ArrayList<>();

        boolean isLinux = Flags.osName().startsWith("Linux");
        if (shell) {
            if (isLinux) {
                pbCommand.add("/bin/bash");
                pbCommand.add("-c");
            } else {
                pbCommand.add("cmd");
                pbCommand.add("/c");
            }
            pbCommand.add(StringUtils.join(command, " "));
        } else {
            pbCommand.addAll(command);
        }

        LOGGER.info("Executing command: " + String.join(" ", pbCommand));

        final ProcessBuilder processBuilder = new ProcessBuilder(pbCommand);
        processBuilder.redirectErrorStream(true);
        processBuilder.directory(BUILD_DIR);
        processBuilder.environment().put("PYTHONUNBUFFERED", "x");

        final Process process = processBuilder.start();

        final InputStream stdout = process.getInputStream();
        final Thread threadOut = new Thread(() -> {
            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new InputStreamReader(stdout));
                for (String line; ((line = reader.readLine()) != null); ) {
                    LOGGER.info(line);
                }
            } catch (IOException e) {
                LOGGER.error("Unexpected I/O exception reading from process.", e);
            } finally {
                try {
                    if (null != reader) reader.close();
                } catch (IOException e) {
                    LOGGER.error("Unexpected I/O exception closing a stream.", e);
                }
            }
        });
        threadOut.start();
        final int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new OperatorException("exit-code:" + exitCode);
        }
    }

    private void execute(final List<String> command, final boolean shell, final Path workingDir) throws IOException, InterruptedException, OperatorException
    {
        final List<String> pbCommand = new ArrayList<>();

        final boolean isLinux = Flags.osName().startsWith("Linux");
        if (shell)
        {
            if (isLinux)
            {
                pbCommand.add("/bin/bash");
                pbCommand.add("-c");
            }
            else
            {
                pbCommand.add("cmd");
                pbCommand.add("/c");
            }
            pbCommand.add(StringUtils.join(command, " "));
        }
        else
        {
            pbCommand.addAll(command);
        }

        LOGGER.info("Executing command: " + String.join(" ", pbCommand));

        final ProcessBuilder processBuilder = new ProcessBuilder(pbCommand);
        processBuilder.redirectErrorStream(true);
        processBuilder.directory(BUILD_DIR);
        processBuilder.environment().put("PYTHONUNBUFFERED", "x");

        if (workingDir != null)
        {
            processBuilder.directory(workingDir.toFile());
        }

        final Process process = processBuilder.start();

        final InputStream stdout = process.getInputStream();
        final Thread threadOut = new Thread(() -> {
            BufferedReader reader = null;
            try
            {
                reader = new BufferedReader(new InputStreamReader(stdout));
                for (String line; ((line = reader.readLine()) != null);)
                {
                    LOGGER.info(line);
                }
            }
            catch (IOException e)
            {
                LOGGER.error("Unexpected I/O exception reading from process.", e);
            }
            finally
            {
                try
                {
                    if (null != reader) reader.close();
                }
                catch (IOException e)
                {
                    LOGGER.error("Unexpected I/O exception closing a stream.", e);
                }
            }
        });
        threadOut.start();
        final int exitCode = process.waitFor();
        if (exitCode != 0)
        {
            throw new OperatorException("exit-code:" + exitCode);
        }
    }
}
