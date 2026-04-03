package eu.kanade.tachiyomi.ui.qrshare

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.backup.qr.QrPayloadBuilder
import eu.kanade.tachiyomi.util.storage.cacheImageDir
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.QrCodeGenerator
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

data class QrShareScreen(
    val mangaIds: List<Long>,
    val categoryId: Long? = null,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val screenModel = rememberScreenModel { QrShareScreenModel() }
        val state by screenModel.state.collectAsState()
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(Unit) {
            screenModel.loadQrCodes(mangaIds, categoryId)
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.qr_share_title),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { contentPadding ->
            when {
                state.isLoading -> LoadingScreen(Modifier.padding(contentPadding))
                state.error != null -> Box(
                    Modifier.fillMaxSize().padding(contentPadding),
                    contentAlignment = Alignment.Center,
                ) { Text(state.error!!) }
                state.qrBitmaps.isNotEmpty() -> QrShareContent(
                    state = state,
                    modifier = Modifier.padding(contentPadding),
                    onSave = { idx -> screenModel.saveToGallery(context, idx) },
                    onShare = { idx -> screenModel.shareBitmap(context, idx) },
                )
            }
        }
    }
}

@Composable
private fun QrShareContent(
    state: QrShareScreenModel.State,
    modifier: Modifier,
    onSave: (Int) -> Unit,
    onShare: (Int) -> Unit,
) {
    val bitmaps = state.qrBitmaps
    if (bitmaps.isEmpty()) return
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (bitmaps.size == 1) {
            Spacer(Modifier.weight(1f))
            Image(
                bitmap = bitmaps[0].asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            )
            Spacer(Modifier.weight(1f))
            QrShareButtons(onSave = { onSave(0) }, onShare = { onShare(0) })
            Spacer(Modifier.height(16.dp))
        } else {
            val pagerState = rememberPagerState { bitmaps.size }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) { page ->
                Image(
                    bitmap = bitmaps[page].asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(8.dp),
                )
            }
            Text(
                text = stringResource(MR.strings.qr_sequence_progress, pagerState.currentPage + 1, bitmaps.size),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(MR.strings.qr_sequence_instruction),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            QrShareButtons(
                onSave = { onSave(pagerState.currentPage) },
                onShare = { onShare(pagerState.currentPage) },
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun QrShareButtons(onSave: () -> Unit, onShare: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onSave) { Text(stringResource(MR.strings.qr_save_to_gallery)) }
        Button(onClick = onShare) { Text(stringResource(MR.strings.action_share)) }
    }
}

class QrShareScreenModel(
    private val payloadBuilder: QrPayloadBuilder = QrPayloadBuilder(),
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
) : StateScreenModel<QrShareScreenModel.State>(State()) {

    data class State(
        val isLoading: Boolean = true,
        val qrBitmaps: List<Bitmap> = emptyList(),
        val error: String? = null,
    )

    fun loadQrCodes(mangaIds: List<Long>, categoryId: Long? = null) {
        screenModelScope.launchIO {
            try {
                val libraryManga = getLibraryManga.await()
                val selectedLibraryManga = when {
                    categoryId != null -> libraryManga.filter { it.categories.contains(categoryId) }
                    mangaIds.isNotEmpty() -> {
                        val byId = libraryManga.associateBy { it.manga.id }
                        mangaIds.mapNotNull { byId[it] }
                    }
                    else -> libraryManga
                }
                val selectedManga = selectedLibraryManga.map { it.manga }
                val mangaCategoryIds = selectedLibraryManga.associate { it.manga.id to it.categories }
                val categories = getCategories.await()
                val uris = payloadBuilder.build(selectedManga, mangaCategoryIds, categories)
                val bitmaps = uris.map { QrCodeGenerator.generate(it) }
                mutableState.update { it.copy(isLoading = false, qrBitmaps = bitmaps) }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                mutableState.update { it.copy(isLoading = false, error = e.message ?: "Error") }
            }
        }
    }

    fun saveToGallery(context: Context, bitmapIndex: Int) {
        val bitmap = state.value.qrBitmaps.getOrNull(bitmapIndex) ?: return
        screenModelScope.launchIO {
            try {
                val filename = "mihon_qr_${System.currentTimeMillis()}.png"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        ?: return@launchIO
                    resolver.openOutputStream(uri)?.use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                } else {
                    @Suppress("DEPRECATION")
                    val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    dir.mkdirs()
                    File(dir, filename).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
                withUIContext { context.toast(MR.strings.qr_saved_to_gallery) }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    fun shareBitmap(context: Context, bitmapIndex: Int) {
        val bitmap = state.value.qrBitmaps.getOrNull(bitmapIndex) ?: return
        screenModelScope.launchIO {
            try {
                val cacheDir = context.cacheImageDir
                cacheDir.mkdirs()
                val file = File(cacheDir, "qr_share.png")
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                val uri = file.getUriCompat(context)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                withUIContext {
                    context.startActivity(
                        Intent.createChooser(intent, null).also { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
                    )
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
            }
        }
    }
}
