package com.chiiii5640.thsrapp.features.searchDashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chiiii5640.thsrapp.core.model.Station
import com.chiiii5640.thsrapp.core.time.ThsrFormatters
import com.chiiii5640.thsrapp.features.trainResults.TrainOptionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchDashboardScreen(viewModel: SearchDashboardViewModel) {
    val state by viewModel.uiState.collectAsState()
    val result = (state.loadState as? SearchLoadState.Loaded)?.result
    val filtered = state.selectedFilter.apply(result?.options.orEmpty())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("高鐵查詢") },
                actions = {
                    IconButton(onClick = viewModel::forceRefresh) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "強制更新")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        ) {
            item {
                QueryFormSection(
                    state = state,
                    onOrigin = viewModel::setOrigin,
                    onDestination = viewModel::setDestination,
                    onSwap = viewModel::swapRoute,
                    onForceRefresh = viewModel::forceRefresh,
                )
            }
            item {
                when (val loadState = state.loadState) {
                    SearchLoadState.Idle -> Unit
                    SearchLoadState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    is SearchLoadState.Failed -> Text(loadState.message)
                    is SearchLoadState.Loaded -> DataSourceSection(loadState.result)
                }
            }
            item {
                ResultFilterBar(
                    selected = state.selectedFilter,
                    onSelected = viewModel::setFilter,
                )
            }
            items(filtered, key = { it.trainNo }) { option ->
                TrainOptionCard(option)
            }
        }
    }
}

@Composable
private fun QueryFormSection(
    state: SearchDashboardUiState,
    onOrigin: (Station) -> Unit,
    onDestination: (Station) -> Unit,
    onSwap: () -> Unit,
    onForceRefresh: () -> Unit,
) {
    ElevatedCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StationPicker("起站", state.origin, onOrigin, Modifier.weight(1f))
                IconButton(onClick = onSwap) {
                    Icon(Icons.Outlined.SwapVert, contentDescription = "交換起迄站")
                }
                StationPicker("迄站", state.destination, onDestination, Modifier.weight(1f))
            }
            Text("日期 ${ThsrFormatters.date(state.travelDate)}  出發 ${ThsrFormatters.time(state.departureAfter)}")
            Button(onClick = onForceRefresh, modifier = Modifier.fillMaxWidth()) {
                Text("強制更新")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StationPicker(label: String, selected: Station, onSelected: (Station) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Station.entries.forEach { station ->
                FilterChip(
                    selected = station == selected,
                    onClick = { onSelected(station) },
                    label = { Text(station.localName) },
                )
            }
        }
    }
}

@Composable
private fun DataSourceSection(result: SearchResult) {
    ElevatedCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            result.sourceStatuses.forEach { status ->
                Text("${status.label} / ${status.state}")
            }
        }
    }
}

@Composable
private fun ResultFilterBar(selected: ResultFilter, onSelected: (ResultFilter) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ResultFilter.entries.forEach { filter ->
            AssistChip(
                onClick = { onSelected(filter) },
                label = { Text(if (filter == selected) "${filter.label} ✓" else filter.label) },
            )
        }
    }
    Spacer(Modifier.height(2.dp))
}
