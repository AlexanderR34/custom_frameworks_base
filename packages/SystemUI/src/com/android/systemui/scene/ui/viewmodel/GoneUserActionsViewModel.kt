package com.android.systemui.scene.ui.viewmodel

import android.content.res.Resources
import com.android.compose.animation.scene.UserAction
import com.android.compose.animation.scene.UserActionResult
import com.android.systemui.res.R
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.shade.domain.interactor.ShadeModeInteractor
import com.android.systemui.shade.shared.model.ShadeMode
import com.android.systemui.shade.ui.viewmodel.dualShadeActions
import com.android.systemui.shade.ui.viewmodel.singleShadeActions
import com.android.systemui.shade.ui.viewmodel.splitShadeActions
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

class GoneUserActionsViewModel
@AssistedInject
constructor(
    private val shadeModeInteractor: ShadeModeInteractor,
    @ShadeDisplayAware private val resources: Resources,
) : UserActionsViewModel() {

    override suspend fun hydrateActions(setActions: (Map<UserAction, UserActionResult>) -> Unit) {
        val twoFingerSwipeEnabled =
            resources.getBoolean(R.bool.config_enableTwoFingerSwipeDownShade)
        val cr = android.app.ActivityThread.currentApplication()?.contentResolver
        val invertFlow = if (cr != null) {
            callbackFlow {
                val uri = android.provider.Settings.System.getUriFor("dual_shade_invert_sides")
                val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        trySend(android.provider.Settings.System.getInt(cr, "dual_shade_invert_sides", 0) == 1)
                    }
                }
                cr.registerContentObserver(uri, false, observer)
                trySend(android.provider.Settings.System.getInt(cr, "dual_shade_invert_sides", 0) == 1)
                awaitClose { cr.unregisterContentObserver(observer) }
            }
        } else {
            flowOf(false)
        }

        combine(shadeModeInteractor.shadeMode, invertFlow) { shadeMode, invertSides ->
            when (shadeMode) {
                ShadeMode.Single -> singleShadeActions(requireTwoPointersForTopEdgeForQs = true)
                ShadeMode.Split -> splitShadeActions()
                ShadeMode.Dual ->
                    dualShadeActions(
                        twoFingerSwipeEnabled = twoFingerSwipeEnabled,
                        invertSides = invertSides,
                    )
            }.associate { it }
        }.collect { setActions(it) }
    }

    @AssistedFactory
    interface Factory {
        fun create(): GoneUserActionsViewModel
    }
}
