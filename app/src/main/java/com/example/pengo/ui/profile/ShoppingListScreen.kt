package com.example.pengo.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAddCheck
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.pengo.data.ShoppingListRepository
import com.example.pengo.ui.Routes
import com.example.pengo.ui.components.EmptyState
import com.example.pengo.ui.components.GlassCard
import com.example.pengo.ui.components.PengoBackground
import com.example.pengo.ui.components.PengoTopBar
import com.example.pengo.ui.theme.PengoColors
import com.example.pengo.ui.theme.PengoGradients

@Composable
fun ShoppingListScreen(nav: NavHostController) {
    val items by ShoppingListRepository.items.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }

    fun add() {
        ShoppingListRepository.add(text)
        text = ""
    }

    PengoBackground {
        Column(Modifier.fillMaxSize().imePadding()) {
            PengoTopBar("Shopping list", onBack = { nav.popBackStack() }) {
                if (items.any { it.done }) {
                    TextButton(onClick = { ShoppingListRepository.clearDone() }) { Text("Clear done", color = PengoColors.Red) }
                }
            }
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(60) },
                    placeholder = { Text("Add an item, e.g. milk, bread") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        unfocusedBorderColor = PengoColors.Divider,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = ::add, enabled = text.isNotBlank()) {
                    Icon(Icons.Rounded.Add, "Add", tint = PengoColors.PrimaryDeep)
                }
            }
            Text(
                "Items tick off automatically when you add a matching product to your cart. Saved on this phone, works offline.",
                style = MaterialTheme.typography.bodySmall,
                color = PengoColors.Muted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            if (items.isEmpty()) {
                EmptyState(
                    Icons.AutoMirrored.Rounded.PlaylistAddCheck,
                    "Your list is empty",
                    "Write down what you need before you reach the store.",
                    brush = PengoGradients.mint,
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items.sortedBy { it.done }, key = { it.id }) { item ->
                        GlassCard(
                            Modifier.fillMaxWidth(),
                            corner = 16.dp,
                            padding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            onClick = { ShoppingListRepository.toggle(item.id) },
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (item.done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                    if (item.done) "Done" else "Not done",
                                    tint = if (item.done) PengoColors.Success else PengoColors.Muted,
                                    modifier = Modifier.padding(8.dp),
                                )
                                Text(
                                    item.text,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (item.done) PengoColors.Muted else PengoColors.Ink,
                                    textDecoration = if (item.done) TextDecoration.LineThrough else null,
                                    modifier = Modifier.weight(1f),
                                )
                                if (!item.done) {
                                    IconButton(onClick = {
                                        nav.navigate(Routes.ProductList(title = "Results for \"${item.text}\"", query = item.text))
                                    }) { Icon(Icons.Rounded.Search, "Find ${item.text}", tint = PengoColors.PrimaryDeep, modifier = Modifier.size(20.dp)) }
                                }
                                IconButton(onClick = { ShoppingListRepository.remove(item.id) }) {
                                    Icon(Icons.Rounded.Close, "Remove", tint = PengoColors.Muted, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
