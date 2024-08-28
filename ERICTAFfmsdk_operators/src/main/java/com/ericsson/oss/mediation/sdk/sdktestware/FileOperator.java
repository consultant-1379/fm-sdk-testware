package com.ericsson.oss.mediation.sdk.sdktestware;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileOperator {
    private final static Logger LOGGER = LoggerFactory.getLogger(FileOperator.class);

    /**
     * Helper function to get a file based on 'http[s]://' 'file://' or '/'
     * If http[s] url give, the file will get downloaded locally and the return value this local path
     *
     * @param externalFilePath File path
     * @return local file path
     * @throws OperatorException Any errors
     */
    public static Path getExternalFile(final String externalFilePath) throws OperatorException {
        try {
            final AtomicBoolean isLocal = new AtomicBoolean();
            final URL sdkBuildMgrCsarUrl = getUrl(externalFilePath, isLocal);
            final Path destination = Paths.get(Flags.tempDirectory().toString(), new File(sdkBuildMgrCsarUrl.getFile()).getName());
            final File parentDir = destination.getParent().toFile();
            if (!parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    throw new OperatorException("Failed to create " + parentDir);
                }
            }
            boolean downLoad = true;
            LOGGER.info("Checking if " + externalFilePath + " needs to be copied to " + Flags.tempDirectory());

            if (isLocal.get()) {
                if (destination.toFile().exists()) {
                    final String localMd5 = md5sum(Paths.get(sdkBuildMgrCsarUrl.getFile()));
                    final String destinationMd5 = md5sum(destination);
                    downLoad = !localMd5.equals(destinationMd5);
                }
            } else {
                final URL remoteMd5Url = new URL(sdkBuildMgrCsarUrl + ".md5");
                try (final InputStream inputStream = remoteMd5Url.openStream()) {
                    final Path localMd5File = Paths.get(destination.getParent().toString(), destination.getFileName().toString() + ".md5");
                    Files.copy(inputStream, localMd5File, StandardCopyOption.REPLACE_EXISTING);
                    final List<String> contents = Files.readAllLines(localMd5File);
                    final String remotePathMd5 = contents.get(0);
                    final String localMd5 = md5sum(destination);
                    downLoad = !localMd5.equals(remotePathMd5);
                } catch (FileNotFoundException e) {
                    LOGGER.info("File " + remoteMd5Url + " not found, can't check MD5");
                    downLoad = true;
                }
            }
            if (downLoad) {
                final InputStream inputStream = sdkBuildMgrCsarUrl.openStream();
                LOGGER.info("Putting " + sdkBuildMgrCsarUrl + " in " + destination);
                Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
            } else {
                LOGGER.info("Skipping copy of " + sdkBuildMgrCsarUrl + ", local and remote MD5 match.");
            }
            return destination;
        } catch (MalformedURLException e) {
            throw new OperatorException("Invalid file path " + externalFilePath, e);
        } catch (IOException e) {
            throw new OperatorException("Could not download " + externalFilePath, e);
        }
    }

    private static URL getUrl(final String path, final AtomicBoolean isLocal) throws OperatorException {
        final String mFile = "^file:";
        final String mHttps = "^https?:";
        isLocal.set(true);
        final Pattern pattern = Pattern.compile(mFile + "|" + mHttps, Pattern.CASE_INSENSITIVE);
        final Matcher match = pattern.matcher(path);
        try {
            if (match.find()) {
                isLocal.set(Pattern.compile(mHttps, Pattern.CASE_INSENSITIVE).matcher(path).matches());
                return new URL(path);
            } else {
                return new File(path).toURI().toURL();
            }
        } catch (MalformedURLException e) {
            throw new OperatorException("Could not match path " + path, e);
        }
    }

    public static String md5sum(final Path file) throws OperatorException {
        final Instant start = Instant.now();
        try {
            // TODO: Change this to use a better, more performant way
            if (file.toFile().exists()) {
                final byte[] data = Files.readAllBytes(file);
                final byte[] hash = MessageDigest.getInstance("MD5").digest(data);

                return new BigInteger(1, hash).toString(16);
            } else {
                return "";
            }
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new OperatorException("Could not get md5sum of local file " + file, e);
        } finally {
            final Instant end = Instant.now();
            LOGGER.info("md5sum " + file + " took " + (Duration.between(start, end)));
        }
    }

    public static String getFileSize(final Path path) throws OperatorException {
        try (final InputStream stream = path.toUri().toURL().openStream()) {
            long byteSize = stream.available();
            final CharacterIterator ci = new StringCharacterIterator("kMGTPE");
            while (byteSize <= -999_950 || byteSize >= 999_950) {
                byteSize /= 1000;
                ci.next();
            }
            return String.format("%.1f %cB", byteSize / 1000.0, ci.current());
        } catch (IOException e) {
            throw new OperatorException("Could not determinte size of " + path, e);
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println(getFileSize(Paths.get("/home/eeipca/ftp_storage/cnFMSDK/cloud-native-enm-sdk-1.8.0-58.tar.gz")));
        System.out.println(getFileSize(Paths.get("/home/eeipca/ftp_storage/cnFMSDK/cloud-native-enm-sdk-9.9.9.42.tar.gz")));
    }
}
