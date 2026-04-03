package eu.kanade.tachiyomi.ui.qrshare

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.backup.models.QrSharePayload
import eu.kanade.tachiyomi.data.backup.qr.QrPayloadParser
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource

data object QrScanScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { QrScanScreenModel() }
        val state by screenModel.state.collectAsState()
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(state.completedPayload) {
            if (state.completedPayload != null) {
                navigator.push(QrImportPreviewScreen(payload = state.completedPayload!!))
            }
        }

        LaunchedEffect(state.error) {
            if (state.error != null) {
                snackbarHostState.showSnackbar(state.error!!)
                screenModel.clearError()
            }
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.qr_scan_title),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { contentPadding ->
            Box(Modifier.fillMaxSize().padding(contentPadding)) {
                QrScannerView(
                    modifier = Modifier.fillMaxSize(),
                    onScan = { raw -> screenModel.onScan(raw, stringResource(MR.strings.qr_scan_error)) },
                )
                ScanOverlayText(
                    state = state,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
                )
            }
        }
    }
}

@Composable
private fun QrScannerView(modifier: Modifier, onScan: (String) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var barcodeView by remember { mutableStateOf<DecoratedBarcodeView?>(null) }
    val scannedTexts = remember { mutableSetOf<String>() }
    val barcodeCallback = remember {
        object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult?) {
                val text = result?.text ?: return
                if (scannedTexts.add(text)) onScan(text)
            }
        }
    }
    AndroidView(
        factory = { ctx ->
            DecoratedBarcodeView(ctx).also { view ->
                barcodeView = view
                view.initializeFromIntent(Intent())
                view.decodeContinuous(barcodeCallback)
                view.resume()
            }
        },
        modifier = modifier,
    )
    DisposableEffect(lifecycleOwner) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) { barcodeView?.resume() }
            override fun onPause(owner: LifecycleOwner) { barcodeView?.pause() }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            barcodeView?.pause()
        }
    }
}

@Composable
private fun ScanOverlayText(state: QrScanScreenModel.State, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        val text = if (state.totalExpected != null && state.totalExpected > 1) {
            stringResource(MR.strings.qr_scan_multi_progress, state.scannedCount, state.totalExpected)
        } else {
            stringResource(MR.strings.qr_scan_instruction)
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

class QrScanScreenModel(
    private val parser: QrPayloadParser = QrPayloadParser(),
) : StateScreenModel<QrScanScreenModel.State>(State()) {

    data class State(
        val scannedCount: Int = 0,
        val totalExpected: Int? = null,
        val error: String? = null,
        val completedPayload: QrSharePayload? = null,
    )

    private val chunks = mutableMapOf<Int, String>()

    fun onScan(raw: String, errorMessage: String) {
        screenModelScope.launch {
            when (val result = parser.parse(raw)) {
                is QrPayloadParser.ScanResult.Single -> {
                    mutableState.update { it.copy(completedPayload = result.payload) }
                }
                is QrPayloadParser.ScanResult.Chunk -> {
                    if (chunks.containsKey(result.index)) return@launch
                    chunks[result.index] = result.encoded
                    mutableState.update { it.copy(scannedCount = chunks.size, totalExpected = result.total) }
                    if (chunks.size == result.total) {
                        val ordered = (1..result.total).mapNotNull { chunks[it] }
                        val payload = parser.assembleAndDecode(ordered)
                        if (payload != null) {
                            mutableState.update { it.copy(completedPayload = payload) }
                        } else {
                            mutableState.update { it.copy(error = errorMessage) }
                        }
                    }
                }
                is QrPayloadParser.ScanResult.Unknown -> {
                    mutableState.update { it.copy(error = errorMessage) }
                }
            }
        }
    }

    fun clearError() {
        mutableState.update { it.copy(error = null) }
    }
}
