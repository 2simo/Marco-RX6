
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
import android.view.Gravity
import android.widget.*
import android.text.Editable
import android.text.TextWatcher
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class Game(
    val name: String,
    val platform: String,
    val description: String,
    val url: String,
    val coverUrl: String = "",
    val coverLocalPath: String = ""
)

class MainActivity : Activity() {

    private val bg = Color.rgb(10, 11, 17)
    private val panel = Color.rgb(27, 29, 39)
    private val red = Color.rgb(255, 55, 75)
    private val white = Color.WHITE
    private val gray = Color.LTGRAY

    private val platforms =
        listOf("ALL", "PSP", "PS1", "PS2", "PS3", "PS4")

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = bg
        window.navigationBarColor = bg

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = 0

        loadGames()
        createInterface()
    }

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }

    private fun makeText(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(null, Typeface.BOLD)
    }

    private fun makeInput(hintText: String): EditText =
        EditText(this).apply {
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
            val top = insets.systemWindowInsetTop
            val bottom = insets.systemWindowInsetBottom

            view.setPadding(
                dp(18),
                dp(12) + top,
                dp(18),
                dp(12) + bottom
            )
            insets
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        brand.addView(makeText("MARCO RX6", 25f, white, true))
        brand.addView(
            makeText("ULTIMATE GAMING LIBRARY", 10f, red, true)
        )

        header.addView(
            brand,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        val addButton = TextView(this).apply {
            text = "+ ADD"
            textSize = 13f
            setTextColor(white)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = rounded(red, 14)
            setPadding(dp(17), dp(12), dp(17), dp(12))
            setOnClickListener { showGameDialog() }
        }

        header.addView(addButton)
        root.addView(header)

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(panel, 20)
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }

        hero.addView(makeText("WELCOME TO MARCO RX6", 10f, red, true))
        hero.addView(
            makeText("YOUR GAME.\nYOUR WORLD.", 27f, white, true)
        )
        hero.addView(
            makeText(
                "Discover your PlayStation collection.",
                12f,
                gray
            )
        )

        root.addView(
            hero,
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(18)
            }
        )

        search = EditText(this).apply {
            hint = "Search games..."
            textSize = 14f
            setSingleLine(true)
            setTextColor(white)
            setHintTextColor(Color.GRAY)
            background = rounded(panel, 14)
            setPadding(dp(15), 0, dp(15), 0)
        }

        root.addView(
            search,
            LinearLayout.LayoutParams(-1, dp(50)).apply {
                topMargin = dp(16)
            }
        )

        val filterScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }

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

                setOnClickListener {
                    selected = platform
                    renderGames()
                }
            }

            filters.addView(
                chip,
                LinearLayout.LayoutParams(-2, -2).apply {
                    rightMargin = dp(8)
                }
            )
        }

        filterScroll.addView(filters)

        root.addView(
            filterScroll,
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(15)
                bottomMargin = dp(14)
            }
        )

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        titleRow.addView(
            makeText("EXPLORE GAMES", 16f, white, true),
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        countText = makeText("0 GAMES", 10f, red, true)
        titleRow.addView(countText)
        root.addView(titleRow)

        val scroll = ScrollView(this)

        gameList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(14), 0, dp(12))
        }

        scroll.addView(gameList)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        root.addView(
            makeText(
                "MARCO RX6  •  GAMING EXPERIENCE",
                9f,
                Color.GRAY
            ).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(10), 0, dp(5))
            }
        )

        setContentView(root)
        root.requestApplyInsets()

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?, start: Int,
                count: Int, after: Int
            ) {}

            override fun onTextChanged(
                s: CharSequence?, start: Int,
                before: Int, count: Int
            ) {
                renderGames()
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        renderGames()
    }

    // IMAGE LOADER: LOCAL OR INTERNET
    private fun loadCover(
        image: ImageView,
        localPath: String,
        imageUrl: String
    ) {
        val key = localPath.ifBlank { imageUrl }
        image.tag = key

        image.setImageResource(android.R.drawable.ic_menu_gallery)

        if (key.isBlank()) return

        Thread {
            try {
                val bitmap = if (localPath.isNotBlank()) {
                    BitmapFactory.decodeFile(localPath)
                } else {
                    val connection =
                        URL(imageUrl).openConnection() as HttpURLConnection

                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000
                    connection.instanceFollowRedirects = true

                    try {
                        connection.connect()
                        connection.inputStream.use {
                            BitmapFactory.decodeStream(it)
                        }
                    } finally {
                        connection.disconnect()
                    }
                }

                if (bitmap != null) {
                    mainHandler.post {
                        if (image.tag == key) {
                            image.setImageBitmap(bitmap)
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }.start()
    }

    // IMAGE PICKER
    private fun chooseCover() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }

        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    @Deprecated("Using activity result for compatibility")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != PICK_IMAGE_REQUEST ||
            resultCode != RESULT_OK
        ) return

        val uri: Uri = data?.data ?: return

        Thread {
            try {
                val folder = File(filesDir, "covers")
                if (!folder.exists()) folder.mkdirs()

                val destination = File(
                    folder,
                    "cover_${System.currentTimeMillis()}.img"
                )

                contentResolver.openInputStream(uri).use { input ->
                    if (input == null) throw Exception("Cannot read image")
                    FileOutputStream(destination).use { output ->
                        input.copyTo(output)
                    }
                }

                mainHandler.post {
                    pendingCoverLocalPath = destination.absolutePath
                    coverUrlField?.setText("")
                    coverPreview?.let {
                        loadCover(it, pendingCoverLocalPath, "")
                    }
                    Toast.makeText(
                        this,
                        "Cover image selected",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (_: Exception) {
                mainHandler.post {
                    Toast.makeText(
                        this,
                        "Could not load this image",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }.start()
    }

    // DISPLAY GAMES
    private fun renderGames() {

        gameList.removeAllViews()

        for (i in 0 until filters.childCount) {
            val chip = filters.getChildAt(i) as TextView
            val active = chip.text.toString() == selected

            chip.background = rounded(
                if (active) red else panel,
                15
            )

            chip.setTextColor(if (active) white else gray)
        }

        val query = search.text.toString().lowercase().trim()

        val results = games.filter {
            val platformMatch =
                selected == "ALL" || it.platform == selected

            val searchMatch =
                it.name.lowercase().contains(query) ||
                it.platform.lowercase().contains(query) ||
                it.description.lowercase().contains(query)

            platformMatch && searchMatch
        }

        countText.text = "${results.size} GAMES"

        if (results.isEmpty()) {
            gameList.addView(
                makeText(
                    "NO GAMES FOUND\n\nAdd your first game using + ADD",
                    14f,
                    gray
                ).apply {
                    gravity = Gravity.CENTER
                    setPadding(dp(10), dp(45), dp(10), dp(45))
                }
            )
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
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(red, Color.rgb(75, 20, 45))
                ).apply {
                    cornerRadius = dp(12).toFloat()
                }
                contentDescription = "${game.name} cover"
            }

            card.addView(
                cover,
                LinearLayout.LayoutParams(dp(82), dp(95))
            )

            loadCover(cover, game.coverLocalPath, game.coverUrl)

            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, 0, 0)
            }

            info.addView(makeText(game.name, 15f, white, true))

            info.addView(
                makeText(
                    game.platform + " • PLAYSTATION",
                    10f,
                    red,
                    true
                ).apply {
                    setPadding(0, dp(4), 0, dp(4))
                }
            )

            info.addView(
                makeText(
                    game.description.ifBlank {
                        "Tap to view details"
                    },
                    11f,
                    gray
                )
            )

            info.addView(
                makeText("VIEW DETAILS  ›", 10f, red, true).apply {
                    setPadding(0, dp(7), 0, 0)
                }
            )

            card.addView(
                info,
                LinearLayout.LayoutParams(0, -2, 1f)
            )

            gameList.addView(
                card,
                LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = dp(11)
                }
            )
        }
    }

    // GAME DETAILS
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

        box.addView(
            cover,
            LinearLayout.LayoutParams(dp(150), dp(175))
        )

        loadCover(cover, game.coverLocalPath, game.coverUrl)

        box.addView(
            makeText(game.name, 21f, white, true).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(12), 0, dp(5))
            }
        )

        box.addView(makeText(game.platform, 13f, red, true))

        box.addView(
            makeText(
                game.description.ifBlank {
                    "No description available."
                },
                13f,
                gray
            ).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(12), 0, dp(12))
            }
        )

        val dialog = AlertDialog.Builder(this)
            .setTitle("GAME DETAILS")
            .setView(box)
            .setPositiveButton("OPEN LINK", null)
            .setNeutralButton("EDIT", null)
            .setNegativeButton("DELETE", null)
            .create()

        dialog.setOnShowListener {

            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    openGameLink(game.url)
                }

            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                .setOnClickListener {
                    dialog.dismiss()
                    showGameDialog(game)
                }

            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                .setOnClickListener {
                    dialog.dismiss()
                    deleteGame(game)
                }
        }

        dialog.show()
    }

    // ADD OR EDIT GAME
    private fun showGameDialog(existing: Game? = null) {

        pendingCoverLocalPath = existing?.coverLocalPath ?: ""

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(5), dp(18), 0)
        }

        val name = makeInput("Game name")
        val description = makeInput("Description")
        val link = makeInput("HTTPS game link")
        val coverUrl = makeInput("Cover image URL (optional)")

        name.setSingleLine(true)
        link.setSingleLine(true)
        coverUrl.setSingleLine(true)

        coverUrlField = coverUrl

        val platform = Spinner(this)
        platform.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            platforms.drop(1)
        )

        if (existing != null) {
            name.setText(existing.name)
            description.setText(existing.description)
            link.setText(existing.url)
            coverUrl.setText(existing.coverUrl)

            val position = platforms.drop(1)
                .indexOf(existing.platform)

            if (position >= 0) {
                platform.setSelection(position)
            }
        }

        val preview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(panel, 14)
        }

        coverPreview = preview

        box.addView(
            preview,
            LinearLayout.LayoutParams(-1, dp(150)).apply {
                bottomMargin = dp(10)
            }
        )

        loadCover(
            preview,
            pendingCoverLocalPath,
            existing?.coverUrl ?: ""
        )

        val chooseButton = Button(this).apply {
            text = "CHOOSE IMAGE FROM PHONE"
            setTextColor(white)
            background = rounded(red, 12)
            setOnClickListener { chooseCover() }
        }

        box.addView(
            chooseButton,
            LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(6)
            }
        )

        val removeButton = TextView(this).apply {
            text = "REMOVE COVER IMAGE"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(red)
            setPadding(dp(8), dp(8), dp(8), dp(8))

            setOnClickListener {
                pendingCoverLocalPath = ""
                coverUrl.setText("")
                preview.setImageResource(
                    android.R.drawable.ic_menu_gallery
                )
            }
        }

        box.addView(removeButton)
        box.addView(name)
        box.addView(platform)
        box.addView(description)
        box.addView(link)
        box.addView(coverUrl)

        val scroll = ScrollView(this)
        scroll.addView(box)

        val dialog = AlertDialog.Builder(this)
            .setTitle(
                if (existing == null) "ADD NEW GAME"
                else "EDIT GAME"
            )
            .setView(scroll)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("SAVE", null)
            .create()

        dialog.setOnShowListener {

            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {

                    val gameName = name.text.toString().trim()
                    val gameLink = link.text.toString().trim()
                    val coverLink = coverUrl.text.toString().trim()

                    if (gameName.isBlank()) {
                        name.error = "Enter game name"
                        return@setOnClickListener
                    }

                    if (!gameLink.startsWith("https://")) {
                        link.error = "Enter a valid HTTPS link"
                        return@setOnClickListener
                    }

                    if (
                        coverLink.isNotBlank() &&
                        !coverLink.startsWith("https://")
                    ) {
                        coverUrl.error = "Use an HTTPS image link"
                        return@setOnClickListener
                    }

                    val localPath =
                        if (coverLink.isNotBlank()) ""
                        else pendingCoverLocalPath

                    val newGame = Game(
                        gameName,
                        platform.selectedItem.toString(),
                        description.text.toString().trim(),
                        gameLink,
                        coverLink,
                        localPath
                    )

                    if (existing == null) {
                        games.add(newGame)
                    } else {
                        val index = games.indexOf(existing)
                        if (index >= 0) {
                            games[index] = newGame

                            if (
                                existing.coverLocalPath.isNotBlank() &&
                                existing.coverLocalPath != localPath
                            ) {
                                File(existing.coverLocalPath).delete()
                            }
                        }
                    }

                    saveGames()
                    renderGames()
                    dialog.dismiss()
                }
        }

        dialog.show()
    }

    // DELETE GAME
    private fun deleteGame(game: Game) {

        AlertDialog.Builder(this)
            .setTitle("DELETE GAME")
            .setMessage("Do you want to delete ${game.name}?")
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("DELETE") { _, _ ->

                games.remove(game)

                if (game.coverLocalPath.isNotBlank()) {
                    File(game.coverLocalPath).delete()
                }

                saveGames()
                renderGames()

                Toast.makeText(
                    this,
                    "Game deleted",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .show()
    }

    // OPEN GAME LINK
    private fun openGameLink(link: String) {

        if (link.isBlank()) {
            Toast.makeText(
                this,
                "No game link available",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        try {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(link))
            )
        } catch (_: Exception) {
            Toast.makeText(
                this,
                "Cannot open this link",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // SAVE GAMES
    private fun saveGames() {

        val array = JSONArray()

        games.forEach { game ->
            array.put(
                JSONObject()
                    .put("name", game.name)
                    .put("platform", game.platform)
                    .put("description", game.description)
                    .put("url", game.url)
                    .put("coverUrl", game.coverUrl)
                    .put("coverLocalPath", game.coverLocalPath)
            )
        }

        getSharedPreferences(
            "games_library",
            MODE_PRIVATE
        ).edit()
            .putString("games", array.toString())
            .apply()
    }

    // LOAD GAMES - OLD DATA IS SUPPORTED
    private fun loadGames() {

        val saved = getSharedPreferences(
            "games_library",
            MODE_PRIVATE
        ).getString("games", null) ?: return

        try {
            val array = JSONArray(saved)

            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)

                games.add(
                    Game(
                        item.optString("name"),
                        item.optString("platform"),
                        item.optString("description"),
                        item.optString("url"),
                        item.optString("coverUrl", ""),
                        item.optString("coverLocalPath", "")
                    )
                )
            }
        } catch (_: Exception) {
            games.clear()
        }
    }
}
