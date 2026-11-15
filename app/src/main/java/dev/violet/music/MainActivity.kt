package dev.violet.music

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.net.toUri
import dagger.hilt.android.AndroidEntryPoint
import dev.violet.music.core.media.AudioPermission
import dev.violet.music.core.ui.VioletApp
import dev.violet.music.core.ui.theme.VioletTheme
import dev.violet.music.feature.library.LibraryViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: LibraryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                viewModel.permissionChecked(AudioPermission.isGranted(this))
            }
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        viewModel.permissionChecked(AudioPermission.isGranted(this@MainActivity))
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            VioletTheme {
                VioletApp(
                    viewModel = viewModel,
                    onPermission = { permissionLauncher.launch(AudioPermission.name) },
                    onAndroidSettings = {
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
                    },
                )
            }
        }
    }
}
