package com.android.systemui.statusbar.phone.island

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.PopupWindow
import com.android.systemui.R

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

    private val mPopupView: View
    private val mPopupWindow: PopupWindow
    private val mBtnPrev: ImageView
    private val mBtnPlayPause: ImageView
    private val mBtnNext: ImageView

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
            false // Non-focusable so it doesn't block background touches
        ).apply {
            isOutsideTouchable = true
            isTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = 20f
        }

        setupButtons()
    }

    private fun setupButtons() {
        mBtnPrev.setOnClickListener {
            onAction(Action.PREVIOUS)
        }
        mBtnPlayPause.setOnClickListener {
            onAction(Action.TOGGLE_PLAY_PAUSE)
        }
        mBtnNext.setOnClickListener {
            onAction(Action.NEXT)
        }

        // Auto dismiss when touching outside the popup content
        mPopupWindow.setTouchInterceptor { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_OUTSIDE) {
                dismissWithAnimation()
                return@setTouchInterceptor false
            }
            false
        }
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

        mPopupWindow.showAtLocation(anchorView, android.view.Gravity.NO_GRAVITY, x, y)

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
