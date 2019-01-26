package it.golovchenko.binwatch

import android.content.*
import android.graphics.*
import android.graphics.Typeface.NORMAL
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.v4.content.ContextCompat
import android.support.v4.content.res.ResourcesCompat
import android.support.wearable.watchface.CanvasWatchFaceService
import android.support.wearable.watchface.WatchFaceService
import android.support.wearable.watchface.WatchFaceStyle
import android.util.Log
import android.view.SurfaceHolder
import android.view.WindowInsets
import it.golovchenko.binwatch.DigitalWatchFaceWearableConfigActivity.Companion.BATTERY
import it.golovchenko.binwatch.DigitalWatchFaceWearableConfigActivity.Companion.GREEN
import it.golovchenko.binwatch.DigitalWatchFaceWearableConfigActivity.Companion.PREF
import it.golovchenko.binwatch.DigitalWatchFaceWearableConfigActivity.Companion.REP_BIN
import java.lang.ref.WeakReference
import java.util.*
import java.util.Calendar.*

class BinWatchFace : CanvasWatchFaceService() {

    companion object {
        private val TAG = BinWatchFace::class.java.simpleName
        private const val INTERACTIVE_UPDATE_RATE_MS = 1000
        private const val MSG_UPDATE_TIME = 0

        private fun convertNum(n: Int, dec: Boolean = false) =
            if (dec) convertToBin(n)
            else convertToBT(n)


        private fun convertToBin(n: Int): String {
            var num = n
            var binaryNumber: Long = 0
            var remainder: Int
            var i = 1

            while (num != 0) {
                remainder = num % 2
                num /= 2
                binaryNumber += (remainder * i).toLong()
                i *= 10
            }

            return binaryNumber.toString()
        }


        private fun convertToBT(v: Int): String {
            if (v < 0)
                return flip(convertToBT(-v))
            if (v == 0)
                return ""
            val rem = mod3(v)
            if (rem == 0)
                return convertToBT(v / 3) + "0"
            if (rem == 1)
                return convertToBT(v / 3) + "+"
            if (rem == 2)
                return convertToBT((v + 1) / 3) + "-"
            else return " error "
        }

        private fun flip(s: String): String {
            var flip = ""
            for (i in 0 until s.length) {
                if (s[i] == '+')
                    flip += '-'.toString()
                else if (s[i] == '-')
                    flip += '+'.toString()
                else
                    flip += '0'.toString()
            }
            return flip
        }

        private fun mod3(v: Int): Int {
            var v = v
            if (v > 0)
                return v % 3
            v = v % 3
            return (v + 3) % 3
        }
    }


    override fun onCreateEngine(): Engine {
        return Engine()
    }

    private class EngineHandler(reference: BinWatchFace.Engine) : Handler() {
        private val mWeakReference: WeakReference<BinWatchFace.Engine> = WeakReference(reference)

        override fun handleMessage(msg: Message) {
            mWeakReference.get()?.apply {
                if (msg.what == MSG_UPDATE_TIME) handleUpdateTimeMessage()
            }
        }
    }

    var humanTimeCount = 0
    private lateinit var fontFace: Typeface

