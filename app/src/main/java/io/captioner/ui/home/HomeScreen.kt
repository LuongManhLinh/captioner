package io.captioner.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onProjectOpen: (String) -> Unit,
    viewModel: HomeScreenViewModel = viewModel(factory = HomeScreenViewModel.factory)
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (state.creating) {
            ProjectLoading(state.progress, state.message)
        } else {
            ProjectView(
                projects = state.projects,
                onProjectClick = {
                    if (state.selectModeOn) {
                        viewModel.selectProject(it)
                    } else {
                        onProjectOpen(it)
                    }
                },
                onProjectPress = {
                    if (!state.selectModeOn) {
                        viewModel.enterSelectMode(it)
                    }
                },
                onPickVideo = { uri, karaoke ->
                    viewModel.createNewProject(context, uri, karaoke)
                },
                selectedIds = state.selectedProjectIds
            )

            if (state.selectModeOn) {
                SelectBar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .background(MaterialTheme.colorScheme.surface),
                    onClose = viewModel::exitSelectMode,
                    onDelete = { viewModel.deleteSelectedProjects(context) },
                    onShare = {},
                    onSelectAll = viewModel::selectAllProjects
                )
            }
        }
    }
}





