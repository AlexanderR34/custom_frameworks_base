package com.android.systemui.statusbar.phone.island

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import androidx.annotation.DrawableRes
import com.android.systemui.res.R

/**
 * Reusable 3-Button Squircle Quick-Action Mini Menu (Dynamic Island Style)
 * Anchors beneath status bar chips (Timer, Hotspot, Bluetooth, etc.) .
 */
class ChipIslandPopup(
    private val context: Context,
    private val popupTitle: String = "ChipIslandPopup",
) {

    companion object {
        private const val AUTO_DISMISS_DELAY_MS = 6000L
        private const val TOUCH_OUTSIDE_GUARD_TIME_MS = 300L
    }

    private val mWindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mPopupView: View
    private val mBtnLeft: ImageView
    private val mBtnCenter: ImageView
    private val mBtnRight: ImageView
    private val mHandler = Handler(Looper.getMainLooper())

    private var mOnLeftClick: (() -> Unit)? = null
    private var mOnCenterClick: (() -> Unit)? = null
    private var mOnRightClick: (() -> Unit)? = null

    private var mCustomCenterTint: Int? = null
    private var mCustomLeftTint: Int? = null
    private var mCustomRightTint: Int? = null
    private var mCurrentIconTintColor: Int? = null

    private val mAutoDismissRunnable = Runnable {
        dismissWithAnimation()
    }

    private var mLastShowTime: Long = 0L
    private var mIsAttached = false

    init {
        mPopupView = LayoutInflater.from(context).inflate(R.layout.chip_island_popup, null)
        mBtnLeft = mPopupView.findViewById(R.id.chip_island_btn_left)
        mBtnCenter = mPopupView.findViewById(R.id.chip_island_btn_center)
        mBtnRight = mPopupView.findViewById(R.id.chip_island_btn_right)

        setupInteractions()
    }

    private fun setupInteractions() {
        mPopupView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                if (SystemClock.elapsedRealtime() - mLastShowTime > TOUCH_OUTSIDE_GUARD_TIME_MS) {
                    dismissWithAnimation()
                }
                true
            } else if (event.action == MotionEvent.ACTION_DOWN) {
                resetAutoDismissTimer()
                false
            } else {
                false
            }
        }

        mBtnLeft.setOnClickListener {
            resetAutoDismissTimer()
            mOnLeftClick?.invoke()
        }

        mBtnCenter.setOnClickListener {
            resetAutoDismissTimer()
            mOnCenterClick?.invoke()
        }

        mBtnRight.setOnClickListener {
            resetAutoDismissTimer()
            mOnRightClick?.invoke()
        }
    }

    fun setLeftButton(
        @DrawableRes iconRes: Int,
        contentDescription: String? = null,
        customTint: Int? = null,
        onClick: (() -> Unit)? = null,
    ) {
        mBtnLeft.setImageResource(iconRes)
        mBtnLeft.contentDescription = contentDescription
        mCustomLeftTint = customTint
        val tint = customTint ?: mCurrentIconTintColor
        if (tint != null) {
            mBtnLeft.setColorFilter(tint)
        }
        if (onClick != null) {
            mOnLeftClick = onClick
        }
    }

    fun setCenterButton(
        @DrawableRes iconRes: Int,
        contentDescription: String? = null,
        customTint: Int? = null,
        onClick: (() -> Unit)? = null,
    ) {
        mBtnCenter.setImageResource(iconRes)
        mBtnCenter.contentDescription = contentDescription
        mCustomCenterTint = customTint
        val tint = customTint ?: mCurrentIconTintColor
        if (tint != null) {
            mBtnCenter.setColorFilter(tint)
        }
        if (onClick != null) {
            mOnCenterClick = onClick
        }
    }

    fun setRightButton(
        @DrawableRes iconRes: Int,
        contentDescription: String? = null,
        customTint: Int? = null,
        onClick: (() -> Unit)? = null,
    ) {
        mBtnRight.setImageResource(iconRes)
        mBtnRight.contentDescription = contentDescription
        mCustomRightTint = customTint
        val tint = customTint ?: mCurrentIconTintColor
        if (tint != null) {
            mBtnRight.setColorFilter(tint)
        }
        if (onClick != null) {
            mOnRightClick = onClick
        }
    }

    private fun resetAutoDismissTimer() {
        mHandler.removeCallbacks(mAutoDismissRunnable)
        mHandler.postDelayed(mAutoDismissRunnable, AUTO_DISMISS_DELAY_MS)
    }

    fun applyMonetTheme(accentColor: Int? = null) {
        val res = context.resources
        val isDark = (res.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        // 1. Resolve Monet surface container color
        val cardBgColor = if (accentColor != null) {
            val hsv = FloatArray(3)
            Color.colorToHSV(accentColor, hsv)
            hsv[1] = (hsv[1] * 0.35f).coerceIn(0.1f, 0.45f)
            hsv[2] = if (isDark) 0.16f else 0.92f
            Color.HSVToColor(hsv)
        } else {
            try {
                if (isDark) {
                    context.getColor(com.android.internal.R.color.system_surface_container_high_dark)
                } else {
                    context.getColor(com.android.internal.R.color.system_surface_container_high_light)
                }
            } catch (e: Exception) {
                if (isDark) Color.parseColor("#2B2930") else Color.parseColor("#ECE6F0")
            }
        }

        // 2. Resolve button squircle fill color
        val buttonBgColor = if (accentColor != null) {
            val hsv = FloatArray(3)
            Color.colorToHSV(accentColor, hsv)
            hsv[1] = (hsv[1] * 0.45f).coerceIn(0.15f, 0.55f)
            hsv[2] = if (isDark) 0.26f else 0.84f
            Color.HSVToColor(hsv)
        } else {
            try {
                if (isDark) {
                    context.getColor(com.android.internal.R.color.system_surface_container_highest_dark)
                } else {
                    context.getColor(com.android.internal.R.color.system_surface_container_highest_light)
                }
            } catch (e: Exception) {
                if (isDark) Color.parseColor("#36343B") else Color.parseColor("#E6E0E9")
            }
        }

        val iconTintColor = try {
            if (isDark) {
                context.getColor(com.android.internal.R.color.system_on_surface_dark)
            } else {
                context.getColor(com.android.internal.R.color.system_on_surface_light)
            }
        } catch (e: Exception) {
            if (isDark) Color.parseColor("#E6E1E5") else Color.parseColor("#1D1B20")
        }
        mCurrentIconTintColor = iconTintColor

        val strokeColor = if (isDark) Color.argb(40, 255, 255, 255) else Color.argb(30, 0, 0, 0)

        // 3. Root container pill
        val root = mPopupView.findViewById<View>(R.id.chip_island_popup_root)
        val rootShape = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, 24f, res.displayMetrics
            )
            setColor(cardBgColor)
            setStroke(
                android.util.TypedValue.applyDimension(
                    android.util.TypedValue.COMPLEX_UNIT_DIP, 1f, res.displayMetrics
                ).toInt(),
                strokeColor
            )
        }
        root?.background = rootShape

        // 4. Squircle button backgrounds with ripple
        fun createButtonBg(radiusDp: Float): android.graphics.drawable.Drawable {
            val content = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = android.util.TypedValue.applyDimension(
                    android.util.TypedValue.COMPLEX_UNIT_DIP, radiusDp, res.displayMetrics
                )
                setColor(buttonBgColor)
            }
            val rippleColor = Color.argb(
                50,
                Color.red(iconTintColor),
                Color.green(iconTintColor),
                Color.blue(iconTintColor)
            )
            return RippleDrawable(
                android.content.res.ColorStateList.valueOf(rippleColor),
                content,
                null
            )
        }

        mBtnLeft.background = createButtonBg(14f)
        mBtnCenter.background = createButtonBg(16f)
        mBtnRight.background = createButtonBg(14f)

        // 5. Apply tints (unless custom overridden)
        mBtnLeft.setColorFilter(mCustomLeftTint ?: iconTintColor)
        mBtnCenter.setColorFilter(mCustomCenterTint ?: iconTintColor)
        mBtnRight.setColorFilter(mCustomRightTint ?: iconTintColor)
    }

    fun showBelow(anchorView: View? = null) {
        if (mIsAttached) {
            resetAutoDismissTimer()
            return
        }

        val statusBarHeight = try {
            context.resources.getDimensionPixelSize(
                com.android.internal.R.dimen.status_bar_height
            )
        } catch (e: Exception) {
            (28 * context.resources.displayMetrics.density).toInt()
        }

        val marginStart: Int
        val marginTop: Int

        if (anchorView != null && anchorView.isAttachedToWindow) {
            val location = IntArray(2)
            anchorView.getLocationOnScreen(location)
            marginStart = maxOf(0, location[0])
            marginTop = maxOf(statusBarHeight, location[1] + anchorView.height + (6 * context.resources.displayMetrics.density).toInt())
        } else {
            marginStart = (16 * context.resources.displayMetrics.density).toInt()
            marginTop = statusBarHeight + (6 * context.resources.displayMetrics.density).toInt()
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_STATUS_BAR_SUB_PANEL,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            title = popupTitle
            gravity = Gravity.TOP or Gravity.START
            x = marginStart
            y = marginTop
        }

        mLastShowTime = SystemClock.elapsedRealtime()

        mPopupView.alpha = 0f
        mPopupView.translationY = -15f
        mPopupView.scaleX = 0.85f
        mPopupView.scaleY = 0.85f

        try {
            mWindowManager.addView(mPopupView, params)
            mIsAttached = true
        } catch (e: Exception) {
            return
        }

        resetAutoDismissTimer()

        mPopupView.animate().cancel()
        mPopupView.animate()
            .setListener(null)
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(220)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    fun show() {
        showBelow(null)
    }

    fun dismissWithAnimation() {
        mHandler.removeCallbacks(mAutoDismissRunnable)
        if (!mIsAttached) return

        mPopupView.animate().cancel()
        mPopupView.animate()
            .alpha(0f)
            .translationY(-10f)
            .scaleX(0.85f)
            .scaleY(0.85f)
            .setDuration(180)
            .setInterpolator(DecelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    mPopupView.animate().setListener(null)
                    if (mIsAttached) {
                        try {
                            mWindowManager.removeViewImmediate(mPopupView)
                        } catch (ignored: Exception) {}
                        mIsAttached = false
                    }
                }
            })
            .start()
    }

    val isShowing: Boolean
        get() = mIsAttached
}