    inner class Engine : CanvasWatchFaceService.Engine() {
        private lateinit var mCalendar: Calendar
        private var mRegisteredTimeZoneReceiver = false
        private var mXOffset: Float = 0F
        private var mYOffset: Float = 0F
        private lateinit var mBackgroundPaint: Paint
        private lateinit var mHourPaint: Paint
        private lateinit var mCenterPaint: Paint
        private lateinit var mSecondsPaint: Paint
        private lateinit var mBatteryPaint: Paint
        private lateinit var fields: List<Paint>
        private var mLowBitAmbient: Boolean = false
        private var mBurnInProtection: Boolean = false
        private var mAmbient: Boolean = false
        private var mShowBatt: Boolean = false
        private var mBinRep: Boolean = false
        private var mIsGreen: Boolean = false

        //        val fieldsConf = mapOf(true to arrayOf(4, 6, 6, 7), false to arrayOf(3, 5, 5, 5))
        private val mUpdateTimeHandler: Handler = EngineHandler(this)

        private val mTimeZoneReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                mCalendar.timeZone = TimeZone.getDefault()
                invalidate()
            }
        }

        private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { pref, key ->
            Log.d(TAG, "onSharedPreferenceChanged : $key ")
            if (key == GREEN) {
                mIsGreen = pref?.getBoolean(GREEN, false) ?: false
                fields.forEach { it.color = if (mIsGreen) Color.GREEN else Color.WHITE }
            } else if (key == BATTERY) {
                mShowBatt = pref?.getBoolean(BATTERY, true) ?: false
            } else if (key == REP_BIN) {
                mBinRep = pref?.getBoolean(REP_BIN, true) ?: false
            }

        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)

            with(getSharedPreferences(PREF, Context.MODE_PRIVATE)) {
                mShowBatt = getBoolean(BATTERY, true)
                mIsGreen = getBoolean(GREEN, false)
                mBinRep = getBoolean(REP_BIN, true)

                mBinRep = BuildConfig.APPLICATION_ID.contains("binwatch")

                registerOnSharedPreferenceChangeListener(prefListener)
            }

            setWatchFaceStyle(
                WatchFaceStyle
                    .Builder(this@BinWatchFace)
                    .setAcceptsTapEvents(true)
                    .build()
            )

            mCalendar = Calendar.getInstance()

            val resources = this@BinWatchFace.resources
            mYOffset = resources.getDimension(R.dimen.digital_y_offset)

            // Initializes background.
            mBackgroundPaint = Paint().apply {
                color = ContextCompat.getColor(applicationContext, R.color.background)
            }
            fontFace = Typeface.create(ResourcesCompat.getFont(applicationContext, R.font.dig), NORMAL)
            mHourPaint = initTextPaint()
            mCenterPaint = initTextPaint()
            mSecondsPaint = initTextPaint()
            mBatteryPaint = initTextPaint()

            fields = listOf(mHourPaint, mCenterPaint, mSecondsPaint, mBatteryPaint)
        }


        private fun initTextPaint(): Paint = Paint().apply {
            typeface = Typeface.MONOSPACE
            typeface = fontFace
            isAntiAlias = true
            color = ContextCompat.getColor(applicationContext, R.color.digital_text)
        }

        override fun onDestroy() {
            Log.d(TAG, "onDestroy  ")
            try {
                getSharedPreferences(PREF, Context.MODE_PRIVATE)
                    .unregisterOnSharedPreferenceChangeListener(prefListener)
            } catch (e: Exception) {
                Log.e(TAG, "onDestroy: ", e)
            }
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            super.onDestroy()
        }

        override fun onPropertiesChanged(properties: Bundle) {
            super.onPropertiesChanged(properties)
            mLowBitAmbient = properties.getBoolean(WatchFaceService.PROPERTY_LOW_BIT_AMBIENT, false)
            mBurnInProtection = properties.getBoolean(WatchFaceService.PROPERTY_BURN_IN_PROTECTION, false)
        }

        override fun onTimeTick() {
            super.onTimeTick()
            invalidate()
        }

        override fun onAmbientModeChanged(inAmbientMode: Boolean) {
            super.onAmbientModeChanged(inAmbientMode)
            mAmbient = inAmbientMode

            if (mLowBitAmbient) {
                mHourPaint.isAntiAlias = !inAmbientMode
                mCenterPaint.isAntiAlias = !inAmbientMode
                mSecondsPaint.isAntiAlias = !inAmbientMode
                mBatteryPaint.isAntiAlias = !inAmbientMode
            }

            if (mIsGreen)
                fields.forEach { it.color = if (mAmbient) Color.WHITE else Color.GREEN }

            updateTimer()
        }

        override fun onTapCommand(tapType: Int, x: Int, y: Int, eventTime: Long) {
            if (tapType == WatchFaceService.TAP_TYPE_TAP) humanTimeCount = 3
            invalidate()
        }

        private fun getBatteryLevel(): Int {
            val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { iFilter ->
                this@BinWatchFace.registerReceiver(null, iFilter)
            }

            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 0
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 1
            val retVal = (level / scale.toFloat() * 100F).toInt()

            return retVal
        }

        private fun drawField(
            canvas: Canvas,
            bounds: Rect,
            textPaint: Paint,
            text: String,
            amb: Float,
            nAmb: Float,
            bat: Boolean = false,
            real: Boolean = false
        ) {
            val textBounds = Rect()

            textPaint.getTextBounds(if (mBinRep) text else "".padStart(text.length, '0'), 0, text.length, textBounds)

            val textX = Math.abs(bounds.centerX() - textBounds.centerX()).toFloat()

            val textY = if (!mAmbient)
                Math.abs(bounds.centerY() - textBounds.centerY() + nAmb * textBounds.centerY())
            else
                Math.abs(bounds.centerY() - textBounds.centerY() + amb * textBounds.centerY())

            canvas.drawText(text, textX, textY, textPaint)
            if (bat) {
                val batteryText =
//                    if (real) "${getBatteryLevel()}%" else "${convertToBin(getBatteryLevel())}%".padStart(
                    if (real) "${getBatteryLevel()}%" else "${convertNum(getBatteryLevel(), mBinRep)}%".padStart(
                        if (mBinRep) 7 else 5,
                        '0'
                    )
                val batteryTextBounds = Rect()
                mBatteryPaint.getTextBounds(batteryText, 0, batteryText.length, batteryTextBounds)
                val textBX =
                    Math.abs(bounds.centerX() + textBounds.width() / 2 - batteryTextBounds.width() + batteryTextBounds.centerY())
                        .toFloat()
                val textBY = Math.abs(textY - 3 * batteryTextBounds.centerY())

                canvas.drawText(batteryText, textBX, textBY, mBatteryPaint)
            }
        }

        override fun onDraw(canvas: Canvas, bounds: Rect) {

            canvas.drawColor(Color.BLACK)

            if (mAmbient) humanTimeCount = 0

            val now = System.currentTimeMillis()

            mCalendar.timeInMillis = now

            if (humanTimeCount <= 0)
                drawField(
                    canvas, bounds, mHourPaint,
//                    convertToBin(mCalendar.get(HOUR)).toString().padStart(4, '0'),
                    convertNum(mCalendar.get(HOUR), mBinRep).padStart(if (mBinRep) 4 else 3, '0'),
                    1.5F, 2.5F
                )

            var decBattery = false

            val centerText = mCalendar.run {
                if (humanTimeCount > 0) {
                    decBattery = true
                    String.format("%d:%02d:%02d", get(HOUR_OF_DAY), get(MINUTE), get(SECOND))
                } else

//                    convertToBin(get(MINUTE)).toString().padStart(6, '0')
                    convertNum(get(MINUTE), mBinRep).padStart(if (mBinRep) 6 else 5, '0')
            }

            drawField(
                canvas, bounds, mCenterPaint, centerText, -1.5F, 0F,
                mAmbient && mShowBatt || decBattery && mShowBatt, decBattery
            )

            if (!mAmbient && humanTimeCount <= 0) {
                drawField(
                    canvas, bounds, mSecondsPaint,
//                    convertToBin(mCalendar.get(SECOND)).toString().padStart(6, '0'),
                    convertNum(mCalendar.get(SECOND), mBinRep).padStart(if (mBinRep) 6 else 5, '0'),
                    -2.5F, -2.5F, mShowBatt
                )
                val n = mCalendar.get(SECOND)
//                Log.d(TAG, "onDraw : $n -> ${convertToBT(n)} ")
            } else humanTimeCount--
        }


        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)

            if (visible) {
                registerReceiver()
                mCalendar.timeZone = TimeZone.getDefault()
                invalidate()
            } else {
                unregisterReceiver()
            }
            updateTimer()
        }

        private fun registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = true
            val filter = IntentFilter(Intent.ACTION_TIMEZONE_CHANGED)
            this@BinWatchFace.registerReceiver(mTimeZoneReceiver, filter)
        }

        private fun unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = false
            this@BinWatchFace.unregisterReceiver(mTimeZoneReceiver)
        }

        override fun onApplyWindowInsets(insets: WindowInsets) {
            super.onApplyWindowInsets(insets)
            val resources = this@BinWatchFace.resources
            val isRound = insets.isRound
            mXOffset = resources.getDimension(if (isRound) R.dimen.digital_x_offset_round else R.dimen.digital_x_offset)

            val textSize =
                resources.getDimension(if (isRound) R.dimen.digital_text_size_round else R.dimen.digital_text_size)

            mHourPaint.textSize = textSize
            mCenterPaint.textSize = textSize
            mSecondsPaint.textSize = textSize
            mBatteryPaint.textSize =
                resources.getDimension(if (isRound) R.dimen.digital_text_size_round_small else R.dimen.digital_text_size_small)

            if (mIsGreen) fields.forEach { it.color = Color.GREEN }
        }

        private fun updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME)
            }
        }

        private fun shouldTimerBeRunning(): Boolean {
            return isVisible && !isInAmbientMode
        }

        fun handleUpdateTimeMessage() {
            invalidate()
            if (shouldTimerBeRunning()) {
                val timeMs = System.currentTimeMillis()
                val delayMs = INTERACTIVE_UPDATE_RATE_MS - timeMs % INTERACTIVE_UPDATE_RATE_MS
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs)
            }
        }
    }
}
