package com.testfairy.uploader

import com.testfairy.plugins.gradle.TestFairyExtension
import groovy.json.JsonSlurper
import org.apache.http.HttpResponse
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.mime.MultipartEntity
import org.apache.http.entity.mime.content.FileBody
import org.apache.http.entity.mime.content.StringBody
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.util.EntityUtils
import org.gradle.api.GradleException

class TestFairyUploader {
    /**
     * Upload an APK using /api/upload REST service.
     *
     * @param project
     * @param extension
     * @param apkFilename
     * @return Object parsed json
     */
    public static def uploadApk(TestFairyExtension extension, String apkFilename, String mappingFilename, String optChangelog) {
        String serverEndpoint = extension.getServerEndpoint()
        String url = "${serverEndpoint}/api/upload"
        MultipartEntity entity = buildEntity(extension, apkFilename, mappingFilename)

        if (optChangelog) {
            entity.addPart('changelog', new StringBody(optChangelog))
        }

        return post(url, entity)
    }

    /**
     * Upload a signed APK using /api/upload-signed REST service.
     *
     * @param extension
     * @param apkFilename
     * @return Object parsed json
     */
    public static def uploadSignedApk(TestFairyExtension extension, String apkFilename) {
        String serverEndpoint = extension.getServerEndpoint()
        String url = "${serverEndpoint}/api/upload-signed"

        MultipartEntity entity = new MultipartEntity()
        entity.addPart('api_key', new StringBody(extension.getApiKey()))
        entity.addPart('apk_file', new FileBody(new File(apkFilename)))

        if (extension.getTestersGroups()) {
            // if omitted, no emails will be sent to testers
            entity.addPart('testers-groups', new StringBody(extension.getTestersGroups()))
        }

        // add notify "on" or "off"
        entity.addPart('notify', new StringBody(extension.getNotify() ? "on" : "off"))

        // add auto-update "on" or "off"
        entity.addPart('auto-update', new StringBody(extension.getAutoUpdate() ? "on" : "off"))

        return post(url, entity)
    }

    /**
     * Build MultipartEntity for API parameters on Upload of an APK
     *
     * @param extension
     * @return MultipartEntity
     */
    public static MultipartEntity buildEntity(TestFairyExtension extension, String apkFilename, String mappingFilename) {
        String apiKey = extension.getApiKey()

        MultipartEntity entity = new MultipartEntity()
        entity.addPart('api_key', new StringBody(apiKey))
        entity.addPart('apk_file', new FileBody(new File(apkFilename)))

        if (mappingFilename != null) {
            entity.addPart('symbols_file', new FileBody(new File(mappingFilename)))
        }

        if (extension.getIconWatermark()) {
            // if omitted, default value is "off"
            entity.addPart('icon-watermark', new StringBody("on"))
        }

        if (extension.getVideo()) {
            // if omitted, default value is "on"
            entity.addPart('video', new StringBody(extension.getVideo()))
        }

        if (extension.getVideoQuality()) {
            // if omitted, default value is "high"
            entity.addPart('video-quality', new StringBody(extension.getVideoQuality()))
        }

        if (extension.getVideoRate()) {
            // if omitted, default is 1 frame per second (videoRate = 1.0)
            entity.addPart('video-rate', new StringBody(extension.getVideoRate()))
        }

        if (extension.getMetrics()) {
            // if omitted, by default will record as much as possible
            entity.addPart('metrics', new StringBody(extension.getMetrics()))
        }

        if (extension.getMaxDuration()) {
            // override default value
            entity.addPart('max-duration', new StringBody(extension.getMaxDuration()))
        }

        if (extension.getRecordOnBackground()) {
            // enable record on background option
            entity.addPart('record-on-background', new StringBody("on"))
        }

        return entity
    }


    private static Object post(String url, MultipartEntity entity) {
        DefaultHttpClient httpClient = HttpClientFactory.buildHttpClient()
        HttpPost post = new HttpPost(url)
        post.addHeader("User-Agent", "TestFairy Gradle Plugin")
        post.setEntity(entity)
        HttpResponse response = httpClient.execute(post)

        String json = EntityUtils.toString(response.getEntity())
        def parser = new JsonSlurper()
        def parsed = parser.parseText(json)
        if (!parsed.status.equals("ok")) {
            throw new GradleException("Failed with json: " + json)
        }

        return parsed
    }
}
