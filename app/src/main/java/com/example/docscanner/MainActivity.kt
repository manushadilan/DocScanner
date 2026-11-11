package com.example.docscanner

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.docscanner.data.DocumentViewModel
import com.example.docscanner.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppTheme { AppNav() } }
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme(); val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme() else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Flow: pages -> capture -> crop -> enhance -> (saved) back to pages. */
@Composable
fun AppNav() {
    val nav = rememberNavController()
    val vm: DocumentViewModel = viewModel() // one instance shared by every screen
    NavHost(nav, startDestination = "pages") {
        composable("pages") { PagesScreen(vm, onAdd = { nav.navigate("capture") }) }
        composable("capture") { CaptureScreen(vm, onCaptured = { nav.navigate("crop") }) }
        composable("crop") { CropScreen(vm, onRetake = { nav.popBackStack() }, onDone = { nav.navigate("enhance") }) }
        composable("enhance") { EnhanceScreen(vm, onSaved = { nav.popBackStack("pages", inclusive = false) }) }
    }
}
