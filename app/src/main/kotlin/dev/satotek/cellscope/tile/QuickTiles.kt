package dev.satotek.cellscope.tile

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.StringRes
import dev.satotek.cellscope.CellScopeApp
import dev.satotek.cellscope.R
import dev.satotek.cellscope.data.telephony.RatLock
import dev.satotek.cellscope.overlay.OverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Toggles the overlay HUD without opening the app. */
class HudTileService : TileService() {
    private val vm get() = (application as CellScopeApp).vm

    override fun onStartListening() = render()

    override fun onClick() {
        if (!Settings.canDrawOverlays(this)) {
            // The grant lives on a system screen, so the tile can only send the user there.
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
            return
        }
        val on = !OverlayService.running
        vm.setOverlayEnabled(on)
        render(on)
    }

    /** The service starts and stops asynchronously, so right after a tap the request is the better guess. */
    private fun render(expected: Boolean = OverlayService.running) {
        val tile = qsTile ?: return
        tile.state = if (expected) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = if (Settings.canDrawOverlays(this)) null else getString(R.string.tile_hud_needs_permission)
        tile.updateTile()
    }
}

/**
 * Steps the RAT lock through the presets. Needs priv-app; otherwise the tile shows as unavailable
 * rather than failing on tap.
 */
class RatTileService : TileService() {
    private val vm get() = (application as CellScopeApp).vm
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    override fun onStartListening() { vm.refreshRatLock(); render() }

    override fun onClick() {
        if (vm.ratLockRaw.value == null) return
        // A changed RAT can drop the data connection, so it is not something to do from the lock screen.
        if (isLocked) unlockAndRun { step() } else step()
    }

    private fun step() {
        val next = next(vm.ratLock.value)
        scope.launch {
            vm.setRatLock(next).join()
            render()
        }
    }

    private fun render() {
        val tile = qsTile ?: return
        val mode = vm.ratLock.value
        when {
            vm.ratLockRaw.value == null -> {
                tile.state = Tile.STATE_UNAVAILABLE
                tile.subtitle = getString(R.string.rat_lock_needs_privapp)
            }
            else -> {
                tile.state = if (mode == RatLock.Mode.AUTO) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
                tile.subtitle = vm.ratLockError.value?.let { getString(R.string.tile_rat_failed) }
                    ?: mode?.let { getString(label(it)) } ?: getString(R.string.tile_rat_custom)
            }
        }
        tile.updateTile()
    }

    companion object {
        /** Most-used first; 3G-only stays in Settings, where landing on it by accident is unlikely. */
        val CYCLE = listOf(RatLock.Mode.AUTO, RatLock.Mode.NR_LTE, RatLock.Mode.NR_ONLY, RatLock.Mode.LTE_ONLY)

        fun next(current: RatLock.Mode?): RatLock.Mode {
            val i = CYCLE.indexOf(current)
            return if (i < 0) RatLock.Mode.AUTO else CYCLE[(i + 1) % CYCLE.size]
        }

        @StringRes fun label(mode: RatLock.Mode): Int = when (mode) {
            RatLock.Mode.AUTO -> R.string.rat_auto
            RatLock.Mode.NR_LTE -> R.string.rat_nr_lte
            RatLock.Mode.NR_ONLY -> R.string.rat_nr
            RatLock.Mode.LTE_ONLY -> R.string.rat_lte
            RatLock.Mode.LTE_WCDMA -> R.string.rat_lte_wcdma
            RatLock.Mode.WCDMA_ONLY -> R.string.rat_wcdma
        }
    }
}
