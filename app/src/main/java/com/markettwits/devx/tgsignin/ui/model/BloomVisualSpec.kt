package com.markettwits.devx.tgsignin.ui.model

data class BloomVisualSpec(
    val primaryHue: Float,
    val secondaryHue: Float,
    val petalCount: Int,
    val rotationFraction: Float
)

/** Pure deterministic mapping used by Compose and covered independently of rendering. */
fun bloomVisualSpec(seed: String): BloomVisualSpec {
    val hash = seed.fold(17) { value, char -> value * 31 + char.code }
    return BloomVisualSpec(
        primaryHue = ((hash and 0x7fffffff) % 360).toFloat(),
        secondaryHue = (((hash ushr 8) and 0x7fffffff) % 360).toFloat(),
        petalCount = 6 + ((hash ushr 16) and 3),
        rotationFraction = (hash and 15) / 15f
    )
}
