package com.marcorx6.library.ui

import android.content.Context
import android.content.SharedPreferences

/** SharedPreferences access for the player's local profile. */
internal object ProfilePreferences {
    private const val FILE_NAME = "user_profile"

    fun open(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
}
