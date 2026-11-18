package com.xperience.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.xperience.camera.ui.CameraScreen
import com.xperience.camera.ui.Chip
import com.xperience.camera.ui.GalleryScreen
import com.xperience.camera.ui.SettingsScreen
import com.xperience.camera.ui.XC
import com.xperience.camera.update.UpdateChecker
import com.xperience.camera.update.UpdateInfo
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val vm: CameraViewModel by viewModels()
    private var granted by mutableStateOf(false)
    private var screen by mutableStateOf("camera")
    private var update by mutableStateOf<UpdateInfo?>(null)

    private val askCamera = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        granted = r[Manifest.permission.CAMERA] == true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (!granted) requestPerms()
        if (intent?.getBooleanExtra(UpdateChecker.EXTRA_DOWNLOAD, false) == true) checkUpdate(manual = true)
        else checkUpdate(manual = false)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = XC.Accent, background = XC.Bg, surface = XC.Panel2)) {
                when {
                    !granted -> PermissionScreen()
                    screen == "settings" -> {
                        BackHandler { screen = "camera" }
                        SettingsScreen(vm, UpdateChecker.currentVersion(this), onCheckUpdate = { checkUpdate(true) }) { screen = "camera" }
                    }
                    screen == "gallery" -> GalleryScreen(vm) { screen = "camera"; vm.refreshLast() }
                    else -> CameraScreen(vm, onSettings = { screen = "settings" }, onGallery = { screen = "gallery" })
                }
                update?.let { info ->
                    AlertDialog(
                        onDismissRequest = { update = null },
                        containerColor = XC.Panel2,
                        title = { Text("XPERIENCE v${info.version} tersedia", color = XC.Accent, fontWeight = FontWeight.Bold) },
                        text = { Text("Versi anda: v${UpdateChecker.currentVersion(this)}\n\n${info.notes}", color = XC.Text, fontSize = 13.sp) },
                        confirmButton = { TextButton(onClick = { UpdateChecker.download(this, info); update = null }) { Text("Muat turun", color = XC.Accent) } },
                        dismissButton = { TextButton(onClick = { update = null }) { Text("Nanti", color = XC.Dim) } },
                    )
                }
            }
        }
    }

    private fun requestPerms() {
        val p = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= 33) p += Manifest.permission.POST_NOTIFICATIONS
        if (Build.VERSION.SDK_INT <= 28) p += Manifest.permission.WRITE_EXTERNAL_STORAGE
        askCamera.launch(p.toTypedArray())
    }

    @androidx.compose.runtime.Composable
    private fun PermissionScreen() {
        Column(
            Modifier.fillMaxSize().background(XC.Bg).padding(32.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("XPERIENCE", color = XC.Text, fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp)
            Spacer(Modifier.height(10.dp))
            Text("Kamera profesional memerlukan kebenaran kamera.", color = XC.Dim, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Chip("Benarkan kamera", true) { requestPerms() }
        }
    }

    private fun checkUpdate(manual: Boolean) {
        lifecycleScope.launch {
            val info = UpdateChecker.check(this@MainActivity)
            if (info != null) { update = info; UpdateChecker.notify(this@MainActivity, info) }
            else if (manual) vm.toast.value = "Anda guna versi terkini (v${UpdateChecker.currentVersion(this@MainActivity)})"
        }
    }

    override fun onResume() {
        super.onResume()
        UpdateChecker.resumePending(this)
        if (!granted) granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(UpdateChecker.EXTRA_DOWNLOAD, false)) checkUpdate(true)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (screen == "camera" && granted && vm.settings.value.volumeShutter &&
            (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_CAMERA)
        ) {
            if (event?.repeatCount == 0) vm.shutter()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
