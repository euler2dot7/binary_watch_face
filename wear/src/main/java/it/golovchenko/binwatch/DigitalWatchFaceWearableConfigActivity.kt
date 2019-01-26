package it.golovchenko.binwatch

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.View.GONE
import kotlinx.android.synthetic.main.config.*

class DigitalWatchFaceWearableConfigActivity : Activity() {
    companion object {
        const val PREF = "binconf"
        const val GREEN = "green"
        const val BATTERY = "battery"
        const val REP_BIN = "bin"
        const val TXT_BINARY = "010"
        const val TXT_TERNARY = "+0-"
    }

    private var isBin = true
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.config)
        isBin = BuildConfig.APPLICATION_ID.contains("binwatch")
        representation.visibility = GONE
        with(getSharedPreferences(PREF, Context.MODE_PRIVATE)) {
            battery.setImageDrawable(
                resources.getDrawable(
                    if (getBoolean(BATTERY, true))
                        R.drawable.ic_remove_battery
                    else
                        R.drawable.ic_empty_battery
                    , baseContext.theme
                )
            )
            color_theme.setImageDrawable(
                resources.getDrawable(
                    if (getBoolean(GREEN, false))
                        R.drawable.ic_color_lens_white_24dp
                    else
                        R.drawable.ic_color_lens_green_24dp
                    , baseContext.theme
                )
            )
            representation.text = if (getBoolean(REP_BIN, isBin)) TXT_TERNARY else TXT_BINARY
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun onTheme(view: View) {
        with(getSharedPreferences(PREF, Context.MODE_PRIVATE)) {
            edit().putBoolean(GREEN, !getBoolean(GREEN, false)).apply()
        }
        finish()
    }

    @Suppress("UNUSED_PARAMETER")
    fun onBattery(view: View) {
        with(getSharedPreferences(PREF, Context.MODE_PRIVATE)) {
            edit().putBoolean(BATTERY, !getBoolean(BATTERY, true)).apply()
        }
        finish()
    }

    @Suppress("UNUSED_PARAMETER")
    fun onRep(view: View) {
        with(getSharedPreferences(PREF, Context.MODE_PRIVATE)) {
            edit().putBoolean(REP_BIN, !getBoolean(REP_BIN, true)).apply()
        }
        finish()
    }

}
