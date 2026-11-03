package com.marcorx6.library.ui

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.widget.ImageView
import android.widget.TextView
import com.marcorx6.library.data.Game
import com.marcorx6.library.data.GameLibraryStore
import java.util.concurrent.Executor

/** Composition root for Blogger post composition, labels, saving, and deletion UI. */
internal class BloggerPostFeature(
    activity: Activity,
    services: BloggerServices,
    gameLibraryStore: GameLibraryStore,
    isAdminSession: () -> Boolean,
    bloggerBlogId: () -> String,
    dp: (Int) -> Int,
    bg: () -> Int,
    panel: () -> Int,
    white: () -> Int,
    gray: () -> Int,
    red: () -> Int,
    makeText: (String, Float, Int, Boolean) -> TextView,
    makeInput: (String) -> android.widget.EditText,
    rounded: (Int, Int) -> GradientDrawable,
    accentForeground: () -> Int,
    executeIo: (() -> Unit) -> Unit,
    postToUi: (() -> Unit) -> Unit,
    styleAlertDialog: (android.app.AlertDialog) -> Unit,
    applyUnifiedSkin: (android.view.View) -> Unit,
    setCoverFields: (String, ImageView) -> Unit,
    pendingCoverLocalPath: () -> String,
    setPendingCoverLocalPath: (String) -> Unit,
    pendingCoverPublicUrl: () -> String,
    setPendingCoverPublicUrl: (String) -> Unit,
    coverUploadInProgress: () -> Boolean,
    loadCover: (ImageView, String, String) -> Unit,
    chooseCover: () -> Unit,
    deleteCoverFileIfUnused: (String) -> Unit,
    resetCoverUploadFailed: () -> Unit,
    saveGames: () -> Unit,
    renderGames: () -> Unit
) {
    private val labelsController = GamePostLabelsController(
        activity = activity,
        white = white,
        panel = panel,
        rounded = rounded,
        fetchLabels = services.posts::fetchLabels,
        executeIo = executeIo,
        postToUi = postToUi,
        styleAlertDialog = styleAlertDialog
    )

    private val formFieldsFactory = BloggerPostFormFieldsFactory(
        activity = activity,
        dp = dp,
        makeInput = makeInput,
        rounded = rounded,
        panel = panel
    )

    private val saveController = BloggerPostSaveController(
        executeIo = executeIo,
        postToUi = postToUi,
        savePost = services.posts::savePost
    )

    private val previewFactory = BloggerArticlePreviewFactory(
        activity = activity,
        backgroundColor = bg,
        isDarkMode = { bg() == android.graphics.Color.BLACK || bg() == android.graphics.Color.rgb(7, 13, 25) }
    )

    private val composer = BloggerPostComposerController(
        activity = activity,
        isAdminSession = isAdminSession,
        dp = dp,
        bg = bg,
        panel = panel,
        white = white,
        gray = gray,
        red = red,
        makeText = makeText,
        rounded = rounded,
        accentForeground = accentForeground,
        labelsController = labelsController,
        formFieldsFactory = formFieldsFactory,
        setCoverFields = setCoverFields,
        pendingCoverLocalPath = pendingCoverLocalPath,
        setPendingCoverLocalPath = setPendingCoverLocalPath,
        pendingCoverPublicUrl = pendingCoverPublicUrl,
        setPendingCoverPublicUrl = setPendingCoverPublicUrl,
        coverUploadInProgress = coverUploadInProgress,
        loadCover = loadCover,
        chooseCover = chooseCover,
        previewFactory = previewFactory,
        styleAlertDialog = styleAlertDialog,
        postFactory = BloggerGamePostFactory(),
        saveController = saveController,
        upsertGame = { saved, existing -> gameLibraryStore.upsert(saved, existing) },
        deleteCoverFileIfUnused = deleteCoverFileIfUnused,
        resetCoverUploadFailed = resetCoverUploadFailed,
        saveGames = saveGames,
        renderGames = renderGames,
        applyUnifiedSkin = applyUnifiedSkin
    )

    private val deletion = GameDeletionController(
        activity = activity,
        isAdminSession = isAdminSession,
        bloggerBlogId = bloggerBlogId,
        deletePost = services.posts::deletePost,
        removeGame = gameLibraryStore::remove,
        deleteCoverFileIfUnused = deleteCoverFileIfUnused,
        renderGames = renderGames,
        executeIo = executeIo,
        postToUi = postToUi,
        styleAlertDialog = styleAlertDialog
    )

    fun showComposer(existing: Game? = null) = composer.show(existing)

    fun delete(game: Game) = deletion.delete(game)
}
