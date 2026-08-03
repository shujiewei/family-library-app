package com.familylibrary.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.familylibrary.app.ui.theme.MemberColors
import kotlin.math.abs

@Composable
fun BookSpine(
    title: String,
    bookId: Long,
    modifier: Modifier = Modifier,
    author: String = "",
    isSelected: Boolean = false,
) {
    val colorKey = if (author.isNotBlank()) author.hashCode() else bookId.hashCode()
    val baseColor = MemberColors[abs(colorKey) % MemberColors.size]
    val spineColor = baseColor.copy(alpha = 0.88f)
    val width = spineWidthDp(title).dp

    Box(
        modifier = modifier
            .width(width)
            .height(132.dp)
            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
            .background(spineColor)
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp))
                } else {
                    Modifier.border(0.5.dp, Color.Black.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
                },
            )
            .padding(horizontal = 3.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        VerticalTitleText(
            text = title,
            color = Color.White,
        )
    }
}
