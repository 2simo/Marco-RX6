package com.marcorx6.library.ui

import com.marcorx6.library.data.GameLibraryStore
import com.marcorx6.library.data.Game

/** Applies imported Blogger games through the existing store merge policy. */
internal object GameImportMerger {
    fun merge(store: GameLibraryStore, imported: List<Game>): Pair<Int, Int> {
        val result = store.mergeImported(imported)
        return result.added to result.updated
    }
}
