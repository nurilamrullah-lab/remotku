package com.remotku.app.ui.ir

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remotku.app.core.haptics.HapticType
import com.remotku.app.core.haptics.LocalHaptics
import com.remotku.app.domain.ir.IrBrand
import com.remotku.app.domain.ir.IrCatalog
import com.remotku.app.ui.theme.RemotkuColors
import kotlinx.coroutines.launch

const val POPULAR_INDEX = "Pop"
val INDEX_ENTRIES: List<String> = listOf(POPULAR_INDEX, "#") + ('A'..'Z').map { it.toString() }

private sealed interface BrandRow {
    val key: String
    data class Header(val title: String, override val key: String) : BrandRow
    data class Item(val brand: IrBrand, override val key: String) : BrandRow
}

/** Rows plus the row index each quick-index entry should jump to. */
private class BrandRows(val rows: List<BrandRow>, val anchors: Map<String, Int>)

private fun buildRows(catalog: IrCatalog): BrandRows {
    val rows = ArrayList<BrandRow>()
    val anchors = HashMap<String, Int>()
    val popular = catalog.popular
    if (popular.isNotEmpty()) {
        anchors[POPULAR_INDEX] = rows.size
        rows += BrandRow.Header("Popular brands", "h_pop")
        popular.forEach { rows += BrandRow.Item(it, "p_${it.name}") }
    }
    catalog.brands.groupBy { it.indexLetter }.toSortedMap(compareBy { if (it == '#') ' ' else it }).forEach { (letter, brands) ->
        anchors[letter.toString()] = rows.size
        rows += BrandRow.Header(letter.toString(), "h_$letter")
        brands.forEach { rows += BrandRow.Item(it, "b_${it.name}") }
    }
    return BrandRows(rows, anchors)
}

@Composable
fun BrandSelectScreen(state: BrandListState, onIntent: (IrIntent) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        SearchField(state.query, onChange = { onIntent(IrIntent.Search(it)) })
        val catalog = state.catalog
        if (catalog == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return
        }
        if (state.query.isNotBlank()) {
            SearchResults(state.results, catalog, state.query) { onIntent(IrIntent.PickBrand(it)) }
        } else {
            IndexedBrandList(catalog) { onIntent(IrIntent.PickBrand(it)) }
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text("Type brand and model") },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = if (query.isNotEmpty()) {
            { IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Close, "Hapus pencarian") } }
        } else {
            null
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = RemotkuColors.Surface,
            unfocusedContainerColor = RemotkuColors.Surface,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .card(RoundedCornerShape(16.dp)),
    )
}

@Composable
private fun SearchResults(results: List<IrBrand>, catalog: IrCatalog, query: String, onPick: (IrBrand) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { SectionHeader(if (results.isEmpty()) "Tidak ada merek atau model \"$query\"" else "Hasil (${results.size})") }
        results.forEach { brand ->
            item(key = brand.name) {
                val modelHit = !brand.name.contains(query.trim(), ignoreCase = true)
                val model = if (modelHit) catalog.codeSetsFor(brand).firstOrNull { it.model.contains(query.trim(), ignoreCase = true) }?.model else null
                BrandItem(brand, subtitle = model?.let { "Model $it" }, onClick = { onPick(brand) })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IndexedBrandList(catalog: IrCatalog, onPick: (IrBrand) -> Unit) {
    val data = remember(catalog) { buildRows(catalog) }
    val listState = rememberLazyListState()
    var active by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.weight(1f).fillMaxHeight(), state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
                data.rows.forEach { row ->
                    when (row) {
                        is BrandRow.Header -> stickyHeader(key = row.key) { SectionHeader(row.title) }
                        is BrandRow.Item -> item(key = row.key) { BrandItem(row.brand, onClick = { onPick(row.brand) }) }
                    }
                }
            }
            AlphabetIndex(data.anchors, listState, active) { active = it }
        }
        active?.let { letter ->
            Box(
                Modifier.align(Alignment.Center).size(64.dp).clip(CircleShape).background(RemotkuColors.Primary),
                contentAlignment = Alignment.Center,
            ) { Text(letter, color = Color.White, fontSize = if (letter.length > 1) 18.sp else 28.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = RemotkuColors.TextMuted,
        modifier = Modifier.fillMaxWidth().background(RemotkuColors.Background).padding(start = 20.dp, top = 14.dp, bottom = 6.dp),
    )
}

@Composable
private fun BrandItem(brand: IrBrand, subtitle: String? = null, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(RemotkuColors.Surface).clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(brand.name, style = MaterialTheme.typography.bodyLarge)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = RemotkuColors.TextMuted, maxLines = 1) }
            }
            Text("${brand.codeSetIds.size} kode", style = MaterialTheme.typography.labelSmall, color = RemotkuColors.TextMuted)
        }
        HorizontalDivider(Modifier.padding(start = 20.dp), color = RemotkuColors.Outline)
    }
}

/** Right-edge quick index: tap or drag to jump; letters without brands jump to the next available one. */
@Composable
private fun AlphabetIndex(anchors: Map<String, Int>, listState: LazyListState, active: String?, setActive: (String?) -> Unit) {
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val currentSetActive by rememberUpdatedState(setActive)

    fun jump(entry: String) {
        currentSetActive(entry)
        haptics.perform(HapticType.Tick)
        val start = INDEX_ENTRIES.indexOf(entry)
        val target = INDEX_ENTRIES.drop(start).firstNotNullOfOrNull { anchors[it] }
            ?: INDEX_ENTRIES.take(start).reversed().firstNotNullOfOrNull { anchors[it] } ?: return
        scope.launch { listState.scrollToItem(target) }
    }

    Column(
        Modifier
            .fillMaxHeight()
            .width(30.dp)
            .padding(vertical = 8.dp)
            .pointerInput(anchors) {
                awaitEachGesture {
                    var last: String? = null
                    fun pick(y: Float) {
                        val i = (y / size.height * INDEX_ENTRIES.size).toInt().coerceIn(0, INDEX_ENTRIES.lastIndex)
                        val entry = INDEX_ENTRIES[i]
                        if (entry != last) {
                            last = entry
                            jump(entry)
                        }
                    }
                    val down = awaitFirstDown()
                    down.consume()
                    pick(down.position.y)
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        change.consume()
                        pick(change.position.y)
                    }
                    currentSetActive(null)
                }
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        INDEX_ENTRIES.forEach { entry ->
            Text(
                entry,
                fontSize = if (entry == POPULAR_INDEX) 9.sp else 11.sp,
                fontWeight = if (entry == active) FontWeight.Bold else FontWeight.Medium,
                color = when {
                    entry == active -> RemotkuColors.Primary
                    anchors.containsKey(entry) -> RemotkuColors.Primary.copy(alpha = 0.85f)
                    else -> RemotkuColors.TextMuted.copy(alpha = 0.45f)
                },
            )
        }
    }
}
