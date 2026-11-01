
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
import android.view.View
import android.view.WindowInsets
import android.widget.*
import android.text.Editable
import android.text.TextWatcher
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Game(
    val name: String,
    val platform: String,
    val description: String,
    val url: String,
    val coverUrl: String = ""
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

        // FIX STATUS BAR AND PHONE INSETS
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

        // HEADER
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        brand.addView(
            makeText("MARCO RX6", 25f, white, true)
        )

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

        // HERO
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(panel, 20)
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }

        hero.addView(
            makeText("WELCOME TO MARCO RX6", 10f, red, true)
        )

        hero.addView(
            makeText(
                "YOUR GAME.\nYOUR WORLD.",
                27f,
                white,
                true
            )
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

        // SEARCH
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

        // FILTERS
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

        // LIBRARY TITLE
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

        // GAME LIST
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

    // IMAGE LOADER
    private fun loadCover(image: ImageView, imageUrl: String) {

        if (imageUrl.isBlank()) return

        image.tag = imageUrl

        Thread {
            try {
                val connection =
                    URL(imageUrl).openConnection() as HttpURLConnection

                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.instanceFollowRedirects = true
                connection.connect()

                val bitmap = connection.inputStream.use {
                    BitmapFactory.decodeStream(it)
                }

                connection.disconnect()

                if (bitmap != null) {
                    mainHandler.post {
                        if (image.tag == imageUrl) {
                            image.setImageBitmap(bitmap)
                        }
                    }
                }

            } catch (_: Exception) {
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

            chip.setTextColor(
                if (active) white else gray
            )
        }

        val query = search.text.toString().lowercase().trim()

        val results = games.filter {

            val platformMatch =
                selected == "ALL" || it.platform == selected

            val searchMatch =
                it.name.lowercase().contains(query) ||
                it.platform.lowercase().contains(query)

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

            // COVER
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

            if (game.coverUrl.isNotBlank()) {
                loadCover(cover, game.coverUrl)
            }

            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, 0, 0)
            }

            info.addView(
                makeText(game.name, 15f, white, true)
            )

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
                    game.description.ifBlank { "Tap to view details" },
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

        loadCover(cover, game.coverUrl)

        box.addView(
            makeText(game.name, 21f, white, true).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(12), 0, dp(5))
            }
        )

        box.addView(
            makeText(game.platform, 13f, red, true)
        )

        box.addView(
            makeText(
                game.description.ifBlank { "No description available." },
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
            .setNegativeButton("CLOSE", null)
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
        }

        dialog.show()
    }

    // ADD OR EDIT GAME
    private fun showGameDialog(existing: Game? = null) {

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(5), dp(18), 0)
        }

        val name = makeInput("Game name")
        val description = makeInput("Description")
        val link = makeInput("HTTPS game link")
        val cover = makeInput("Cover image URL (optional)")

        name.setSingleLine(true)
        link.setSingleLine(true)
        cover.setSingleLine(true)

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
            cover.setText(existing.coverUrl)

            val position = platforms.drop(1)
                .indexOf(existing.platform)

            if (position >= 0) {
                platform.setSelection(position)
            }
        }

        box.addView(name)
        box.addView(platform)
        box.addView(description)
        box.addView(link)
        box.addView(cover)

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "ADD NEW GAME" else "EDIT GAME")
            .setView(box)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("SAVE", null)
            .create()

        dialog.setOnShowListener {

            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {

                    val gameName = name.text.toString().trim()
                    val gameLink = link.text.toString().trim()
                    val coverLink = cover.text.toString().trim()

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
                        cover.error = "Use an HTTPS image link"
                        return@setOnClickListener
                    }

                    val newGame = Game(
                        gameName,
                        platform.selectedItem.toString(),
                        description.text.toString().trim(),
                        gameLink,
                        coverLink
                    )

                    if (existing == null) {
                        games.add(newGame)
                    } else {
                        val index = games.indexOf(existing)
                        if (index >= 0) {
                            games[index] = newGame
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
            )
        }

        getSharedPreferences(
            "games_library",
            MODE_PRIVATE
        ).edit()
            .putString("games", array.toString())
            .apply()
    }

    // LOAD GAMES
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
                        item.optString("coverUrl", "")
                    )
                )
            }

        } catch (_: Exception) {
            games.clear()
        }
    }
}
