/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.android.systemui.statusbar.notification.stack.ui.view

import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import androidx.constraintlayout.core.widgets.Optimizer
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.constraintlayout.widget.ConstraintSet.BOTTOM
import androidx.constraintlayout.widget.ConstraintSet.END
import androidx.constraintlayout.widget.ConstraintSet.PARENT_ID
import androidx.constraintlayout.widget.ConstraintSet.START
import androidx.constraintlayout.widget.ConstraintSet.TOP
import androidx.constraintlayout.widget.ConstraintSet.VERTICAL
import com.android.systemui.res.R
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.SharedNotificationContainerViewModel.HorizontalPosition
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.SharedNotificationContainerViewModel.HorizontalPosition.Centered
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.SharedNotificationContainerViewModel.HorizontalPosition.EdgeToMiddle
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.SharedNotificationContainerViewModel.HorizontalPosition.MiddleToEdge

/**
 * Container for the stack scroller, so that the bounds can be externally specified, such as from
 * the keyguard or shade scenes.
 */
class SharedNotificationContainer(context: Context, attrs: AttributeSet?) :
    ConstraintLayout(context, attrs) {

    private val baseConstraintSet = ConstraintSet()
    private var floatingClearAllButton: View? = null
    private var lastHorizontalPosition: HorizontalPosition = HorizontalPosition.Centered(0)
    private var lastMarginStart: Int = 0
    private var lastMarginTop: Int = 0
    private var lastMarginEnd: Int = 0
    private var lastMarginBottom: Int = 0
    private var lastNsslAlpha: Float = 1f

    init {
        clipChildren = false
        clipToPadding = false
        optimizationLevel = optimizationLevel or Optimizer.OPTIMIZATION_GRAPH
        baseConstraintSet.apply {
            create(R.id.nssl_guideline, VERTICAL)
            setGuidelinePercent(R.id.nssl_guideline, 0.5f)
        }
        baseConstraintSet.applyTo(this)
    }

    fun addNotificationStackScrollLayout(nssl: View) {
        clipChildren = false
        clipToPadding = false
        (nssl as? ViewGroup)?.clipChildren = false
        (nssl as? ViewGroup)?.clipToPadding = false
        addView(nssl)
    }

    fun setFloatingClearAllButton(button: View?) {
        if (floatingClearAllButton === button) return
        val oldButton = floatingClearAllButton
        floatingClearAllButton = button
        if (oldButton != null) {
            removeView(oldButton)
        }
        if (button != null) {
            (button.parent as? ViewGroup)?.removeView(button)
            if (button.id == View.NO_ID) {
                button.id = R.id.hyperos_clear_all_button
            }
            button.translationZ = 100f
            button.elevation = 30f
            addView(button)
        }
        updateConstraints(
            lastHorizontalPosition,
            lastMarginStart,
            lastMarginTop,
            lastMarginEnd,
            lastMarginBottom,
            lastNsslAlpha,
        )
    }

    fun updateConstraints(
        horizontalPosition: HorizontalPosition,
        marginStart: Int,
        marginTop: Int,
        marginEnd: Int,
        marginBottom: Int,
        nsslAlpha: Float,
    ) {
        lastHorizontalPosition = horizontalPosition
        lastMarginStart = marginStart
        lastMarginTop = marginTop
        lastMarginEnd = marginEnd
        lastMarginBottom = marginBottom
        lastNsslAlpha = nsslAlpha

        val constraintSet = ConstraintSet()
        constraintSet.clone(baseConstraintSet)

        val startConstraintId =
            if (horizontalPosition is MiddleToEdge) R.id.nssl_guideline else PARENT_ID

        val endConstraintId =
            if (SceneContainerFlag.isEnabled && horizontalPosition is EdgeToMiddle) {
                R.id.nssl_guideline
            } else {
                PARENT_ID
            }

        val nsslId = R.id.notification_stack_scroller
        constraintSet.apply {
            if (SceneContainerFlag.isEnabled) {
                when (horizontalPosition) {
                    is EdgeToMiddle -> {
                        setGuidelinePercent(R.id.nssl_guideline, /* ratio= */ 0.5f)
                        constrainMaxWidth(nsslId, horizontalPosition.maxWidth)
                        // Ensure START alignment in case the maxWidth is smaller than half the
                        // parent width.
                        constraintSet.setHorizontalBias(nsslId, /* bias= */ 0f)
                    }
                    is MiddleToEdge -> {
                        setGuidelinePercent(R.id.nssl_guideline, /* ratio= */ 0.5f)
                        constrainMaxWidth(nsslId, horizontalPosition.maxWidth)
                        // Ensure END alignment in case the maxWidth is smaller than half the
                        // parent width.
                        constraintSet.setHorizontalBias(nsslId, /* bias= */ 1f)
                    }
                    is Centered -> {
                        constrainMaxWidth(nsslId, horizontalPosition.maxWidth)
                        constraintSet.setHorizontalBias(nsslId, /* bias= */ 0.5f)
                    }
                    else -> Unit
                }
            }

            // Constraint layout sets the alpha to 1 if it's not set explicitly in the constraint
            // set. Let's keep the current nssl alpha instead, otherwise this might interfere with
            // animations.
            setAlpha(nsslId, nsslAlpha)
            connect(nsslId, START, startConstraintId, START, marginStart)
            connect(nsslId, END, endConstraintId, END, marginEnd)
            connect(nsslId, BOTTOM, PARENT_ID, BOTTOM, marginBottom)
            connect(nsslId, TOP, PARENT_ID, TOP, marginTop)

            floatingClearAllButton?.let { btn ->
                val btnId = btn.id
                val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                val density = resources.displayMetrics.density
                val insets = rootWindowInsets
                val navBarBottom = insets?.getInsets(WindowInsets.Type.navigationBars())?.bottom ?: 0
                val navBarEnd = insets?.getInsets(WindowInsets.Type.navigationBars())?.right ?: 0
                val navBarResId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
                val fallbackNavBarBottom = if (navBarResId > 0) resources.getDimensionPixelSize(navBarResId) else 0
                val finalNavBarBottom = if (navBarBottom > 0) navBarBottom else fallbackNavBarBottom

                val navBarLandResId = resources.getIdentifier("navigation_bar_height_landscape", "dimen", "android")
                val fallbackNavBarEnd = if (navBarLandResId > 0) resources.getDimensionPixelSize(navBarLandResId) else fallbackNavBarBottom
                val finalNavBarEnd = if (navBarEnd > 0) navBarEnd else fallbackNavBarEnd

                constrainWidth(btnId, (54 * density).toInt())
                constrainHeight(btnId, (54 * density).toInt())
                if (isLandscape) {
                    val margin = finalNavBarEnd + (36 * density).toInt()
                    connect(btnId, END, PARENT_ID, END, margin)
                    connect(btnId, TOP, PARENT_ID, TOP, 0)
                    connect(btnId, BOTTOM, PARENT_ID, BOTTOM, 0)
                } else {
                    val margin = finalNavBarBottom + (28 * density).toInt()
                    connect(btnId, START, PARENT_ID, START, 0)
                    connect(btnId, END, PARENT_ID, END, 0)
                    connect(btnId, BOTTOM, PARENT_ID, BOTTOM, margin)
                }
            }
        }
        constraintSet.applyTo(this)
    }
}
