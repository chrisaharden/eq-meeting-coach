package com.eqcoach.model

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Verdict {
    @SerialName("GREEN")  GREEN,
    @SerialName("YELLOW") YELLOW,
    @SerialName("RED")    RED,
    @SerialName("GRAY")   GRAY;

    fun toColor(): Color = when (this) {
        GREEN  -> Color(0xFF4CAF50)
        YELLOW -> Color(0xFFFF9800)
        RED    -> Color(0xFFF44336)
        GRAY   -> Color(0xFF9E9E9E)
    }
}
