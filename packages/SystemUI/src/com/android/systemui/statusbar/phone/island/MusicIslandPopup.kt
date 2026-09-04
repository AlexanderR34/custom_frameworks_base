package com.android.systemui.statusbar.phone.island

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
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

    init {
        mPopupView = LayoutInflater.from(context).inflate(R.layout.music_island_popup, null)
        mBtnPrev = mPopupView.findViewById(R.id.music_island_btn_prev)
        mBtnPlayPause = mPopupView.findViewById(R.id.music_island_btn_play_pause)
        mBtnNext = mPopupView.findViewById(R.id.music_island_btn_next)

        mPopupWindow = PopupWindow(
            mPopupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true // Focusable so it maintains window focus and handles touches cleanly
        ).apply {
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            isOutsideTouchable = true
            isTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = 24f
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

    fun setMonetColors(primaryContainer: Int, onPrimaryContainer: Int) {
        val root = mPopupView.findViewById<View>(R.id.music_island_popup_root)
        if (root?.background is GradientDrawable) {
            (root.background as GradientDrawable).setColor(primaryContainer)
        }

        mBtnPrev.setColorFilter(onPrimaryContainer)
        mBtnPlayPause.setColorFilter(onPrimaryContainer)
        mBtnNext.setColorFilter(onPrimaryContainer)
    }

    fun showBelow(anchorView: View) {
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
