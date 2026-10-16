package com.azza.core.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.azza.core.model.ContextFilter
import com.azza.core.model.SellingContexts

/** All / Personal / each store — shown only when there is more than one thing to choose. */
@Composable
fun ContextChips(contexts: SellingContexts, selected: ContextFilter, onSelect: (ContextFilter) -> Unit, modifier: Modifier = Modifier) {
    if (contexts.stores.isEmpty()) return
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(selected = selected.context == "all", onClick = { onSelect(ContextFilter.ALL) }, label = { Text(stringResource(R.string.context_all)) })
        if (contexts.hasPersonal) {
            FilterChip(selected = selected.context == "personal", onClick = { onSelect(ContextFilter.PERSONAL) }, label = { Text(stringResource(R.string.context_personal)) })
        }
        contexts.stores.forEach { (id, name) ->
            FilterChip(selected = selected.context == "store" && selected.storeId == id, onClick = { onSelect(ContextFilter.store(id)) }, label = { Text(name) })
        }
    }
}
