package dev.satotek.cellscope

import android.Manifest
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.satotek.cellscope.ui.CellScopeRoot
import dev.satotek.cellscope.ui.theme.CellScopeTheme
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val vm get() = (application as CellScopeApp).vm

    private val perms = arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    /** True once the user has denied with "don't ask again": the system dialog will no longer appear, only Settings helps. */
    private val permanentlyDenied = mutableStateOf(false)
    private val inPip = mutableStateOf(false)

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        permanentlyDenied.value = result.any { (perm, granted) -> !granted && !shouldShowRequestPermissionRationale(perm) }
        vm.onPermissionsResult()
        syncPipParams()
    }

    private val pipCycleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_PIP_CYCLE) vm.cycleHud()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        inPip.value = isInPictureInPictureMode
        registerReceiver(pipCycleReceiver, IntentFilter(ACTION_PIP_CYCLE), RECEIVER_NOT_EXPORTED)
        setContent {
            CellScopeTheme {
                CellScopeRoot(
                    vm,
                    onRequestPermissions = ::requestPerms,
                    permanentlyDenied = permanentlyDenied.value,
                    onOpenSettings = ::openAppSettings,
                    inPip = inPip.value,
                    onEnterPip = ::enterPip,
                    onExit = ::exitApp,
                )
            }
        }
        requestPerms()
        syncPipParams()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(vm.pipAuto, vm.overlayEnabled, vm.state) { auto, overlay, st -> Triple(auto, overlay, st.permissionsGranted) }
                    .distinctUntilChanged()
                    .collect { syncPipParams() }
            }
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(pipCycleReceiver) }
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        vm.onPermissionsResult()
        vm.reconcileOverlay()
        syncPipParams()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip.value = isInPictureInPictureMode
        syncPipParams()
    }

    private fun pipParams(autoEnter: Boolean) = PictureInPictureParams.Builder()
        .setAspectRatio(Rational(4, 3))
        .setAutoEnterEnabled(autoEnter)
        .setSeamlessResizeEnabled(true)
        .setTitle(getString(R.string.app_name))
        .setActions(
            listOf(
                RemoteAction(
                    Icon.createWithResource(this, R.drawable.ic_pip_cycle),
                    getString(R.string.hud_cycle),
                    getString(R.string.hud_cycle),
                    PendingIntent.getBroadcast(
                        this, 0,
                        Intent(ACTION_PIP_CYCLE).setPackage(packageName),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                ),
            ),
        )
        .build()

    private fun syncPipParams() {
        val auto = vm.state.value.permissionsGranted && vm.pipAuto.value && !vm.overlayEnabled.value
        setPictureInPictureParams(pipParams(autoEnter = auto))
    }

    private fun enterPip() {
        if (isInPictureInPictureMode) return
        runCatching { enterPictureInPictureMode(pipParams(autoEnter = vm.pipAuto.value && !vm.overlayEnabled.value)) }
    }

    private fun requestPerms() { permLauncher.launch(perms) }

    /** Stop polling / overlay / recording, drop the task, then let the process go (the VM is process-wide). */
    private fun exitApp() {
        vm.shutdown()
        finishAndRemoveTask()
        window.decorView.postDelayed({ android.os.Process.killProcess(android.os.Process.myPid()) }, 400)
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }

    companion object {
        const val ACTION_PIP_CYCLE = "dev.satotek.cellscope.PIP_CYCLE"
    }
}
