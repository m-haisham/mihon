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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.backup.qr.QrPayloadBuilder
import eu.kanade.tachiyomi.util.storage.cacheImageDir
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import qrgenerator.qrkitpainter.PatternType
import qrgenerator.qrkitpainter.QrBallType
import qrgenerator.qrkitpainter.QrFrameType
import qrgenerator.qrkitpainter.QrKitBrush
import qrgenerator.qrkitpainter.QrKitColors
import qrgenerator.qrkitpainter.QrKitLogo
import qrgenerator.qrkitpainter.QrKitShapes
import qrgenerator.qrkitpainter.QrPixelType
import qrgenerator.qrkitpainter.getSelectedFrameShape
import qrgenerator.qrkitpainter.getSelectedPattern
import qrgenerator.qrkitpainter.getSelectedPixel
import qrgenerator.qrkitpainter.getSelectedQrBall
import qrgenerator.qrkitpainter.rememberQrKitPainter
import qrgenerator.qrkitpainter.solidBrush
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.interactor.GetManga
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
        val scope = rememberCoroutineScope()

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
                state.qrData.isNotEmpty() -> QrShareContent(
                    state = state,
                    modifier = Modifier.padding(contentPadding),
                    context = context,
                    scope = scope,
                )
            }
        }
    }
}

