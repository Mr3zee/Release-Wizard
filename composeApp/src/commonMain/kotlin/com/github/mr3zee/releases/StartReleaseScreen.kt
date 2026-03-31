package com.github.mr3zee.releases

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.github.mr3zee.components.RwButton
import com.github.mr3zee.components.RwButtonVariant
import com.github.mr3zee.components.RwTextField
import com.github.mr3zee.i18n.packStringResource
import com.github.mr3zee.model.ReleaseId
import com.github.mr3zee.util.resolve
import com.github.mr3zee.theme.AppTypography
import com.github.mr3zee.theme.LocalAppColors
import com.github.mr3zee.theme.Spacing
import releasewizard.composeapp.generated.resources.Res
import releasewizard.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartReleaseScreen(
    viewModel: StartReleaseViewModel,
    onBack: () -> Unit,
    onReleaseStarted: (ReleaseId) -> Unit,
) {
    val project by viewModel.project.collectAsState()
    val releaseName by viewModel.releaseName.collectAsState()
    val parameters by viewModel.parameters.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isStarting by viewModel.isStarting.collectAsState()
    val error by viewModel.error.collectAsState()
    val colors = LocalAppColors.current

    val canStart = remember(releaseName, parameters) {
        releaseName.isNotBlank() && parameters.all { it.value.isNotBlank() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(packStringResource(Res.string.start_release_title))
                        val projectName = project?.name
                        if (projectName != null) {
                            Text(
                                projectName,
                                style = AppTypography.bodySmall,
                                color = colors.chromeTextSecondary,
                            )
                        }
                    }
                },
                navigationIcon = {
                    RwButton(
                        onClick = onBack,
                        variant = RwButtonVariant.Ghost,
                        modifier = Modifier.testTag("start_release_back"),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = packStringResource(Res.string.common_navigate_back))
                        Text(packStringResource(Res.string.common_back))
                    }
                },
            )
        },
        modifier = Modifier.testTag("start_release_screen"),
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                val loadingDesc = packStringResource(Res.string.start_release_loading)
                CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = loadingDesc })
            }
            return@Scaffold
        }

        if (project == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    packStringResource(Res.string.start_release_project_not_found),
                    style = AppTypography.body,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("start_release_not_found"),
                )
            }
            return@Scaffold
        }

        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                // Release name
                RwTextField(
                    value = releaseName,
                    onValueChange = { viewModel.updateReleaseName(it) },
                    label = packStringResource(Res.string.start_release_name_label),
                    placeholder = packStringResource(Res.string.start_release_name_placeholder),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("start_release_name"),
                )

                // Parameters section
                if (parameters.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(
                            packStringResource(Res.string.start_release_parameters_section),
                            style = AppTypography.subheading,
                            color = colors.chromeTextSecondary,
                            modifier = Modifier.testTag("start_release_params_header"),
                        )

                        parameters.forEach { param ->
                            val isEmpty = param.value.isBlank()
                            RwTextField(
                                value = param.value,
                                onValueChange = { viewModel.updateParameter(param.key, it) },
                                label = param.label.ifBlank { param.key },
                                placeholder = param.key,
                                singleLine = true,
                                isError = isEmpty,
                                supportingText = when {
                                    isEmpty -> {
                                        { Text(packStringResource(Res.string.start_release_param_required), style = AppTypography.caption) }
                                    }
                                    param.description.isNotBlank() -> {
                                        { Text(param.description, style = AppTypography.caption) }
                                    }
                                    else -> null
                                },
                                modifier = Modifier.fillMaxWidth().testTag("start_release_param_${param.key}"),
                            )
                        }
                    }
                }

                // Error
                val currentError = error
                if (currentError != null) {
                    Text(
                        text = currentError.resolve(),
                        color = MaterialTheme.colorScheme.error,
                        style = AppTypography.bodySmall,
                        modifier = Modifier.testTag("start_release_error"),
                    )
                }

                // Start button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    RwButton(
                        onClick = { viewModel.startRelease(onReleaseStarted) },
                        enabled = canStart && !isStarting,
                        variant = RwButtonVariant.Primary,
                        modifier = Modifier.testTag("start_release_confirm"),
                    ) {
                        if (isStarting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(Spacing.lg),
                                strokeWidth = Spacing.xxs,
                            )
                            Spacer(Modifier.width(Spacing.sm))
                        }
                        Text(packStringResource(Res.string.start_release_start))
                    }
                }
            }
        }
    }
}
