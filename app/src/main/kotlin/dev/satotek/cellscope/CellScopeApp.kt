package dev.satotek.cellscope

import android.app.Application

class CellScopeApp : Application() {
    /** Process-wide VM so the overlay HUD keeps polling after the activity is backgrounded. */
    val vm: MainViewModel by lazy { MainViewModel(this) }
}
