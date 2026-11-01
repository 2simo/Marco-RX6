package com.marcorx6.library

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Html
import android.text.TextWatcher
import android.view.Gravity
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.regex.Pattern

/**
 * Marco RX6 v3 - Blogger import foundation.
 * Keeps the existing local library and supports importing/updating Blogger posts.
 */
data class Game(
    val name: String,
    val platform: String,
    val description: String,
    val url: String,
    val coverUrl: String = "",
    val coverLocalPath: String = "",
    val sourcePostUrl: String = "",
    val downloadLinks: String = "",
    val blogImported: Boolean = false,
    val platformManuallyCorrected: Boolean = false
)

class MainActivity : Activity() {

    private val bg = Color.rgb(10, 11, 17)
    private val panel = Color.rgb(27, 29, 39)
    private val red = Color.rgb(255, 55, 75)
    private val white = Color.WHITE
    private val gray = Color.LTGRAY
    private val platforms = listOf("ALL", "PSP", "PS1", "PS2", "PS3", "PS4")
    private val games = mutableListOf<Game>()
    private lateinit var gameList: LinearLayout
    private lateinit var search: EditText
    private lateinit var filters: LinearLayout
    private lateinit var countText: TextView
    private var selected = "ALL"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val PICK_IMAGE_REQUEST = 1001
    private var pendingCoverLocalPath = ""
    private var coverPreview: ImageView? = null
    private var coverUrlField: EditText? = null
    private var isSyncing = false

    private val bloggerFeedUrl =
        "https://marcorx6.blogspot.com/feeds/posts/default?alt=json&max-results=100"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = 0
        loadGames()
        createInterface()
        // Try a quiet sync whenever the app opens. Existing local games remain untouched.
        syncFromBlog(showResult = false)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun makeText(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
        }

    private fun makeInput(hintText: String): EditText = EditText(this).apply {
        hint = hintText
        textSize = 14f
        setTextColor(white)
        setHintTextColor(Color.GRAY)
        background = rounded(panel, 12)
        setPadding(dp(13), dp(10), dp(13), dp(10))
    }