@Composable
private fun QrShareContent(
    state: QrShareScreenModel.State,
    modifier: Modifier,
    context: Context,
    scope: CoroutineScope,
) {
    val items = state.qrData
    if (items.isEmpty()) return

    val primaryColor = MaterialTheme.colorScheme.primary
    val logoPainter = painterResource(R.drawable.ic_mihon)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (items.size == 1) {
            val (uri, label) = items[0]
            val graphicsLayer = rememberGraphicsLayer()

            Spacer(Modifier.weight(1f))

            val painter = rememberQrKitPainter(data = uri) {
                colors = QrKitColors(
                    darkBrush = QrKitBrush.solidBrush(primaryColor),
                )
                logo = QrKitLogo(logoPainter)
                shapes = QrKitShapes(
                    ballShape = getSelectedQrBall(QrBallType.CircleQrBall()),
                    darkPixelShape = getSelectedPixel(QrPixelType.CirclePixel()),
                    frameShape = getSelectedFrameShape(QrFrameType.RoundCornersFrame(corner = 16.0F)),
                    codeShape = getSelectedPattern(PatternType.SquarePattern),
                )
            }
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(8.dp)
                    .drawWithContent {
                        graphicsLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(graphicsLayer)
                    },
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(Modifier.weight(1f))
            QrShareButtons(
                onSave = {
                    scope.launch {
                        saveToGallery(context, graphicsLayer.toImageBitmap().asAndroidBitmap())
                    }
                },
                onShare = {
                    scope.launch {
                        shareBitmap(context, graphicsLayer.toImageBitmap().asAndroidBitmap())
                    }
                },
            )
            Spacer(Modifier.height(24.dp))
        } else {
            val pagerState = rememberPagerState { items.size }
            // One graphics layer per page, keyed by page count
            val pageGraphicsLayers = remember(items.size) { arrayOfNulls<androidx.compose.ui.graphics.layer.GraphicsLayer>(items.size) }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) { page ->
                val (uri, label) = items[page]
                val graphicsLayer = rememberGraphicsLayer()
                pageGraphicsLayers[page] = graphicsLayer

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val painter = rememberQrKitPainter(data = uri) {
                        colors = QrKitColors(
                            darkBrush = QrKitBrush.solidBrush(primaryColor),
                        )
                        logo = QrKitLogo(logoPainter)
                        shapes = QrKitShapes(
                            ballShape = getSelectedQrBall(QrBallType.CircleQrBall()),
                            darkPixelShape = getSelectedPixel(QrPixelType.CirclePixel()),
                            frameShape = getSelectedFrameShape(QrFrameType.RoundCornersFrame(corner = 16.0F)),
                            codeShape = getSelectedPattern(PatternType.SquarePattern),
                        )
                    }
                    Image(
                        painter = painter,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .padding(horizontal = 8.dp, vertical = 12.dp)
                            .drawWithContent {
                                graphicsLayer.record { this@drawWithContent.drawContent() }
                                drawLayer(graphicsLayer)
                            },
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }

            Text(
                text = stringResource(
                    MR.strings.qr_sequence_progress,
                    pagerState.currentPage + 1,
                    items.size,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(MR.strings.qr_sequence_instruction),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            QrShareButtons(
                onSave = {
                    scope.launch {
                        pageGraphicsLayers[pagerState.currentPage]?.let {
                            saveToGallery(context, it.toImageBitmap().asAndroidBitmap())
                        }
                    }
                },
                onShare = {
                    scope.launch {
                        pageGraphicsLayers[pagerState.currentPage]?.let {
                            shareBitmap(context, it.toImageBitmap().asAndroidBitmap())
                        }
                    }
                },
            )
            Spacer(Modifier.height(24.dp))
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

private suspend fun saveToGallery(context: Context, bitmap: Bitmap?) {
    if (bitmap == null) return
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
                ?: return
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
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
//        logcat(LogPriority.ERROR, e)
    }
}

private suspend fun shareBitmap(context: Context, bitmap: Bitmap?) {
    if (bitmap == null) return
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
                Intent.createChooser(intent, null).also {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
    } catch (e: Exception) {
//        logcat(LogPriority.ERROR, e)
    }
}

class QrShareScreenModel(
    private val payloadBuilder: QrPayloadBuilder = QrPayloadBuilder(),
    private val getManga: GetManga = Injekt.get(),
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
) : StateScreenModel<QrShareScreenModel.State>(State()) {

    data class State(
        val isLoading: Boolean = true,
        val qrData: List<Pair<String, String>> = emptyList(), // uri to label
        val error: String? = null,
    )

    fun loadQrCodes(mangaIds: List<Long>, categoryId: Long? = null) {
        screenModelScope.launchIO {
            try {
                val categories = getCategories.await()

                // When sharing specific manga by ID (e.g. from MangaScreen), fetch them
                // directly so non-library manga are also supported.
                val selectedManga: List<tachiyomi.domain.manga.model.Manga>
                val mangaCategoryIds: Map<Long, List<Long>>

                if (categoryId != null || mangaIds.isEmpty()) {
                    // Category share or full-library share — use library data for category info
                    val libraryManga = getLibraryManga.await()
                    val filtered = when {
                        categoryId != null -> libraryManga.filter { it.categories.contains(categoryId) }
                        else -> libraryManga
                    }
                    selectedManga = filtered.map { it.manga }
                    mangaCategoryIds = filtered.associate { it.manga.id to it.categories }
                } else {
                    // Specific IDs — fetch directly, works for both library and non-library manga
                    selectedManga = mangaIds.mapNotNull { getManga.await(it) }
                    // Also try to get category associations from library for any that are in library
                    val libraryById = getLibraryManga.await().associateBy { it.manga.id }
                    mangaCategoryIds = selectedManga.associate { m ->
                        m.id to (libraryById[m.id]?.categories ?: emptyList())
                    }
                }

                val uris = payloadBuilder.build(selectedManga, mangaCategoryIds, categories)
                val pairs = uris.mapIndexed { index, uri ->
                    val label = when {
                        selectedManga.size == 1 -> selectedManga.first().title
                        uris.size > 1 -> "${index + 1}/${uris.size} · ${selectedManga.size} manga"
                        else -> "${selectedManga.size} manga"
                    }
                    uri to label
                }
                mutableState.update { it.copy(isLoading = false, qrData = pairs) }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                mutableState.update { it.copy(isLoading = false, error = e.message ?: "Error") }
            }
        }
    }
}
