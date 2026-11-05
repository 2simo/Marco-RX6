package com.marcorx6.library.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadFileNamesTest {
    @Test
    fun parsesRfc5987FilenameWithLanguageAndPreservesLiteralPlus() {
        val result = DownloadFileNames.fromResponse(
            "attachment; filename*=UTF-8'en'Marco+RX6%20%E2%9C%93.zip",
            "https://example.org/download"
        )
        assertEquals("Marco+RX6 ✓.zip", result)
    }

    @Test
    fun parsesQuotedFilenameContainingSpacesAndSemicolon() {
        val result = DownloadFileNames.fromResponse(
            """attachment; filename="Game Collection; Disc 1.iso"""",
            "https://example.org/download"
        )
        assertEquals("Game Collection; Disc 1.iso", result)
    }

    @Test
    fun fallsBackToRegularFilenameWhenExtendedValueIsMalformed() {
        val result = DownloadFileNames.fromResponse(
            "attachment; filename*=UTF-8''%ZZ; filename=backup.zip",
            "https://example.org/download"
        )
        assertEquals("backup.zip", result)
    }
}
