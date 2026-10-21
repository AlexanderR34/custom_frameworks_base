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

import androidx.annotation.VisibleForTesting
import com.android.systemui.statusbar.notification.domain.interactor.ActiveNotificationsInteractor
import kotlinx.coroutines.flow.Flow

class GoneUserActionsViewModel private constructor(
    private val shadeModeInteractor: ShadeModeInteractor,
    @ShadeDisplayAware private val resources: Resources,
    private val activeNotificationsInteractor: ActiveNotificationsInteractor?,
    @Suppress("UNUSED_PARAMETER") marker: Unit,
) : UserActionsViewModel() {

    @AssistedInject
    constructor(
        shadeModeInteractor: ShadeModeInteractor,
        @ShadeDisplayAware resources: Resources,
        activeNotificationsInteractor: ActiveNotificationsInteractor,
    ) : this(shadeModeInteractor, resources, activeNotificationsInteractor, Unit)

    @VisibleForTesting
    constructor(
        shadeModeInteractor: ShadeModeInteractor,
        @ShadeDisplayAware resources: Resources,
    ) : this(shadeModeInteractor, resources, null, Unit)

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

        val smartPullDownFlow = if (cr != null) {
            callbackFlow {
                val uri = android.provider.Settings.System.getUriFor("dual_shade_smart_pull_down")
                val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        trySend(android.provider.Settings.System.getInt(cr, "dual_shade_smart_pull_down", 0) == 1)
                    }
                }
                cr.registerContentObserver(uri, false, observer)
                trySend(android.provider.Settings.System.getInt(cr, "dual_shade_smart_pull_down", 0) == 1)
                awaitClose { cr.unregisterContentObserver(observer) }
            }
        } else {
            flowOf(false)
        }

        val notifFlow: Flow<Boolean> = activeNotificationsInteractor?.areAnyNotificationsPresent ?: flowOf(true)

        combine(
            shadeModeInteractor.shadeMode,
            invertFlow,
            smartPullDownFlow,
            notifFlow,
        ) { shadeMode, invertSides, smartPullDown, hasNotifications ->
            when (shadeMode) {
                ShadeMode.Single ->
                    singleShadeActions(
                        requireTwoPointersForTopEdgeForQs = true,
                        smartPullDown = smartPullDown,
                        hasNotifications = hasNotifications,
                    )
                ShadeMode.Split -> splitShadeActions()
                ShadeMode.Dual ->
                    dualShadeActions(
                        twoFingerSwipeEnabled = twoFingerSwipeEnabled,
                        invertSides = invertSides,
                        smartPullDown = smartPullDown,
                        hasNotifications = hasNotifications,
                    )
            }.associate { it }
        }.collect { setActions(it) }
    }

    @AssistedFactory
    interface Factory {
        fun create(): GoneUserActionsViewModel
    }
}
