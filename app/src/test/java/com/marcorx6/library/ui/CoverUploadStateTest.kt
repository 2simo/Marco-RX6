package com.marcorx6.library.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CoverUploadStateTest {

    @Test
    fun beginSelectionClearsOldUrlAndResetsFailure() {
        val state = CoverUploadState()
        state.pendingPublicUrl = "https://example.test/old.jpg"
        state.failed = true

        state.beginSelection()

        assertEquals("", state.pendingPublicUrl)
        assertEquals(true, state.inProgress)
        assertEquals(false, state.failed)
    }

    @Test
    fun successfulUploadStoresLocalAndPublicPathsAndStopsProgress() {
        val state = CoverUploadState()

        state.beginSelection()
        state.uploadSucceeded("/covers/new.jpg", "https://example.test/new.jpg")

        assertEquals("/covers/new.jpg", state.pendingLocalPath)
        assertEquals("https://example.test/new.jpg", state.pendingPublicUrl)
        assertEquals(false, state.inProgress)
        assertEquals(false, state.failed)
    }

    @Test
    fun failedUploadStopsProgressAndCanBeReset() {
        val state = CoverUploadState()
        state.beginSelection()

        state.uploadFailed()
        assertEquals(false, state.inProgress)
        assertEquals(true, state.failed)

        state.resetFailure()
        assertEquals(false, state.failed)
    }

    @Test
    fun clearResetsTemporaryCoverValues() {
        val state = CoverUploadState()
        state.pendingLocalPath = "/covers/old.jpg"
        state.pendingPublicUrl = "https://example.test/old.jpg"
        state.inProgress = true
        state.failed = true

        state.clear()

        assertEquals("", state.pendingLocalPath)
        assertEquals("", state.pendingPublicUrl)
        assertEquals(false, state.inProgress)
        assertEquals(false, state.failed)
    }
}
