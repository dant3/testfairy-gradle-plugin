package com.testfairy.plugins.gradle

import com.testfairy.uploader.SdkEnvironment
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.io.IOUtils
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.ProjectConfigurationException

import java.util.zip.ZipEntry

class ApkTools {
    private final Configuration configuration;

    public ApkTools(Project project) {
        this.configuration = new Configuration(project)
    }

    /**
     * Returns true if code minification is enabled for this build type.
     * Added to work around runProguard property being renamed to isMinifyEnabled in Android Gradle Plugin 0.14.0
     *
     * @param buildType
     * @return boolean
     */
    public static boolean isMinifyEnabledCompat(buildType) {
        if (buildType.respondsTo("isMinifyEnabled")) {
            return buildType.isMinifyEnabled()
        } else {
            return buildType.runProguard
        }
    }

    public static File getMappingFileCompat(variant) {
        if (variant.metaClass.respondsTo(variant, "getMappingFile")) {
            // getMappingFile was added in Android Plugin 0.13
            return variant.getMappingFile()
        }

        // fallback to getProcessResources
        File f = new File(variant.processResources.proguardOutputFile.parent, 'mapping.txt')
        if (f.exists()) {
            // found as mapping.txt using getProguardOutputFile
            return f.absoluteFile
        }

        f = new File(variant.packageApplication.outputFile.parent)
        f = new File(f.parent, "proguard/${variant.name}/mapping.txt")
        if (f.exists()) {
            // found through getPackageApplication
            return f.absoluteFile
        }

        // any other ways to find mapping file?
        return null
    }

    /**
     * Get a list of all files inside this APK file.
     *
     * @param apkFile
     * @return List<String>
     */
    public static List<String> getApkFiles(File apkFile) {
        List<String> files = new ArrayList<String>()

        ZipFile zf = new ZipFile(apkFile)
        Enumeration<? extends ZipEntry> e = zf.entries
        while (e.hasMoreElements()) {
            ZipEntry entry = e.nextElement()
            String entryName = entry.getName()
            files.add(entryName)
        }

        zf.close()
        return files
    }

    /**
     * Checks if the given APK is signed
     *
     * @param apkFile
     * @return boolean
     */
    public static boolean isApkSigned(File apkFile) {
        List<String> filenames = getApkFiles(apkFile)
        String signatureFile = filenames.find { it.startsWith("META-INF/") && it.endsWith("SF")}
        return signatureFile != null
    }

    /**
     * Remove all signature files from archive, turning it back to unsigned.
     *
     * @param apkFile
     * @param outputFile
     */
    public static void removeSignature(File apkFile, File outFile) {
        ZipArchiveInputStream zais = new ZipArchiveInputStream(new FileInputStream(apkFile))
        ZipArchiveOutputStream zaos = new ZipArchiveOutputStream(new FileOutputStream(outFile))
        while (true) {
            ZipArchiveEntry entry = zais.getNextZipEntry()
            if (entry == null) {
                break
            }

            if (entry.getName().startsWith("META-INF/")) {
                // skip META-INF files
                continue
            }

            ZipArchiveEntry zipEntry = new ZipArchiveEntry(entry.getName())
            if (entry.getMethod() == ZipEntry.STORED) {
                // when storing files, we need to copy the size and crc ourselves
                zipEntry.setSize(entry.getSize())
                zipEntry.setCrc(entry.getCrc())
            }

            zaos.setMethod(entry.getMethod())
            zaos.putArchiveEntry(zipEntry)
            IOUtils.copy(zais, zaos)
            zaos.closeArchiveEntry()
        }

        zaos.close()
        zais.close()
    }

    /**
     * Remove previous signature and sign archive again. Works in-place, overwrites the original apk file.
     *
     * @param apkFile
     * @param sc
     */
    public void resignApk(File apkFile, sc) {
        resignApk(configuration, apkFile, sc)
    }

    /**
     * Remove previous signature and sign archive again. Works in-place, overwrites the original apk file.
     *
     * @param toolsConfig
     * @param apkFile
     * @param sc
     */
    public static File resignApk(Configuration toolsConfig, File apkFile, sc) {
        // use a temporary file in the same directory as apkFile
        File tmpFile = new File(apkFile.getAbsolutePath() + ".temp")

        // remove signature onto temp file, sign and zipalign back onto original filename
        try {
            removeSignature(apkFile, tmpFile)
            signApkFile(toolsConfig, tmpFile, sc)
            zipAlignFile(toolsConfig, tmpFile, apkFile)
            // make sure everything is still intact
            validateApkSignature(toolsConfig, apkFile)
            return apkFile;
        } finally {
            tmpFile.delete()
        }
    }

