package com.example.photocad.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// rounded-xl / rounded-2xl из дизайна
val CardShape = RoundedCornerShape(16.dp)
val FieldShape = RoundedCornerShape(12.dp)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = FieldShape,
    medium = CardShape,
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)
