package com.familylibrary.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.familylibrary.app.data.cover.CoverService
import com.familylibrary.app.data.entity.CoverMeta
import com.familylibrary.app.ui.theme.PrimaryBrownLight
import java.io.File

@Composable
fun BookCover(
    coverUri: String?,
    title: String,
    modifier: Modifier = Modifier,
    coverStatus: String? = null,
) {
    val context = LocalContext.current
    val imageFile = remember(coverUri) {
        coverUri?.let { CoverService(context.applicationContext).toAbsolutePath(it) }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(PrimaryBrownLight.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center,
    ) {
        if (imageFile != null) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageFile)
                    .crossfade(true)
                    .size(200)
                    .build(),
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                },
                error = {
                    PlaceholderIcon(failed = coverStatus == CoverMeta.STATUS_FAILED)
                },
            )
        } else {
            PlaceholderIcon(
                loading = coverStatus == CoverMeta.STATUS_LOADING,
                failed = coverStatus == CoverMeta.STATUS_FAILED,
            )
        }
    }
}

@Composable
private fun PlaceholderIcon(loading: Boolean = false, failed: Boolean = false) {
    if (loading) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.dp,
        )
    } else {
        Icon(
            Icons.Default.MenuBook,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
fun BookTitleText(title: String, modifier: Modifier = Modifier, maxLines: Int = 2) {
    Text(
        text = title,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

fun formatWordCount(count: Int): String = when {
    count >= 10000 -> "%.1f万字".format(count / 10000.0)
    count > 0 -> "$count 字"
    else -> ""
}

/** 解析封面相对路径为绝对路径，供详情页等大图场景使用 */
fun resolveCoverFile(context: android.content.Context, coverUri: String?): File? =
    CoverService(context.applicationContext).toAbsolutePath(coverUri)
