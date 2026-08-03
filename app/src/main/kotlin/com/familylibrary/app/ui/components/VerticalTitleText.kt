package com.familylibrary.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 竖排书名：每列自上而下，列从右到左（更接近真实书脊） */
@Composable
fun VerticalTitleText(
    text: String,
    modifier: Modifier = Modifier,
    maxCharsPerColumn: Int = 7,
    style: TextStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 11.sp,
        lineHeight = 12.sp,
    ),
    color: Color = Color.White,
) {
    val columns = remember(text, maxCharsPerColumn) {
        text.filter { !it.isWhitespace() }
            .chunked(maxCharsPerColumn)
            .reversed()
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        columns.forEach { column ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
            ) {
                column.forEach { char ->
                    Text(text = char.toString(), style = style, color = color)
                }
            }
        }
    }
}

/** 根据竖排字数估算书脊宽度（dp） */
fun spineWidthDp(title: String, maxCharsPerColumn: Int = 7): Int {
    val charCount = title.count { !it.isWhitespace() }
    if (charCount == 0) return 28
    val columns = (charCount + maxCharsPerColumn - 1) / maxCharsPerColumn
    return (columns * 13 + 10).coerceIn(28, 52)
}