    private fun createInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(12))
            setBackgroundColor(bg)
        }
        @Suppress("DEPRECATION")
        root.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(dp(18), dp(12) + insets.systemWindowInsetTop,
                dp(18), dp(12) + insets.systemWindowInsetBottom)
            insets
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(makeText("MARCO RX6", 25f, white, true))
        brand.addView(makeText("ULTIMATE GAMING LIBRARY", 10f, red, true))
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))

        val syncButton = TextView(this).apply {
            text = "↻ SYNC"
            textSize = 12f
            setTextColor(white)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(48, 53, 70), 13)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { syncFromBlog(showResult = true) }
        }
        header.addView(syncButton)

        val addButton = TextView(this).apply {
            text = "+ ADD"
            textSize = 13f
            setTextColor(white)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = rounded(red, 14)
            setPadding(dp(15), dp(12), dp(15), dp(12))
            setOnClickListener { showGameDialog() }
        }
        header.addView(addButton, LinearLayout.LayoutParams(-2, -2).apply {
            leftMargin = dp(8)
        })
        root.addView(header)

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(panel, 20)
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        hero.addView(makeText("CONNECTED TO MARCO RX6 BLOG", 10f, red, true))
        hero.addView(makeText("YOUR GAME.\nYOUR WORLD.", 27f, white, true))
        hero.addView(makeText("Sync new posts from your Blogger library.", 12f, gray))
        root.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })

        search = EditText(this).apply {
            hint = "Search games..."
            textSize = 14f
            setSingleLine(true)
            setTextColor(white)
            setHintTextColor(Color.GRAY)
            background = rounded(panel, 14)
            setPadding(dp(15), 0, dp(15), 0)
        }
        root.addView(search, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(16) })

        val filterScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        filters = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        platforms.forEach { platform ->
            val chip = TextView(this).apply {
                text = platform
                textSize = 12f
                gravity = Gravity.CENTER
                setTypeface(null, Typeface.BOLD)
                setPadding(dp(15), dp(11), dp(15), dp(11))
                setOnClickListener { selected = platform; renderGames() }
            }
            filters.addView(chip, LinearLayout.LayoutParams(-2, -2).apply {
                rightMargin = dp(8)
            })
        }
        filterScroll.addView(filters)
        root.addView(filterScroll, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(15); bottomMargin = dp(14)
        })

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(makeText("EXPLORE GAMES", 16f, white, true),
            LinearLayout.LayoutParams(0, -2, 1f))
        countText = makeText("0 GAMES", 10f, red, true)
        titleRow.addView(countText)
        root.addView(titleRow)

        val scroll = ScrollView(this)
        gameList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(14), 0, dp(12))
        }
        scroll.addView(gameList)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(makeText("MARCO RX6  •  GAMING EXPERIENCE", 9f, Color.GRAY).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(5))
        })
        setContentView(root)
        root.requestApplyInsets()

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { renderGames() }
            override fun afterTextChanged(s: Editable?) {}
        })
        renderGames()
    }

    // Blogger public Atom JSON feed: no API key or Blogger login is required for public posts.
    private fun syncFromBlog(showResult: Boolean) {
        if (isSyncing) {
            if (showResult) Toast.makeText(this, "Sync already running", Toast.LENGTH_SHORT).show()
            return
        }
        isSyncing = true
        if (showResult) Toast.makeText(this, "Connecting to Marco RX6 blog...", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val connection = URL(bloggerFeedUrl).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 20000
                connection.setRequestProperty("User-Agent", "MarcoRX6-Android/3.0")
                connection.setRequestProperty("Accept", "application/json")
                val body = try {
                    connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                } finally {
                    connection.disconnect()
                }
                val feed = JSONObject(body).optJSONObject("feed") ?: JSONObject()
                val entries = feed.optJSONArray("entry") ?: JSONArray()
                val imported = mutableListOf<Game>()
                for (i in 0 until entries.length()) {
                    val entry = entries.optJSONObject(i) ?: continue
                    val game = parseBlogEntry(entry) ?: continue
                    imported.add(game)
                }
                mainHandler.post {
                    val result = mergeImportedGames(imported)
                    isSyncing = false
                    renderGames()
                    if (showResult) {
                        Toast.makeText(this,
                            "Sync complete: ${result.first} added, ${result.second} updated",
                            Toast.LENGTH_LONG).show()
                    }
                    showPlatformWarnings(result.third)
                }
            } catch (e: Exception) {
                mainHandler.post {
                    isSyncing = false
                    if (showResult) Toast.makeText(this,
                        "Could not sync. Check internet and try again.", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun mergeImportedGames(imported: List<Game>): Triple<Int, Int, List<Pair<Game, String>>> {
        var added = 0
        var updated = 0
        val warnings = mutableListOf<Pair<Game, String>>()
        imported.forEach { incoming ->
            val byPost = games.indexOfFirst {
                it.sourcePostUrl.isNotBlank() && it.sourcePostUrl == incoming.sourcePostUrl
            }
            if (byPost >= 0) {
                val old = games[byPost]
                games[byPost] = incoming.copy(
                    coverLocalPath = old.coverLocalPath,
                    coverUrl = if (old.coverLocalPath.isNotBlank()) old.coverUrl else incoming.coverUrl,
                    platform = if (old.platformManuallyCorrected) old.platform else incoming.platform,
                    platformManuallyCorrected = old.platformManuallyCorrected
                )
                updated++
            } else {
                // Do not overwrite a game the user entered manually with the same title.
                val manualDuplicate = games.any {
                    !it.blogImported && it.name.equals(incoming.name, ignoreCase = true)
                }
                if (!manualDuplicate) {
                    games.add(incoming)
                    added++
                    val mismatch = findPlatformMismatch(incoming)
                    if (mismatch != null) warnings.add(incoming to mismatch)
                }
            }
        }
        saveGames()
        return Triple(added, updated, warnings)
    }

    private fun showPlatformWarnings(warnings: List<Pair<Game, String>>) {
        if (warnings.isEmpty() || isFinishing) return
        val (game, detected) = warnings.first()
        AlertDialog.Builder(this)
            .setTitle("Platform information conflict")
            .setMessage("${game.name}\n\nBlogger category: ${game.platform}\nOperating system field: $detected\n\nThe app has used the Blogger category. Would you like to correct the platform manually?")
            .setNegativeButton("KEEP CATEGORY", null)
            .setPositiveButton("CORRECT") { _, _ -> showPlatformCorrection(game) }
            .show()
    }

    private fun showPlatformCorrection(game: Game) {
        val choices = platforms.drop(1).toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Choose platform")
            .setItems(choices) { _, which ->
                val index = games.indexOfFirst { it.sourcePostUrl == game.sourcePostUrl }
                if (index >= 0) {
                    games[index] = games[index].copy(platform = choices[which], platformManuallyCorrected = true)
                    saveGames()
                    renderGames()
                }
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun findPlatformMismatch(game: Game): String? {
        val description = game.description
        val regex = Regex("(?i)(?:نظام\\s*التشغيل|operating\\s*system)\\s*[:：\\-]?\\s*(PlayStation\\s*[1-4]|PS[1-4]|PSP)")
        val found = regex.find(description)?.groupValues?.getOrNull(1) ?: return null
        val fieldPlatform = normalizePlatform(found) ?: return null
        return if (fieldPlatform != game.platform) fieldPlatform else null
    }

    private fun parseBlogEntry(entry: JSONObject): Game? {
        val title = entry.optJSONObject("title")?.optString("\$t", "")?.trim().orEmpty()
        if (title.isBlank()) return null
        val content = entry.optJSONObject("content")?.optString("\$t", "")
            ?: entry.optJSONObject("summary")?.optString("\$t", "").orEmpty()
        val postUrl = findPostUrl(entry)
        if (postUrl.isBlank()) return null
        val platform = platformFromCategories(entry) ?: return null
        val image = extractFirstImage(content)
        val cleanText = htmlToText(content)
        val links = extractDownloadLinks(content)
        return Game(
            name = title,
            platform = platform,
            description = cleanText.take(1800),
            url = postUrl,
            coverUrl = image,
            sourcePostUrl = postUrl,
            downloadLinks = links,
            blogImported = true
        )
    }

    private fun findPostUrl(entry: JSONObject): String {
        val links = entry.optJSONArray("link") ?: return ""
        for (i in 0 until links.length()) {
            val item = links.optJSONObject(i) ?: continue
            if (item.optString("rel") == "alternate") {
                val href = item.optString("href", "")
                if (href.startsWith("https://")) return href.substringBefore("?")
            }
        }
        return ""
    }

    private fun platformFromCategories(entry: JSONObject): String? {
        val categories = entry.optJSONArray("category") ?: return null
        for (i in 0 until categories.length()) {
            val term = categories.optJSONObject(i)?.optString("term", "") ?: continue
            val platform = normalizePlatform(term)
            if (platform != null) return platform
        }
        return null
    }

    private fun normalizePlatform(value: String): String? {
        val s = value.lowercase(Locale.ROOT).replace(" ", "").replace("-", "")
        return when {
            s == "psp" || s.contains("playstationportable") -> "PSP"
            s == "ps1" || s.contains("playstation1") || s.contains("playstationone") -> "PS1"
            s == "ps2" || s.contains("playstation2") -> "PS2"
            s == "ps3" || s.contains("playstation3") -> "PS3"
            s == "ps4" || s.contains("playstation4") -> "PS4"
            else -> null
        }
    }

    private fun extractFirstImage(html: String): String {
        val pattern = Pattern.compile("(?i)<img[^>]+(?:src|data-src)=[\\\"']([^\\\"']+)[\\\"']")
        val matcher = pattern.matcher(html)
        if (matcher.find()) {
            val src = matcher.group(1) ?: ""
            if (src.startsWith("https://") || src.startsWith("http://")) return src
        }
        return ""
    }

    private fun extractDownloadLinks(html: String): String {
        val result = JSONArray()
        val pattern = Pattern.compile("(?is)<a[^>]+href=[\\\"']([^\\\"']+)[\\\"'][^>]*>(.*?)</a>")
        val matcher = pattern.matcher(html)
        while (matcher.find()) {
            val href = matcher.group(1)?.trim().orEmpty()
            val labelHtml = matcher.group(2).orEmpty()
            @Suppress("DEPRECATION")
            val label = Html.fromHtml(labelHtml).toString().trim()
            if (href.startsWith("https://") && label.isNotBlank() &&
                (label.contains("اللعبة") || label.contains("التحديث") || label.contains("الإضافات") ||
                    label.contains("تحميل") || label.contains("Game", true) || label.contains("Update", true) ||
                    label.contains("DLC", true))) {
                result.put(JSONObject().put("label", label).put("url", href))
            }
        }
        return result.toString()
    }

    private fun htmlToText(html: String): String {
        val withBreaks = html.replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</p>|</div>|</li>|</h[1-6]>"), "\n")
        @Suppress("DEPRECATION")
        return Html.fromHtml(withBreaks).toString()
            .replace(Regex("[\\t ]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    // IMAGE LOADER: LOCAL OR INTERNET
    private fun loadCover(image: ImageView, localPath: String, imageUrl: String) {
        val key = localPath.ifBlank { imageUrl }
        image.tag = key
        image.setImageResource(android.R.drawable.ic_menu_gallery)
        if (key.isBlank()) return
        Thread {
            try {
                val bitmap = if (localPath.isNotBlank()) {
                    BitmapFactory.decodeFile(localPath)
                } else {
                    val connection = URL(imageUrl).openConnection() as HttpURLConnection
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000
                    connection.instanceFollowRedirects = true
                    try {
                        connection.connect()
                        connection.inputStream.use { BitmapFactory.decodeStream(it) }
                    } finally { connection.disconnect() }
                }
                if (bitmap != null) mainHandler.post {
                    if (image.tag == key) image.setImageBitmap(bitmap)
                }
            } catch (_: Exception) { }
        }.start()
    }

    private fun chooseCover() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    @Deprecated("Using activity result for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_IMAGE_REQUEST || resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return
        Thread {
            try {
                val folder = File(filesDir, "covers")
                if (!folder.exists()) folder.mkdirs()
                val destination = File(folder, "cover_${System.currentTimeMillis()}.img")
                contentResolver.openInputStream(uri).use { input ->
                    if (input == null) throw Exception("Cannot read image")
                    FileOutputStream(destination).use { output -> input.copyTo(output) }
                }
                mainHandler.post {
                    pendingCoverLocalPath = destination.absolutePath
                    coverUrlField?.setText("")
                    coverPreview?.let { loadCover(it, pendingCoverLocalPath, "") }
                    Toast.makeText(this, "Cover image selected", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                mainHandler.post { Toast.makeText(this, "Could not load this image", Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    private fun renderGames() {
        gameList.removeAllViews()
        for (i in 0 until filters.childCount) {
            val chip = filters.getChildAt(i) as TextView
            val active = chip.text.toString() == selected
            chip.background = rounded(if (active) red else panel, 15)
            chip.setTextColor(if (active) white else gray)
        }
        val query = search.text.toString().lowercase(Locale.ROOT).trim()
        val results = games.filter {
            val platformMatch = selected == "ALL" || it.platform == selected
            val searchMatch = it.name.lowercase(Locale.ROOT).contains(query) ||
                it.platform.lowercase(Locale.ROOT).contains(query) ||
                it.description.lowercase(Locale.ROOT).contains(query)
            platformMatch && searchMatch
        }
        countText.text = "${results.size} GAMES"
        if (results.isEmpty()) {
            gameList.addView(makeText("NO GAMES FOUND\n\nAdd a game or sync your blog.", 14f, gray).apply {
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(45), dp(10), dp(45))
            })
            return
        }
        results.forEach { game ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = rounded(panel, 16)
                setPadding(dp(11), dp(11), dp(11), dp(11))
                setOnClickListener { showDetails(game) }
            }
            val cover = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    intArrayOf(red, Color.rgb(75, 20, 45))).apply { cornerRadius = dp(12).toFloat() }
                contentDescription = "${game.name} cover"
            }
            card.addView(cover, LinearLayout.LayoutParams(dp(82), dp(95)))
            loadCover(cover, game.coverLocalPath, game.coverUrl)
            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, 0, 0)
            }
            info.addView(makeText(game.name, 15f, white, true))
            info.addView(makeText(game.platform + " • PLAYSTATION", 10f, red, true).apply {
                setPadding(0, dp(4), 0, dp(4))
            })
            if (game.blogImported) info.addView(makeText("BLOG IMPORT", 9f, Color.rgb(120, 190, 255), true))
            info.addView(makeText(game.description.ifBlank { "Tap to view details" }, 11f, gray))
            info.addView(makeText("VIEW DETAILS  ›", 10f, red, true).apply { setPadding(0, dp(7), 0, 0) })
            card.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
            gameList.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(11) })
        }
    }

    private fun showDetails(game: Game) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(10), dp(18), dp(5))
        }
        val cover = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(panel, 14)
        }
        box.addView(cover, LinearLayout.LayoutParams(dp(150), dp(175)))
        loadCover(cover, game.coverLocalPath, game.coverUrl)
        box.addView(makeText(game.name, 21f, white, true).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(5))
        })
        box.addView(makeText(game.platform, 13f, red, true))
        box.addView(makeText(game.description.ifBlank { "No description available." }, 12f, gray).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(12))
        })
        if (game.downloadLinks.isNotBlank()) {
            val links = try { JSONArray(game.downloadLinks) } catch (_: Exception) { JSONArray() }
            if (links.length() > 0) {
                box.addView(makeText("BLOG DOWNLOAD LINKS", 12f, white, true).apply {
                    setPadding(0, dp(8), 0, dp(6))
                })
                for (i in 0 until links.length()) {
                    val item = links.optJSONObject(i) ?: continue
                    val label = item.optString("label", "Open link")
                    val href = item.optString("url", "")
                    val linkButton = TextView(this).apply {
                        text = "↗  $label"
                        textSize = 13f
                        setTextColor(Color.rgb(120, 190, 255))
                        gravity = Gravity.CENTER
                        background = rounded(panel, 10)
                        setPadding(dp(10), dp(11), dp(10), dp(11))
                        setOnClickListener { openGameLink(href) }
                    }
                    box.addView(linkButton, LinearLayout.LayoutParams(-1, -2).apply {
                        bottomMargin = dp(6)
                    })
                }
            }
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (game.blogImported) "BLOG GAME DETAILS" else "GAME DETAILS")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("OPEN POST", null)
            .setNeutralButton("EDIT", null)
            .setNegativeButton("DELETE", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { openGameLink(game.url) }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                dialog.dismiss(); showGameDialog(game)
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                dialog.dismiss(); deleteGame(game)
            }
        }
        dialog.show()
    }

    private fun showGameDialog(existing: Game? = null) {
        pendingCoverLocalPath = existing?.coverLocalPath ?: ""
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(5), dp(18), 0)
        }
        val name = makeInput("Game name")
        val description = makeInput("Description")
        val link = makeInput("HTTPS game or post link")
        val coverUrl = makeInput("Cover image URL (optional)")
        name.setSingleLine(true); link.setSingleLine(true); coverUrl.setSingleLine(true)
        coverUrlField = coverUrl
        val platform = Spinner(this)
        platform.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, platforms.drop(1))
        if (existing != null) {
            name.setText(existing.name); description.setText(existing.description)
            link.setText(existing.url); coverUrl.setText(existing.coverUrl)
            val position = platforms.drop(1).indexOf(existing.platform)
            if (position >= 0) platform.setSelection(position)
        }
        val preview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(panel, 14)
        }
        coverPreview = preview
        box.addView(preview, LinearLayout.LayoutParams(-1, dp(150)).apply { bottomMargin = dp(10) })
        loadCover(preview, pendingCoverLocalPath, existing?.coverUrl ?: "")
        box.addView(Button(this).apply {
            text = "CHOOSE IMAGE FROM PHONE"
            setTextColor(white); background = rounded(red, 12)
            setOnClickListener { chooseCover() }
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        box.addView(TextView(this).apply {
            text = "REMOVE COVER IMAGE"; textSize = 12f; gravity = Gravity.CENTER
            setTextColor(red); setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener {
                pendingCoverLocalPath = ""; coverUrl.setText("")
                preview.setImageResource(android.R.drawable.ic_menu_gallery)
            }
        })
        box.addView(name); box.addView(platform); box.addView(description); box.addView(link); box.addView(coverUrl)
        val scroll = ScrollView(this); scroll.addView(box)
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "ADD NEW GAME" else "EDIT GAME")
            .setView(scroll).setNegativeButton("CANCEL", null).setPositiveButton("SAVE", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val gameName = name.text.toString().trim()
                val gameLink = link.text.toString().trim()
                val coverLink = coverUrl.text.toString().trim()
                if (gameName.isBlank()) { name.error = "Enter game name"; return@setOnClickListener }
                if (!gameLink.startsWith("https://")) { link.error = "Enter a valid HTTPS link"; return@setOnClickListener }
                if (coverLink.isNotBlank() && !coverLink.startsWith("https://")) {
                    coverUrl.error = "Use an HTTPS image link"; return@setOnClickListener
                }
                val localPath = if (coverLink.isNotBlank()) "" else pendingCoverLocalPath
                val newGame = Game(
                    name = gameName,
                    platform = platform.selectedItem.toString(),
                    description = description.text.toString().trim(),
                    url = gameLink,
                    coverUrl = coverLink,
                    coverLocalPath = localPath,
                    sourcePostUrl = existing?.sourcePostUrl ?: "",
                    downloadLinks = existing?.downloadLinks ?: "",
                    blogImported = existing?.blogImported ?: false,
                    platformManuallyCorrected = existing?.platformManuallyCorrected ?: false
                )
                if (existing == null) games.add(newGame) else {
                    val index = games.indexOf(existing)
                    if (index >= 0) {
                        games[index] = newGame
                        if (existing.coverLocalPath.isNotBlank() && existing.coverLocalPath != localPath) {
                            File(existing.coverLocalPath).delete()
                        }
                    }
                }
                saveGames(); renderGames(); dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun deleteGame(game: Game) {
        AlertDialog.Builder(this).setTitle("DELETE GAME")
            .setMessage("Do you want to delete ${game.name}?")
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("DELETE") { _, _ ->
                games.remove(game)
                if (game.coverLocalPath.isNotBlank()) File(game.coverLocalPath).delete()
                saveGames(); renderGames()
                Toast.makeText(this, "Game deleted", Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun openGameLink(link: String) {
        if (link.isBlank()) {
            Toast.makeText(this, "No link available", Toast.LENGTH_SHORT).show(); return
        }
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
        catch (_: Exception) { Toast.makeText(this, "Cannot open this link", Toast.LENGTH_SHORT).show() }
    }

    private fun saveGames() {
        val array = JSONArray()
        games.forEach { game ->
            array.put(JSONObject()
                .put("name", game.name)
                .put("platform", game.platform)
                .put("description", game.description)
                .put("url", game.url)
                .put("coverUrl", game.coverUrl)
                .put("coverLocalPath", game.coverLocalPath)
                .put("sourcePostUrl", game.sourcePostUrl)
                .put("downloadLinks", game.downloadLinks)
                .put("blogImported", game.blogImported)
                .put("platformManuallyCorrected", game.platformManuallyCorrected))
        }
        getSharedPreferences("games_library", MODE_PRIVATE).edit()
            .putString("games", array.toString()).apply()
    }

    private fun loadGames() {
        val saved = getSharedPreferences("games_library", MODE_PRIVATE)
            .getString("games", null) ?: return
        try {
            val array = JSONArray(saved)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                games.add(Game(
                    item.optString("name"),
                    item.optString("platform"),
                    item.optString("description"),
                    item.optString("url"),
                    item.optString("coverUrl", ""),
                    item.optString("coverLocalPath", ""),
                    item.optString("sourcePostUrl", ""),
                    item.optString("downloadLinks", ""),
                    item.optBoolean("blogImported", false),
                    item.optBoolean("platformManuallyCorrected", false)
                ))
            }
        } catch (_: Exception) { games.clear() }
    }
}
