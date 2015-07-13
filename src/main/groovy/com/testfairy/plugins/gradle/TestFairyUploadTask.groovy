package com.testfairy.plugins.gradle

import com.testfairy.uploader.HttpClientFactory
import com.testfairy.uploader.TestFairyUploader
import org.apache.commons.io.FilenameUtils
import org.apache.commons.io.IOUtils
import org.apache.http.HttpEntity
import org.apache.http.HttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.DefaultHttpClient
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction

class TestFairyUploadTask extends DefaultTask {
    public def variant;

    @InputFile
    public File uploadedFile
    public String changelog

    public String downloadUrl

    public void setUploadedFile(File uploadedFile) { this.uploadedFile = uploadedFile }
    public File getUploadedFile() { return uploadedFile }
    public void setChangelog(String changelog) { this.changelog = changelog }
    public String getChangelog() { return changelog?:getChangelogFromProperties(project)  }

    public TestFairyUploadTask() {
        this.group = "TestFairy"
    }

    public void setVariant(variant) {
        this.variant = variant;
    }

    public def getVariant() {
        this.variant
    }

    // optional: testfairyChangelog, as passed through -P
    private static String getChangelogFromProperties(Project project) {
        project.hasProperty("testfairyChangelog") ? project.property("testfairyChangelog") : null
    }

    @TaskAction public void executeTask() {
        downloadUrl = uploadFile()
    }

    public String uploadFile() {
        TestFairyExtension extension = project.extensions.findByName("testfairyConfig") as TestFairyExtension
        def apkTools = new ApkTools(project)
        def tempDir = temporaryDir.absolutePath

        assertValidApiKey(extension)

        String apiKey = extension.getApiKey()
        String serverEndpoint = extension.getServerEndpoint()

        // use outputFile from packageApp task
        String apkFilename = uploadedFile.getAbsolutePath().toString()
        project.logger.info("Instrumenting ${apkFilename} using apiKey ${apiKey} and server ${serverEndpoint}")

        project.logger.debug("Saving temporary files to ${tempDir}")

        String proguardMappingFilename = null
        if (ApkTools.isMinifyEnabledCompat(variant.buildType) && extension.uploadProguardMapping) {
            // proguard-mapping.txt upload is enabled

            proguardMappingFilename = ApkTools.getMappingFileCompat(variant)
            project.logger.debug("Using proguard mapping file at ${proguardMappingFilename}")
        }

        def json = TestFairyUploader.uploadApk(extension, apkFilename, proguardMappingFilename, changelog)
        if (variant.isSigningReady() && ApkTools.isApkSigned(apkFilename)) {
            // apk was previously signed, so we will sign it again
            project.logger.debug("Signing is ready, and APK was previously signed")

            // first, we need to download the instrumented apk
            String instrumentedUrl = json.instrumented_url.toString()
            project.logger.info("Downloading instrumented APK from ${instrumentedUrl}")

            // add API_KEY to download url, needed only in case of Strict Mode
            instrumentedUrl = instrumentedUrl + "?api_key=" + apiKey
            project.logger.debug("Added api_key to download url, and is now ${instrumentedUrl}")

            String baseName = FilenameUtils.getBaseName(apkFilename)
            String tempFilename = FilenameUtils.normalize("${tempDir}/testfairy-${baseName}.apk".toString())
            project.logger.debug("Downloading instrumented APK onto ${tempFilename}")
            downloadFile(instrumentedUrl, tempFilename)

            // resign using gradle build settings
            apkTools.resignApk(tempFilename, variant.signingConfig)

            // upload the signed apk file back to testfairy
            json = TestFairyUploader.uploadSignedApk(extension, tempFilename)
            (new File(tempFilename)).delete()

            project.logger.debug("Signed instrumented file is available at: ${json.instrumented_url}")
        }

        println ""
        println "Successfully uploaded to TestFairy, build is available at:"
        println json.build_url
        return json.build_url
    }


    /**
     * Make sure ApiKey is configured and not empty.
     *
     * @param extension
     */
    private static void assertValidApiKey(extension) {
        if (extension.getApiKey() == null || extension.getApiKey().equals("")) {
            throw new GradleException("Please configure your TestFairy apiKey before building")
        }
    }


    /**
     * Downloads the entire page at a remote location, onto a local file.
     *
     * @param url
     * @param localFilename
     */
    private static void downloadFile(String url, String localFilename) {
        DefaultHttpClient httpClient = HttpClientFactory.buildHttpClient()
        HttpGet downloadRequest = new HttpGet(url)
        HttpResponse response = httpClient.execute(downloadRequest)
        HttpEntity responseEntity = response.getEntity()

        FileOutputStream fileOutput = new FileOutputStream(localFilename)
        try {
            IOUtils.copy(responseEntity.getContent(), fileOutput)
        } finally {
            fileOutput.close()
        }
    }
}
