package com.android.systemui.statusbar.phone.island

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.PopupWindow
import com.android.systemui.res.R

/**
 * Floating Quick Media Controls Popup for Status Bar Music Island
 */
class MusicIslandPopup(
    private val context: Context,
    private val onAction: (Action) -> Unit
) {

    enum class Action {
        PREVIOUS,
        TOGGLE_PLAY_PAUSE,
        NEXT
    }

    companion object {
        private const val AUTO_DISMISS_DELAY_MS = 6000L
        private const val TOUCH_OUTSIDE_GUARD_TIME_MS = 400L
    }

    private val mPopupView: View
    private val mPopupWindow: PopupWindow
    private val mBtnPrev: ImageView
    private val mBtnPlayPause: ImageView
    private val mBtnNext: ImageView
    private val mHandler = Handler(Looper.getMainLooper())

    private val mAutoDismissRunnable = Runnable {
        dismissWithAnimation()
    }

    private var mIsPlaying = true
    private var mLastShowTime: Long = 0L

    init {
        mPopupView = LayoutInflater.from(context).inflate(R.layout.music_island_popup, null)
        mBtnPrev = mPopupView.findViewById(R.id.music_island_btn_prev)
        mBtnPlayPause = mPopupView.findViewById(R.id.music_island_btn_play_pause)
        mBtnNext = mPopupView.findViewById(R.id.music_island_btn_next)

        mPopupWindow = PopupWindow(
            mPopupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            false // Non-focusable to avoid stealing status bar / system focus
        ).apply {
            windowLayoutType = WindowManager.LayoutParams.TYPE_STATUS_BAR_SUB_PANEL
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            isOutsideTouchable = true
            isTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = 24f

            setTouchInterceptor { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    if (SystemClock.elapsedRealtime() - mLastShowTime > TOUCH_OUTSIDE_GUARD_TIME_MS) {
                        dismissWithAnimation()
                    }
                    true
                } else {
                    false
                }
            }

            setOnDismissListener {
                mHandler.removeCallbacks(mAutoDismissRunnable)
            }
        }

        setupButtons()
    }

    private fun setupButtons() {
        mPopupView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                resetAutoDismissTimer()
            }
            false
        }

        mBtnPrev.setOnClickListener {
            resetAutoDismissTimer()
            onAction(Action.PREVIOUS)
        }
        mBtnPlayPause.setOnClickListener {
            resetAutoDismissTimer()
            onAction(Action.TOGGLE_PLAY_PAUSE)
        }
        mBtnNext.setOnClickListener {
            resetAutoDismissTimer()
            onAction(Action.NEXT)
        }
    }

    private fun resetAutoDismissTimer() {
        mHandler.removeCallbacks(mAutoDismissRunnable)
        mHandler.postDelayed(mAutoDismissRunnable, AUTO_DISMISS_DELAY_MS)
    }

    fun setPlayingState(isPlaying: Boolean) {
        mIsPlaying = isPlaying
        mBtnPlayPause.setImageResource(
            if (isPlaying) R.drawable.ic_music_island_pause else R.drawable.ic_music_island_play
        )
    }

    fun applyMonetTheme(artworkColor: Int? = null) {
        val res = context.resources
        val isDark = (res.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        // 1. Resolve Monet Dynamic Colors
        val cardBgColor = try {
            if (isDark) {
                context.getColor(com.android.internal.R.color.system_surface_container_high_dark)
            } else {
                context.getColor(com.android.internal.R.color.system_surface_container_high_light)
            }
        } catch (e: Exception) {
            if (isDark) Color.parseColor("#2B2930") else Color.parseColor("#ECE6F0")
        }

        val buttonBgColor = try {
            if (isDark) {
                context.getColor(com.android.internal.R.color.system_surface_container_highest_dark)
            } else {
                context.getColor(com.android.internal.R.color.system_surface_container_highest_light)
            }
        } catch (e: Exception) {
            if (isDark) Color.parseColor("#36343B") else Color.parseColor("#E6E0E9")
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

        val strokeColor = if (isDark) Color.argb(40, 255, 255, 255) else Color.argb(30, 0, 0, 0)

        // 2. Apply dynamic background to root popup container
        val root = mPopupView.findViewById<View>(R.id.music_island_popup_root)
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

        // 3. Apply squircle dynamic backgrounds to buttons
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
            return android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(rippleColor),
                content,
                null
            )
        }

        mBtnPrev.background = createButtonBg(14f)
        mBtnPlayPause.background = createButtonBg(16f)
        mBtnNext.background = createButtonBg(14f)

        // 4. Apply icon tints
        mBtnPrev.setColorFilter(iconTintColor)
        mBtnPlayPause.setColorFilter(iconTintColor)
        mBtnNext.setColorFilter(iconTintColor)
    }

    fun setMonetColors(primaryContainer: Int, onPrimaryContainer: Int) {
        applyMonetTheme()
    }

    fun showBelow(anchorView: View) {
        applyMonetTheme()
        if (mPopupWindow.isShowing) {
            resetAutoDismissTimer()
            return
        }

        mPopupView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )

        val location = IntArray(2)
        anchorView.getLocationOnScreen(location)

        val x = location[0]
        val y = location[1] + anchorView.height + 8

        mLastShowTime = SystemClock.elapsedRealtime()

        mPopupView.alpha = 0f
        mPopupView.translationY = -15f
        mPopupView.scaleX = 0.85f
        mPopupView.scaleY = 0.85f

        try {
            mPopupWindow.showAtLocation(anchorView, android.view.Gravity.NO_GRAVITY, x, y)
        } catch (e: Exception) {
            return
        }

        resetAutoDismissTimer()

        mPopupView.animate().cancel()
        mPopupView.animate()
            .setListener(null) // Clear any previous dismiss animator listener!
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(220)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    fun dismissWithAnimation() {
        mHandler.removeCallbacks(mAutoDismissRunnable)
        if (!mPopupWindow.isShowing) return

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
                    try {
                        mPopupWindow.dismiss()
                    } catch (ignored: Exception) {}
                }
            })
            .start()
    }

    val isShowing: Boolean
        get() = mPopupWindow.isShowing
}
