package eu.siacs.conversations.ui

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.siacs.conversations.R
import eu.siacs.conversations.ui.adapter.MediaAdapter
import eu.siacs.conversations.ui.util.Attachment
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun MediaBrowserActivity.FilterType.label(): Int = when (this) {
    MediaBrowserActivity.FilterType.IMAGES -> R.string.filter_type_images
    MediaBrowserActivity.FilterType.VIDEOS -> R.string.filter_type_videos
    MediaBrowserActivity.FilterType.AUDIO -> R.string.filter_type_audio
    MediaBrowserActivity.FilterType.DOCUMENTS -> R.string.filter_type_documents
    MediaBrowserActivity.FilterType.OTHER -> R.string.filter_type_other
}

private fun MediaBrowserActivity.FilterType.icon(): Int = when (this) {
    MediaBrowserActivity.FilterType.IMAGES -> R.drawable.ic_image_24dp
    MediaBrowserActivity.FilterType.VIDEOS -> R.drawable.ic_movie_24dp
    MediaBrowserActivity.FilterType.AUDIO -> R.drawable.ic_headphones_24dp
    MediaBrowserActivity.FilterType.DOCUMENTS -> R.drawable.ic_description_24dp
    MediaBrowserActivity.FilterType.OTHER -> R.drawable.ic_category_24dp
}

@OptIn(ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun MediaBrowserScreen(
    title: String,
    loaded: Boolean,
    attachments: List<Attachment>,
    filters: Set<MediaBrowserActivity.FilterType>,
    selection: List<String>,
    canSave: Boolean,
    onBack: () -> Unit,
    onToggleFilter: (MediaBrowserActivity.FilterType) -> Unit,
    onClick: (Attachment, android.graphics.Rect?) -> Unit,
    onLongClick: (Attachment) -> Unit,
    onToggleSelection: (Attachment) -> Unit,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onShowInChat: () -> Unit,
    loadPreview: (Attachment, Int) -> Bitmap?,
    deleteTarget: List<File>?,
    onConfirmDelete: (List<File>) -> Unit,
    onCancelDelete: () -> Unit,
) {
    val selecting = selection.isNotEmpty()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            AnimatedContent(
                targetState = selecting,
                transitionSpec = { fadeIn(spring(stiffness = 600f)) togetherWith fadeOut(spring(stiffness = 600f)) },
                label = "mediaBar",
            ) { sel ->
                if (sel) {
                    TopAppBar(
                        title = { Text(pluralStringResource(R.plurals.media_selected, selection.size, selection.size)) },
                        navigationIcon = {
                            IconButton(onClick = onClearSelection) {
                                Icon(painterResource(R.drawable.ic_close_24dp), contentDescription = stringResource(R.string.cancel))
                            }
                        },
                        actions = {
                            if (selection.size == 1) {
                                IconButton(onClick = onShowInChat) {
                                    Icon(painterResource(R.drawable.ic_chat_24dp), contentDescription = stringResource(R.string.show_in_chat))
                                }
                            }
                            IconButton(onClick = onSelectAll) {
                                Icon(painterResource(R.drawable.ic_done_all_24dp), contentDescription = stringResource(R.string.select_all))
                            }
                            IconButton(onClick = onShare) {
                                Icon(painterResource(R.drawable.ic_share_24dp), contentDescription = stringResource(R.string.share))
                            }
                            if (canSave) {
                                IconButton(onClick = onSave) {
                                    Icon(painterResource(R.drawable.ic_download_24dp), contentDescription = stringResource(R.string.save))
                                }
                            }
                            IconButton(onClick = onDelete) {
                                Icon(painterResource(R.drawable.ic_delete_24dp), contentDescription = stringResource(R.string.delete))
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    )
                } else {
                    TopAppBar(
                        title = {
                            Column {
                                Text(stringResource(R.string.contact_details_media), style = MaterialTheme.typography.titleLarge)
                                if (title.isNotBlank()) {
                                    Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(painterResource(R.drawable.ic_arrow_back_24dp), contentDescription = stringResource(R.string.back))
                            }
                        },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowItems(MediaBrowserActivity.FilterType.entries.toList()) { type ->
                    FilterChip(
                        selected = type in filters,
                        onClick = { onToggleFilter(type) },
                        label = { Text(stringResource(type.label())) },
                        leadingIcon = { Icon(painterResource(type.icon()), contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
            }
            if (attachments.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (loaded) {
                        Text(stringResource(R.string.media_browser_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(104.dp),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(attachments, key = { it.uuid }) { attachment ->
                        MediaTile(
                            attachment = attachment,
                            selected = attachment.uuid.toString() in selection,
                            selecting = selecting,
                            onClick = { bounds -> onClick(attachment, bounds) },
                            onLongClick = { onLongClick(attachment) },
                            loadPreview = loadPreview,
                        )
                    }
                }
            }
        }
    }
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = onCancelDelete,
            title = { Text(pluralStringResource(R.plurals.delete_files_dialog, deleteTarget.size, deleteTarget.size)) },
            text = { Text(pluralStringResource(R.plurals.delete_files_dialog_msg, deleteTarget.size)) },
            confirmButton = { TextButton(onClick = { onConfirmDelete(deleteTarget) }) { Text(stringResource(R.string.confirm)) } },
            dismissButton = { TextButton(onClick = onCancelDelete) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaTile(
    attachment: Attachment,
    selected: Boolean,
    selecting: Boolean,
    onClick: (android.graphics.Rect?) -> Unit,
    onLongClick: () -> Unit,
    loadPreview: (Attachment, Int) -> Bitmap?,
) {
    val density = LocalDensity.current
    val sizePx = with(density) { 160.dp.toPx() }.toInt()
    var preview by remember(attachment) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(attachment) {
        preview = withContext(Dispatchers.IO) { loadPreview(attachment, sizePx) }?.asImageBitmap()
    }
    // A selected tile draws itself together and rounds off.
    val scale by animateFloatAsState(if (selected) 0.86f else 1f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "tileScale")
    val corner by animateDpAsState(if (selected) 26.dp else 14.dp, spring(dampingRatio = 0.7f, stiffness = 500f), label = "tileCorner")
    var bounds by remember { mutableStateOf<android.graphics.Rect?>(null) }
    val mime = attachment.mime.orEmpty()
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .onGloballyPositioned {
                val b = it.boundsInWindow()
                bounds = android.graphics.Rect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt())
            }
            .scale(scale)
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .combinedClickable(onClick = { onClick(bounds) }, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        val p = preview
        if (p != null) {
            Image(bitmap = p, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(
                painterResource(MediaAdapter.getImageDrawable(attachment)),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp),
            )
        }
        if (mime.startsWith("video/")) {
            Box(
                Modifier.align(Alignment.BottomStart).padding(6.dp).size(26.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_play_arrow_24dp), contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
        if (selecting) {
            Box(
                Modifier.align(Alignment.TopStart).padding(8.dp).size(24.dp).clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(painterResource(R.drawable.ic_check_24dp), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
