package eu.kanade.tachiyomi.ui.qrshare

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.backup.qr.QrPayloadParser
import tachiyomi.presentation.core.screens.LoadingScreen

data class QrImportDeepLinkScreen(private val rawUri: String) : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val parser = remember { QrPayloadParser() }
        var handled by remember { mutableStateOf(false) }
        if (!handled) LoadingScreen()
        LaunchedEffect(rawUri) {
            when (val result = parser.parse(rawUri)) {
                is QrPayloadParser.ScanResult.Single -> {
                    navigator.replace(QrImportPreviewScreen(payload = result.payload))
                }
                is QrPayloadParser.ScanResult.Chunk -> {
                    // A deep link only provides one chunk; redirect to scanner to collect the rest
                    navigator.replace(QrScanScreen)
                }
                is QrPayloadParser.ScanResult.Unknown -> navigator.pop()
            }
            handled = true
        }
    }
}
