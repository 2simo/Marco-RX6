
package com.marcorx6.library

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import android.text.TextWatcher
import android.text.Editable
import org.json.JSONArray
import org.json.JSONObject

data class Game(
    val name: String,
    val platform: String,
    val description: String,
    val url: String
)

class MainActivity : Activity() {

    private val bg = Color.rgb(12, 13, 19)
    private val panel = Color.rgb(27, 29, 39)
    private val red = Color.rgb(255, 55, 75)

    private val platforms =
        listOf("ALL", "PSP", "PS1", "PS2", "PS3", "PS4")

    private val games = mutableListOf<Game>()

    private lateinit var gameList: LinearLayout
    private lateinit var search: EditText
    private lateinit var filters: LinearLayout

    private var selected = "ALL"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = bg
        window.navigationBarColor = bg

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

    private fun createInterface() {

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(12))
            setBackgroundColor(bg)
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
            makeText("MARCO RX6", 25f, Color.WHITE, true)
        )

        brand.addView(
            makeText("ULTIMATE PLAYSTATION LIBRARY", 10f, red, true)
        )

        header.addView(
            brand,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        val addButton = TextView(this).apply {
            text = "+ ADD"
            textSize = 13f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = rounded(red, 15)
            setPadding(dp(17), dp(12), dp(17), dp(12))
            setOnClickListener { showAddDialog() }
        }

        header.addView(addButton)
        root.addView(header)

        // HERO
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(panel, 20)
            setPadding(dp(18), dp(20), dp(18), dp(20))
        }

        hero.addView(
            makeText("WELCOME TO MARCO RX6", 10f, red, true)
        )

        hero.addView(
            makeText(
                "PLAY YOUR\nCLASSIC WORLD.",
                28f,
                Color.WHITE,
                true
            )
        )

        hero.addView(
            makeText(
                "Discover PSP, PS1, PS2, PS3 and PS4 games.",
                12f,
                Color.LTGRAY
            )
        )

        root.addView(
            hero,
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(20)
            }
        )

        // SEARCH
        search = EditText(this).apply {
            hint = "Search games..."
            textSize = 14f
            singleLine = true
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            background = rounded(panel, 14)
            setPadding(dp(15), 0, dp(15), 0)
        }

        root.addView(
            search,
            LinearLayout.LayoutParams(-1, dp(50)).apply {
                topMargin = dp(18)
            }
        )

        // PLATFORM FILTERS
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
                topMargin = dp(16)
                bottomMargin = dp(15)
            }
        )

        // LIBRARY TITLE
        root.addView(
            makeText("EXPLORE GAMES", 16f, Color.WHITE, true)
        )

        // GAME LIST
        val scroll = ScrollView(this)

        gameList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(14), 0, dp(10))
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
                setPadding(0, dp(12), 0, dp(5))
            }
        )

        setContentView(root)

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
                if (active) Color.WHITE else Color.LTGRAY
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

        if (results.isEmpty()) {

            gameList.addView(
                makeText(
                    "NO GAMES FOUND\n\nAdd your first game using + ADD",
                    14f,
                    Color.LTGRAY
                ).apply {
                    gravity = Gravity.CENTER
                    setPadding(dp(10), dp(45), dp(10), dp(45))
                }
            )
        }

        results.forEach { game ->

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = rounded(panel, 16)
                setPadding(dp(12), dp(12), dp(12), dp(12))
            }

            val cover = TextView(this).apply {
                text = game.platform
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)

                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(red, Color.rgb(75, 20, 45))
                ).apply {
                    cornerRadius = dp(12).toFloat()
                }
            }

            card.addView(
                cover,
                LinearLayout.LayoutParams(dp(75), dp(85))
            )

            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, 0, 0)
            }

            info.addView(
                makeText(game.name, 15f, Color.WHITE, true)
            )

            info.addView(
                makeText(
                    game.platform + " • PLAYSTATION",
                    10f,
                    red,
                    true
                )
            )

            info.addView(
                makeText(game.description, 11f, Color.LTGRAY)
            )

            card.addView(
                info,
                LinearLayout.LayoutParams(0, -2, 1f)
            )

            card.setOnClickListener {

                if (game.url.isNotBlank()) {

                    try {
                        startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(game.url)
                            )
                        )
                    } catch (_: Exception) {
                        Toast.makeText(
                            this,
                            "Invalid download link",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                } else {
                    Toast.makeText(
                        this,
                        "No download link added",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            gameList.addView(
                card,
                LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = dp(11)
                }
            )
        }
    }

    // ADD GAME
    private fun showAddDialog() {

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(5), dp(20), 0)
        }

        val name = EditText(this).apply {
            hint = "Game name"
        }

        val description = EditText(this).apply {
            hint = "Description"
        }

        val link = EditText(this).apply {
            hint = "HTTPS download URL"
        }

        val platform = Spinner(this)

        platform.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            platforms.drop(1)
        )

        box.addView(name)
        box.addView(platform)
        box.addView(description)
        box.addView(link)

        AlertDialog.Builder(this)
            .setTitle("ADD NEW GAME")
            .setView(box)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("SAVE") { _, _ ->

                val gameName = name.text.toString().trim()
                val gameLink = link.text.toString().trim()

                if (
                    gameName.isBlank() ||
                    !gameLink.startsWith("https://")
                ) {

                    Toast.makeText(
                        this,
                        "Enter a name and valid HTTPS URL",
                        Toast.LENGTH_LONG
                    ).show()

                } else {

                    games.add(
                        Game(
                            gameName,
                            platform.selectedItem.toString(),
                            description.text.toString(),
                            gameLink
                        )
                    )

                    saveGames()
                    renderGames()
                }
            }
            .show()
    }

    // SAVE GAMES
    private fun saveGames() {

        val array = JSONArray()

        games.forEach {

            array.put(
                JSONObject()
                    .put("name", it.name)
                    .put("platform", it.platform)
                    .put("description", it.description)
                    .put("url", it.url)
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
                        item.optString("url")
                    )
                )
            }

        } catch (_: Exception) {
        }
    }
}
