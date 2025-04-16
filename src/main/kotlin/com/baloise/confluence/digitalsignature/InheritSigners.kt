package com.baloise.confluence.digitalsignature

import java.util.*

enum class InheritSigners {
    NONE,
    READERS_AND_WRITERS,
    READERS_ONLY,
    WRITERS_ONLY;

    companion object {
        @JvmStatic
        fun ofValue(v: String): InheritSigners =
            try {
                valueOf(v.uppercase(Locale.getDefault()).replace("\\W+".toRegex(), "_"))
            } catch (e: IllegalArgumentException) {
                NONE
            }
    }
}
