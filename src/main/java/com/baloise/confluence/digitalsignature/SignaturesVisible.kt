package com.baloise.confluence.digitalsignature

import java.util.*

enum class SignaturesVisible {
    ALWAYS,
    IF_SIGNATORY,
    IF_SIGNED;

    companion object {
        fun ofValue(v: String): SignaturesVisible {
            return try {
                valueOf(
                    v.uppercase(Locale.getDefault()).replace("\\W+".toRegex(), "_")
                )
            } catch (e: Exception) {
                ALWAYS
            }
        }
    }
}
