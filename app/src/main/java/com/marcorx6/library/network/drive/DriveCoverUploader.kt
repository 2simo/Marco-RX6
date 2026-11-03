package com.marcorx6.library.network

import java.io.File

/** Narrow Drive transport contract consumed by cover data repositories. */
internal interface DriveCoverUploader {
    fun uploadCover(file: File, accessToken: String): String
}
