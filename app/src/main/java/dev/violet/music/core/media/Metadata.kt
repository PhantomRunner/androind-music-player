package dev.violet.music.core.media

internal fun normalizeMetadata(value: String?): String? =
    value?.trim()?.takeUnless { it.isEmpty() || it.equals("<unknown>", ignoreCase = true) }
