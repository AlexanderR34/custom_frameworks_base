/*
 * Copyright (C) 2024 The Android Open Source Project
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
 */

package com.android.systemui.volume.dialog

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentDialog
import com.android.app.tracing.coroutines.coroutineScopeTraced
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.lifecycle.repeatWhenAttached
import com.android.systemui.res.R
import com.android.systemui.volume.Events
import com.android.systemui.volume.dialog.dagger.factory.VolumeDialogComponentFactory
import com.android.systemui.volume.dialog.domain.interactor.VolumeDialogVisibilityInteractor
import com.android.systemui.statusbar.BlurUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.awaitCancellation

class VolumeDialog
@AssistedInject
constructor(
    @Application context: Context,
    private val componentFactory: VolumeDialogComponentFactory,
    private val visibilityInteractor: VolumeDialogVisibilityInteractor,
    private val blurUtils: BlurUtils,
    @Assisted private val isVolumeDialogVertical: Boolean,
) : ComponentDialog(context, R.style.Theme_SystemUI_Dialog_Volume) {

    @AssistedFactory
    interface Factory {
        fun create(isVolumeDialogVertical: Boolean): VolumeDialog
    }

    init {
        with(window!!) {
            addFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            )
            val isBlurDisabled = android.os.SystemProperties.getBoolean("persist.sysui.disableBlur", false) ||
                android.os.SystemProperties.getInt("persist.sys.custom_blur_intensity", 50) <= 0
            if (!isBlurDisabled) {
                addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            }
            addPrivateFlags(WindowManager.LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY)
            setType(WindowManager.LayoutParams.TYPE_VOLUME_OVERLAY)
            setWindowAnimations(-1)

            attributes =
                attributes.apply {
                    title = "VolumeDialog" // Not the same as Window#setTitle
                    if (!isBlurDisabled) {
                        val intensity = android.os.SystemProperties.getInt("persist.sys.custom_blur_intensity", 50)
                        val blurRadius = (80 * (intensity / 50f)).toInt().coerceIn(20, 150)
                        setBlurBehindRadius(blurRadius)
                    }
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    fitInsetsTypes = 0
                }
            if (isVolumeDialogVertical) {
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setGravity(Gravity.FILL)
            } else {
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setGravity(Gravity.TOP or Gravity.END)
            }
        }
        setCancelable(false)
        setCanceledOnTouchOutside(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isVolumeDialogVertical) {
            setContentView(R.layout.volume_dialog)
        } else {
            setContentView(R.layout.volume_dialog_horizontal)
        }
        requireViewById<View>(R.id.volume_dialog).repeatWhenAttached {
            coroutineScopeTraced("[Volume]dialog") {
                val component = componentFactory.create(this)
                with(component.volumeDialogViewBinder()) {
                    bind(this@VolumeDialog, isVolumeDialogVertical)
                }

                awaitCancellation()
            }
        }
    }

    /**
     * NOTE: This will be called with ACTION_OUTSIDE MotionEvents for touches that occur outside of
     * the touchable region of the volume dialog (as returned by [.onComputeInternalInsets]) even if
     * those touches occurred within the bounds of the volume dialog.
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isShowing) {
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                visibilityInteractor.dismissDialog(Events.DISMISS_REASON_TOUCH_OUTSIDE)
                return true
            }
        }
        return false
    }
}
