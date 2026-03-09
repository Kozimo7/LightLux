package com.example.lightluxmeter.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lightluxmeter.R
import com.example.lightluxmeter.ui.viewmodels.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val isDarkMode by viewModel.isDarkMode.collectAsState()

    // We use a simple remember for Language UI state since it's handled via AppCompatDelegate
    var currentLang by remember { mutableStateOf(viewModel.getCurrentLanguage()) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                colors =
                        TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
        )

        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            item {
                Text(
                        text = stringResource(R.string.settings_appearance),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            item {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                            text = stringResource(R.string.settings_dark_mode),
                            style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(checked = isDarkMode, onCheckedChange = { viewModel.setDarkMode(it) })
                }
            }

            item { Divider(modifier = Modifier.padding(vertical = 16.dp)) }

            item {
                Text(
                        text = stringResource(R.string.settings_language),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            item {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                            text = stringResource(R.string.lang_english),
                            style = MaterialTheme.typography.bodyLarge
                    )
                    RadioButton(
                            selected = currentLang.startsWith("en"),
                            onClick = {
                                viewModel.setLanguage("en")
                                currentLang = "en"
                            }
                    )
                }
            }

            item {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                            text = stringResource(R.string.lang_romanian),
                            style = MaterialTheme.typography.bodyLarge
                    )
                    RadioButton(
                            selected = currentLang.startsWith("ro"),
                            onClick = {
                                viewModel.setLanguage("ro")
                                currentLang = "ro"
                            }
                    )
                }
            }
        }
    }
}
