package com.wode.app.service

import android.net.Uri
import java.io.File
import java.io.OutputStream

data class RestoreTarget(
    val output: OutputStream,
    val displayPath: String,
    val installUri: Uri?,
    val file: File?,
)
