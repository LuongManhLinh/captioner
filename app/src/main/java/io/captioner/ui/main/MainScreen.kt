package io.captioner.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.captioner.ui.editor.EditorScreen
import io.captioner.ui.export.ExportScreen
import io.captioner.ui.home.HomeScreen

@Composable
fun MainScreen() {
    val navController = rememberNavController()

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        StoragePermissionScreen()
        NavigationScreen(
            navController = navController,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

@Composable
fun StoragePermissionScreen() {
    val context = LocalContext.current

    val readPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val writePermission = Manifest.permission.WRITE_EXTERNAL_STORAGE

    val readLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) Log.d("Permission", "Granted Read permission")
        else Log.d("Permission", "Denied Read permission")
    }

    val writeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) Log.d("Permission", "Granted Write permission")
        else Log.d("Permission", "Denied Write permission")
    }

    LaunchedEffect(Unit) {
        val writeGranted = ContextCompat.checkSelfPermission(
            context, writePermission
        ) == PackageManager.PERMISSION_GRANTED
        if (!writeGranted) writeLauncher.launch(writePermission)
    }

    LaunchedEffect(Unit) {
        val readGranted = ContextCompat.checkSelfPermission(
            context, readPermission
        ) == PackageManager.PERMISSION_GRANTED
        if (!readGranted) readLauncher.launch(readPermission)
    }
}

@Composable
private fun NavigationScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = modifier
    ) {
        composable(route = "home") {
            HomeScreen(
                onProjectOpen = { id ->
                    navController.navigate("editor/$id")
                },
            )
        }

        composable(
            route = "editor/{projectId}",
            arguments = listOf(
                navArgument("projectId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId")
            if (projectId != null) {
                EditorScreen(
                    projectId = projectId,
                    onBack = { navController.navigateUp() },
                    onExport = { pid, flag ->
                        navController.navigate("export/$pid/$flag")
                    }
                )
            } else {
                navController.navigateUp()
            }
        }

        // ← thêm route export mới
        composable(
            route = "export/{projectId}/{flag}",
            arguments = listOf(
                navArgument("projectId") { type = NavType.StringType },
                navArgument("flag") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId")
            val flag = backStackEntry.arguments?.getInt("flag")
            if (projectId != null && flag != null) {
                ExportScreen(
                    projectId = projectId,
                    flag = flag,
                    onBack = { navController.navigateUp() },
                    onDone = { navController.navigate("home") }
                )
            } else {
                navController.navigateUp()
            }
        }
    }
}