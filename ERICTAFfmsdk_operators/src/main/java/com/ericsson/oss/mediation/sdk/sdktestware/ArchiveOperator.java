package com.ericsson.oss.mediation.sdk.sdktestware;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ArchiveOperator {
    private final static Logger LOGGER = LoggerFactory.getLogger(ArchiveOperator.class);

    public static List<String> list(final Path archive) throws OperatorException {
        return listArchive(archive);
    }

    private static List<String> listArchive(final Path archive) throws OperatorException {
        final List<String> contents = new ArrayList<>();
        try (final TarArchiveInputStream taIs = new TarArchiveInputStream(Files.newInputStream(archive.toFile().toPath()))) {
            ArchiveEntry entry;
            while (null != (entry = taIs.getNextTarEntry())) {
                if (!entry.isDirectory()) {
                    contents.add(entry.getName());
                }
            }
        } catch (IOException e) {
            throw new OperatorException("Could not list contents of " + archive, e);
        }
        return contents;
    }

    public static boolean isTarEmpty(final Path archive) throws OperatorException {
        try (final TarArchiveInputStream taIs = new TarArchiveInputStream(Files.newInputStream(archive.toFile().toPath()))) {
            return taIs.getNextTarEntry() == null;
        } catch (IOException e) {
            throw new OperatorException("Could not list contents of " + archive, e);
        }
    }

    /**
     * Extract an archive to a location
     *
     * @param archive     Archive path
     * @param destination Location to extract to
     * @throws OperatorException Any extraction errors
     */
    public static void extractArchive(final Path archive, final Path destination) throws OperatorException {
        extractArchive(archive, destination, null);
    }

    public static void extractArchive(final Path archive, final Path destination, final String archiveFile) throws OperatorException {
        try {
            final String mimeType = Files.probeContentType(archive);
            if ("application/zip".equalsIgnoreCase(mimeType)) {
                extractZip(archive.toString(), destination.toString(), archiveFile);
            } else if ("application/x-compressed-tar".equalsIgnoreCase(mimeType)) {
                extractTgz(archive, destination.toString(), archiveFile);
            } else if ("application/x-java-archive".equalsIgnoreCase(mimeType)) {
                extractJar(archive, destination.toString(), null);
            } else {
                throw new OperatorException("Unknown archive type " + mimeType + " for " + archive);
            }
        } catch (IOException e) {
            throw new OperatorException("Could not get mimetype from " + archive, e);
        }
    }

    /**
     * Extract a Jar
     *
     * @param archive     Archive path
     * @param destination Location to extract to
     * @param include     Only extract a file/dir
     * @throws OperatorException Any extraction errors
     */
    public static void extractJar(final Path archive, final String destination, final String include) throws OperatorException {
        try (final JarFile jar = new JarFile(archive.toFile())) {
            final Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                final JarEntry entry = entries.nextElement();
                if (entry.getName().startsWith(include)) {
                    final String relPath = entry.getName().replace(include, "");
                    if (!relPath.equals("")) {
                        final File destFile = new File(destination, relPath);
                        if (entry.isDirectory()) {
                            if (!destFile.exists()) {
                                LOGGER.info("Creating " + destFile.getAbsolutePath());
                                if (!destFile.mkdirs()) {
                                    throw new OperatorException("Failed to create directory " + destFile.getAbsolutePath());
                                }
                            }
                        } else {
                            LOGGER.info("Extracting " + entry.getName() + " to " + destFile.getAbsolutePath());
                            try (InputStream is = jar.getInputStream(entry); final FileOutputStream fos = new FileOutputStream(destFile)) {
                                while (is.available() > 0) {  // write contents of 'is' to 'fos'
                                    fos.write(is.read());
                                }
                            }
                        }
                    }
                }
            }

        } catch (IOException e) {
            throw new OperatorException("Failed to extract " + archive, e);
        }
    }

    public static void extractFileFromJar(final Path archive, final String filePath, final File target) throws OperatorException {
        try (final JarFile jar = new JarFile(archive.toFile())) {
            final Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                final JarEntry entry = entries.nextElement();
                System.out.println(entry.getName());
                if (entry.getName().startsWith(filePath)) {
                    try (InputStream is = jar.getInputStream(entry); final FileOutputStream fos = new FileOutputStream(target)) {
                        while (is.available() > 0) {  // write contents of 'is' to 'fos'
                            fos.write(is.read());
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new OperatorException("Failed to extract " + archive, e);
        }
    }

    /**
     * Extract a .tgz archive
     *
     * @param archive     Archive path
     * @param destination Location to extract to
     * @throws OperatorException Any extraction errors
     */
    public static void extractTgz(final Path archive, final String destination, final String archiveFile) throws OperatorException {
        LOGGER.info("Extracting " + archive + " to " + destination);
        try {
            final InputStream inputStream = Files.newInputStream(archive);
            final GzipCompressorInputStream gzipIn = new GzipCompressorInputStream(inputStream);
            try (TarArchiveInputStream tarIn = new TarArchiveInputStream(gzipIn)) {
                TarArchiveEntry entry;

                while ((entry = (TarArchiveEntry) tarIn.getNextEntry()) != null) {
                    if (archiveFile == null || (archiveFile.equals(entry.getName()))) {
                        if (entry.isDirectory()) {
                            final File entryFile = new File(destination, entry.getName());
                            if (!entryFile.exists()) {
                                LOGGER.info("Creating " + entryFile);
                                boolean created = entryFile.mkdirs();
                                if (!created) {
                                    throw new OperatorException("Failed to create archive directory " + entryFile.getAbsolutePath());
                                }
                            }
                        } else {
                            int count;
                            byte[] data = new byte[1024];
                            final File outFile = new File(destination, entry.getName());
                            LOGGER.info("Extracting " + entry.getName() + " to " + outFile);

                            if (!outFile.getParentFile().exists()) {
                                LOGGER.info("Creating " + outFile.getParentFile());
                                boolean created = outFile.getParentFile().mkdirs();
                                if (!created) {
                                    throw new OperatorException("Failed to create archive directory " + outFile.getParentFile().getAbsolutePath());
                                }
                            }
                            final FileOutputStream fos = new FileOutputStream(outFile, false);
                            try (BufferedOutputStream dest = new BufferedOutputStream(fos, 1024)) {
                                while ((count = tarIn.read(data, 0, 1024)) != -1) {
                                    dest.write(data, 0, count);
                                }
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new OperatorException("Failed to extract " + archive, e);
        }
    }

    /**
     * Extract a .zip
     *
     * @param archive     Archive path
     * @param destination Location to extract to
     * @throws OperatorException Any extraction errors
     */
    public static void extractZip(final String archive, final String destination, final String archiveFile) throws OperatorException {
        LOGGER.info("Extracting " + archive + " to " + destination);
        final ZipFile zipFile = new ZipFile(archive);
        try {
            if (archiveFile != null) {
                zipFile.extractFile(archiveFile, destination);
            } else {
                zipFile.extractAll(destination);
            }
        } catch (ZipException e) {
            throw new OperatorException("Failed to extract " + archive, e);
        }
    }
}