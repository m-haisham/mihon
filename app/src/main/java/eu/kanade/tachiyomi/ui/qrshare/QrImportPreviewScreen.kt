package eu.kanade.tachiyomi.ui.qrshare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.data.backup.models.QrSharePayload
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.ui.home.HomeScreen
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.category.interactor.CreateCategoryWithName
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class QrImportPreviewScreen(
    val payload: QrSharePayload,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val screenModel = rememberScreenModel { QrImportPreviewScreenModel() }
        val state by screenModel.state.collectAsState()
        val snackbarHostState = remember { SnackbarHostState() }
        val coroutineScope = rememberCoroutineScope()

        LaunchedEffect(Unit) { screenModel.initialize(payload) }

        LaunchedEffect(state.isDone) {
            if (state.isDone) {
                snackbarHostState.showSnackbar(
                    context.stringResource(MR.strings.qr_import_success, state.importedCount),
                )
                navigator.pop()
            }
        }

        val selectedCount = state.selectedMangaIndices.size

        Scaffold(
            topBar = { sb ->
                AppBar(
                    title = stringResource(MR.strings.qr_import_title),
                    navigateUp = navigator::pop,
                    scrollBehavior = sb,
                )
            },
            floatingActionButton = {
                SmallExtendedFloatingActionButton(
                    text = { Text(stringResource(MR.strings.qr_add_to_library)) },
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    onClick = { screenModel.importSelected() },
                    expanded = !state.isImporting && selectedCount > 0,
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { contentPadding ->
            Box(Modifier.fillMaxSize()) {
                QrImportContent(
                    state = state,
                    contentPadding = contentPadding,
                    missingSources = screenModel.missingSources,
                    onToggleKeepCategories = screenModel::toggleKeepOriginalCategories,
                    onToggleMangaSelection = screenModel::toggleMangaSelection,
                    onSelectAll = screenModel::selectAll,
                    onDeselectAll = screenModel::deselectAll,
                    onNavigateToBrowse = {
                        coroutineScope.launch {
                            navigator.popUntilRoot()
                            HomeScreen.openTab(HomeScreen.Tab.Browse(toExtensions = true))
                        }
                    },
                )
                if (state.isImporting) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
                }
            }
        }
    }
}

@Composable
private fun QrImportContent(
    state: QrImportPreviewScreenModel.State,
    contentPadding: PaddingValues,
    missingSources: List<BackupSource>,
    onToggleKeepCategories: () -> Unit,
    onToggleMangaSelection: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onNavigateToBrowse: () -> Unit,
) {
    val payload = state.payload
    val isMultiManga = payload.manga.size > 1

    LazyColumn(
        contentPadding = contentPadding,
        modifier = Modifier.fillMaxSize(),
    ) {
        // Header: count
        item {
            Text(
                text = stringResource(MR.strings.qr_import_count, payload.manga.size),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }

        // Missing sources warning card
        if (missingSources.isNotEmpty()) {
            item {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(MR.strings.qr_missing_sources),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(MR.strings.qr_missing_sources_message),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(8.dp))
                        missingSources.forEach { source ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = source.name.ifBlank {
                                        stringResource(MR.strings.qr_source_unavailable)
                                    },
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                TextButton(onClick = onNavigateToBrowse) {
                                    Text(stringResource(MR.strings.qr_install_extension, source.name))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Category preference chips
        item {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = state.keepOriginalCategories,
                        onClick = onToggleKeepCategories,
                        label = { Text(stringResource(MR.strings.qr_keep_original_categories)) },
                    )
                    FilterChip(
                        selected = !state.keepOriginalCategories,
                        onClick = onToggleKeepCategories,
                        label = { Text(stringResource(MR.strings.qr_no_categories)) },
                    )
                }
            }
        }

        // Select all / deselect all row for multi-manga
        if (isMultiManga) {
            item {
                val allSelected = state.selectedMangaIndices.size == payload.manga.size
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = if (allSelected) onDeselectAll else onSelectAll) {
                        Text(
                            text = if (allSelected) {
                                stringResource(MR.strings.action_deselect_all)
                            } else {
                                stringResource(MR.strings.action_select_all)
                            },
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }

        // Manga list with dividers
        item {
            HorizontalDivider()
        }

        payload.manga.forEachIndexed { index, qrManga ->
            item(key = index) {
                val isMissingSource = qrManga.source !in state.installedSourceIds
                val sourceName = payload.sources
                    .find { it.sourceId == qrManga.source }
                    ?.name
                    ?.ifBlank { null }
                    ?: stringResource(MR.strings.qr_source_unavailable)

                val categoryNames = qrManga.categories
                    .mapNotNull { catId -> payload.categories.find { it.id == catId }?.name }
                    .joinToString(", ")

                val isSelected = index in state.selectedMangaIndices

                ListItem(
                    headlineContent = {
                        Text(
                            text = qrManga.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                        )
                    },
                    supportingContent = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = sourceName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                                if (isMissingSource) {
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Outlined.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(12.dp),
                                    )
                                }
                            }
                            if (categoryNames.isNotEmpty()) {
                                Text(
                                    text = categoryNames,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    },
                    leadingContent = {
                        AsyncImage(
                            model = qrManga.thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                        )
                    },
                    trailingContent = if (isMultiManga) {
                        {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { onToggleMangaSelection(index) },
                            )
                        }
                    } else {
                        null
                    },
                )
                HorizontalDivider()
            }
        }

        // Bottom padding so FAB doesn't cover last item
        item {
            Spacer(Modifier.height(80.dp))
        }
    }
}

class QrImportPreviewScreenModel(
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val createCategoryWithName: CreateCategoryWithName = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val setMangaCategories: SetMangaCategories = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
) : StateScreenModel<QrImportPreviewScreenModel.State>(State(payload = QrSharePayload())) {

    data class State(
        val payload: QrSharePayload,
        val installedSourceIds: Set<Long> = emptySet(),
        val keepOriginalCategories: Boolean = true,
        val isImporting: Boolean = false,
        val importedCount: Int = 0,
        val isDone: Boolean = false,
        val error: String? = null,
        val selectedMangaIndices: Set<Int> = emptySet(),
    )

    val missingSources: List<BackupSource>
        get() = state.value.payload.sources.filter { it.sourceId !in state.value.installedSourceIds }

    fun initialize(payload: QrSharePayload) {
        screenModelScope.launchIO {
            val installedIds = extensionManager.installedExtensionsFlow.value
                .flatMap { it.sources }
                .map { it.id }
                .toSet()
            mutableState.update {
                it.copy(
                    payload = payload,
                    installedSourceIds = installedIds,
                    selectedMangaIndices = payload.manga.indices.toSet(),
                )
            }
        }
    }

    fun toggleKeepOriginalCategories() {
        mutableState.update { it.copy(keepOriginalCategories = !it.keepOriginalCategories) }
    }

    fun toggleMangaSelection(index: Int) {
        mutableState.update { s ->
            val current = s.selectedMangaIndices.toMutableSet()
            if (index in current) current.remove(index) else current.add(index)
            s.copy(selectedMangaIndices = current)
        }
    }

    fun selectAll() {
        mutableState.update { s ->
            s.copy(selectedMangaIndices = s.payload.manga.indices.toSet())
        }
    }

    fun deselectAll() {
        mutableState.update { s ->
            s.copy(selectedMangaIndices = emptySet())
        }
    }

    fun importSelected(allowMissingSources: Boolean = true) {
        screenModelScope.launchIO {
            mutableState.update { it.copy(isImporting = true) }
            try {
                val currentState = state.value
                val payload = currentState.payload
                val localCategories = getCategories.await().toMutableList()
                var importedCount = 0

                val mangaToImport = payload.manga.filterIndexed { idx, _ ->
                    idx in currentState.selectedMangaIndices
                }

                for (qrManga in mangaToImport) {
                    if (!allowMissingSources && qrManga.source !in currentState.installedSourceIds) continue
                    val manga = Manga.create().copy(
                        url = qrManga.url,
                        source = qrManga.source,
                        title = qrManga.title,
                        thumbnailUrl = qrManga.thumbnailUrl,
                        favorite = true,
                    )
                    val localManga = networkToLocalManga(manga)
                    if (!localManga.favorite) {
                        updateManga.awaitUpdateFavorite(localManga.id, true)
                    }
                    if (currentState.keepOriginalCategories && qrManga.categories.isNotEmpty()) {
                        val categoryIds = mutableListOf<Long>()
                        for (catId in qrManga.categories) {
                            val backupCat = payload.categories.find { it.id == catId }
                            if (backupCat != null) {
                                val localCat = localCategories.find { it.name == backupCat.name }
                                    ?: run {
                                        createCategoryWithName.await(backupCat.name)
                                        val refreshed = getCategories.await()
                                        localCategories.clear()
                                        localCategories.addAll(refreshed)
                                        refreshed.find { it.name == backupCat.name }
                                    }
                                localCat?.let { categoryIds.add(it.id) }
                            }
                        }
                        if (categoryIds.isNotEmpty()) {
                            setMangaCategories.await(localManga.id, categoryIds)
                        }
                    }
                    importedCount++
                }
                mutableState.update {
                    it.copy(isImporting = false, importedCount = importedCount, isDone = true)
                }
            } catch (e: Exception) {
                mutableState.update { it.copy(isImporting = false, error = e.message) }
            }
        }
    }
}