    /**
     * Sign an APK file with the given signingConfig settings.
     *
     * @param apkFile
     * @param sc
     */
    public void signApkFile(File apkFile, sc) {
        signApkFile(configuration, apkFile, sc)
    }

    /**
     * Sign an APK file with the given signingConfig settings.
     *
     * @param toolsConfig
     * @param apkFile
     * @param sc
     */
    public static void signApkFile(Configuration toolsConfig, File apkFile, sc) {
        def command = [toolsConfig.jarSignerPath, "-keystore", sc.storeFile, "-storepass", sc.storePassword, "-keypass", sc.keyPassword, "-digestalg", "SHA1", "-sigalg", "MD5withRSA", apkFile.absolutePath, sc.keyAlias]
        def proc = command.execute()
        proc.consumeProcessOutput()
        proc.waitFor()
        if (proc.exitValue()) {
            throw new GradleException("Could not jarsign ${apkFile}, used this command:\n${command}")
        }
    }

    /**
     * Zipaligns input APK file onto outFile.
     *
     * @param inFile
     * @param outFile
     */
    public void zipAlignFile(File inFile, File outFile) {
        zipAlignFile(configuration, inFile, outFile)
    }

    /**
     * Zipaligns input APK file onto outFile.
     *
     * @param toolsConfig
     * @param inFile
     * @param outFile
     */
    public static void zipAlignFile(Configuration toolsConfig, File inFile, File outFile) {
        def command = [toolsConfig.zipAlignPath, "-f", "4", inFile.absolutePath, outFile.absolutePath]
        def proc = command.execute()
        proc.consumeProcessOutput()
        proc.waitFor()
        if (proc.exitValue()) {
            throw new GradleException("Could not zipalign ${inFile} onto ${outFile}")
        }
    }


    /**
     * Verifies that APK is signed properly. Will throw an exception
     * if not.
     *
     * @param apkFile
     */
    public void validateApkSignature(File apkFile) {
        validateApkSignature(configuration, apkFile)
    }

    /**
     * Verifies that APK is signed properly. Will throw an exception
     * if not.
     *
     * @param toolsConfig
     * @param apkFile
     */
    public static void validateApkSignature(Configuration toolsConfig, File apkFile) {
        def command = [toolsConfig.jarSignerPath, "-verify", apkFile.absolutePath]
        def proc = command.execute()
        proc.consumeProcessOutput()
        proc.waitFor()
        if (proc.exitValue()) {
            throw new GradleException("Could not jarsign ${apkFile}, used this command:\n${command}")
        }
    }




    private static class Configuration {
        /// path to Java's jarsigner
        public final String jarSignerPath

        /// path to zipalign
        public final String zipAlignPath


        public Configuration(Project project) {
            String sdkDirectory = getSdkDirectory(project)
            SdkEnvironment env = new SdkEnvironment(sdkDirectory)

            this.jarSignerPath = env.locateJarsigner()
            if (jarSignerPath == null) {
                throw new GradleException("Could not locate jarsigner, please update java.home property")
            }

            this.zipAlignPath = env.locateZipalign()
            if (zipAlignPath == null) {
                throw new GradleException("Could not locate zipalign, please validate 'buildToolsVersion' settings")
            }

            // configure java tools before even starting
            project.logger.debug("Located zipalign at ${zipAlignPath}")
            project.logger.debug("Located jarsigner at ${jarSignerPath}")
        }

        private static String getSdkDirectory(Project project) {
            def sdkDir

            Properties properties = new Properties()
            File localProps = project.rootProject.file('local.properties')
            if (localProps.exists()) {
                properties.load(localProps.newDataInputStream())
                sdkDir = properties.getProperty('sdk.dir')
            } else {
                sdkDir = System.getenv('ANDROID_HOME')
            }

            if (!sdkDir) {
                throw new ProjectConfigurationException("Cannot find android sdk. Make sure sdk.dir is defined in local.properties or the environment variable ANDROID_HOME is set.", null)
            }

            return sdkDir.toString()
        }
    }
}
