package com.android.systemui.statusbar.phone.island

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import com.android.systemui.R

/**
 * Status Bar Music Island View - Circular Artwork with Dynamic Progress Ring
 */
class MusicIslandView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val mImageView = ImageView(context)
    private val mArcRect = RectF()
    private val mStrokeWidth = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 2.5f, resources.displayMetrics
    )
    private val mRingPadding = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 1.5f, resources.displayMetrics
    )

    private val mProgressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = mStrokeWidth
        color = Color.WHITE
    }

    private val mBackgroundRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = mStrokeWidth
        color = Color.argb(40, 255, 255, 255)
    }

    private var mProgress: Float = 0f
    private var mAnimatedProgress: Float = 0f
    private var mProgressAnimator: ValueAnimator? = null
    private var mIsIslandVisible = false

    var onIslandClicked: ((View) -> Unit)? = null

    init {
        setWillNotDraw(false)
        val size = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 26f, resources.displayMetrics
        ).toInt()

        val innerPadding = (mStrokeWidth + mRingPadding).toInt()
        val lp = LayoutParams(size - innerPadding * 2, size - innerPadding * 2).apply {
            gravity = Gravity.CENTER
        }

        mImageView.layoutParams = lp
        mImageView.scaleType = ImageView.ScaleType.CENTER_CROP
        mImageView.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
        mImageView.clipToOutline = true
        addView(mImageView)

        setOnClickListener {
            onIslandClicked?.invoke(this)
        }

        // Initially hidden
        visibility = GONE
        alpha = 0f
        scaleX = 0.7f
        scaleY = 0.7f
    }

    fun setArtwork(bitmap: Bitmap?) {
        if (bitmap != null) {
            mImageView.setImageBitmap(bitmap)
        } else {
            mImageView.setImageResource(R.drawable.ic_music_island_play)
        }
        invalidate()
    }

    fun setProgress(progressRatio: Float) {
        val targetProgress = progressRatio.coerceIn(0f, 1f)
        if (Math.abs(targetProgress - mProgress) > 0.005f) {
            mProgress = targetProgress
            mProgressAnimator?.cancel()
            mProgressAnimator = ValueAnimator.ofFloat(mAnimatedProgress, targetProgress).apply {
                duration = 300
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    mAnimatedProgress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
    }

    fun setRingColor(color: Int) {
        mProgressPaint.color = color
        mBackgroundRingPaint.color = Color.argb(45, Color.red(color), Color.green(color), Color.blue(color))
        invalidate()
    }

    fun showIsland() {
        if (mIsIslandVisible && visibility == VISIBLE) return
        mIsIslandVisible = true
        visibility = VISIBLE
        animate().cancel()
        animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(280)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    fun hideIsland() {
        if (!mIsIslandVisible && visibility == GONE) return
        mIsIslandVisible = false
        animate().cancel()
        animate()
            .alpha(0f)
            .scaleX(0.7f)
            .scaleY(0.7f)
            .setDuration(250)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                if (!mIsIslandVisible) {
                    visibility = GONE
                }
            }
            .start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val halfStroke = mStrokeWidth / 2f + mRingPadding
        mArcRect.set(halfStroke, halfStroke, width - halfStroke, height - halfStroke)

        // Draw subtle background track ring
        canvas.drawOval(mArcRect, mBackgroundRingPaint)

        // Draw animated progress arc
        if (mAnimatedProgress > 0f) {
            val sweepAngle = 360f * mAnimatedProgress
            canvas.drawArc(mArcRect, -90f, sweepAngle, false, mProgressPaint)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 28f, resources.displayMetrics
        ).toInt()
        val spec = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)
        super.onMeasure(spec, spec)
    }
}
