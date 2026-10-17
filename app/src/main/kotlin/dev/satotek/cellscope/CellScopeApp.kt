package dev.satotek.cellscope

import android.app.Application

class CellScopeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Remove credentials left by versions that offered AI integration.
        deleteSharedPreferences("ai_secrets")
    }

    /** Process-wide VM so the overlay HUD keeps polling after the activity is backgrounded. */
    val vm: MainViewModel by lazy { MainViewModel(this) }
}
