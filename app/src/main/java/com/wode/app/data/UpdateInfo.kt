package com.wode.app.data

data class UpdateInfo(
    val versionName: String,
    val tagName: String,
    val releaseName: String,
    val body: String,
    val apkName: String,
    val apkDownloadUrl: String,
    val htmlUrl: String,
)
