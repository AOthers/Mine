package com.wode.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class UpdateServiceTest {
    @Test
    fun parseGitHubRelease_findsApkAssetWithoutReflectiveListInstantiation() {
        val json = """
            {
              "tag_name": "v1.4",
              "name": "Release v1.4",
              "body": "Bug fixes",
              "html_url": "https://github.com/AOthers/Mine/releases/tag/v1.4",
              "assets": [
                {
                  "name": "notes.txt",
                  "browser_download_url": "https://example.com/notes.txt"
                },
                {
                  "name": "Mine-v1.4.apk",
                  "browser_download_url": "https://example.com/Mine-v1.4.apk"
                }
              ]
            }
        """.trimIndent()

        val release = UpdateService.parseGitHubRelease(json)

        assertNotNull(release)
        assertEquals("v1.4", release?.tagName)
        assertEquals("Release v1.4", release?.name)
        assertEquals("Mine-v1.4.apk", release?.apkName)
        assertEquals("https://example.com/Mine-v1.4.apk", release?.apkDownloadUrl)
    }

    @Test
    fun parseGitHubRelease_acceptsAnyApkAssetName() {
        val json = """
            {
              "tag_name": "v1.4",
              "name": "Release v1.4",
              "html_url": "https://github.com/AOthers/Mine/releases/tag/v1.4",
              "assets": [
                {
                  "name": "app-release.apk",
                  "browser_download_url": "https://example.com/app-release.apk"
                }
              ]
            }
        """.trimIndent()

        val release = UpdateService.parseGitHubRelease(json)

        assertEquals("app-release.apk", release?.apkName)
        assertEquals("https://example.com/app-release.apk", release?.apkDownloadUrl)
    }

    @Test
    fun parseGitHubAssets_acceptsSingleApkWithAnyName() {
        val json = """
            [
              {
                "name": "release-build.apk",
                "browser_download_url": "https://example.com/release-build.apk"
              }
            ]
        """.trimIndent()

        val assets = UpdateService.parseGitHubAssets(json)

        assertEquals(1, assets.size)
        assertEquals("release-build.apk", assets.first().name)
        assertEquals("https://example.com/release-build.apk", assets.first().downloadUrl)
    }
}
