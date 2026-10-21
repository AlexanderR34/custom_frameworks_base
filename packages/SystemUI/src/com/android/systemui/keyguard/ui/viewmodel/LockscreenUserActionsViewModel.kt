package com.android.systemui.keyguard.ui.viewmodel

import com.android.compose.animation.scene.Swipe
import com.android.compose.animation.scene.UserAction
import com.android.compose.animation.scene.UserActionResult
import com.android.systemui.communal.domain.interactor.CommunalInteractor
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryInteractor
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.scene.ui.viewmodel.UserActionsViewModel
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import com.android.systemui.shade.domain.interactor.ShadeModeInteractor
import com.android.systemui.shade.shared.model.ShadeMode
import com.android.systemui.shade.ui.viewmodel.dualShadeActions
import com.android.systemui.shade.ui.viewmodel.singleShadeActions
import com.android.systemui.shade.ui.viewmodel.splitShadeActions
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

import androidx.annotation.VisibleForTesting
import com.android.systemui.statusbar.notification.domain.interactor.ActiveNotificationsInteractor
import kotlinx.coroutines.flow.Flow

/** Models UI state and handles user input for the lockscreen scene. */
@OptIn(ExperimentalCoroutinesApi::class)
class LockscreenUserActionsViewModel private constructor(
    private val deviceEntryInteractor: DeviceEntryInteractor,
    private val communalInteractor: CommunalInteractor,
    private val shadeInteractor: ShadeInteractor,
    private val shadeModeInteractor: ShadeModeInteractor,
    private val activeNotificationsInteractor: ActiveNotificationsInteractor?,
    @Suppress("UNUSED_PARAMETER") marker: Unit,
) : UserActionsViewModel() {

    @AssistedInject
    constructor(
        deviceEntryInteractor: DeviceEntryInteractor,
        communalInteractor: CommunalInteractor,
        shadeInteractor: ShadeInteractor,
        shadeModeInteractor: ShadeModeInteractor,
        activeNotificationsInteractor: ActiveNotificationsInteractor,
    ) : this(
        deviceEntryInteractor,
        communalInteractor,
        shadeInteractor,
        shadeModeInteractor,
        activeNotificationsInteractor,
        Unit,
    )

    @VisibleForTesting
    constructor(
        deviceEntryInteractor: DeviceEntryInteractor,
        communalInteractor: CommunalInteractor,
        shadeInteractor: ShadeInteractor,
        shadeModeInteractor: ShadeModeInteractor,
    ) : this(deviceEntryInteractor, communalInteractor, shadeInteractor, shadeModeInteractor, null, Unit)

    override suspend fun hydrateActions(setActions: (Map<UserAction, UserActionResult>) -> Unit) {
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

        val dualShadeConfigFlow = combine(invertFlow, smartPullDownFlow, notifFlow) { invert, smart, notif ->
            Triple(invert, smart, notif)
        }

        shadeInteractor.isShadeTouchable
            .flatMapLatest { isShadeTouchable ->
                if (!isShadeTouchable) {
                    return@flatMapLatest flowOf(emptyMap())
                }

                combine(
                    deviceEntryInteractor.isUnlocked,
                    communalInteractor.isCommunalAvailable,
                    shadeModeInteractor.shadeMode,
                    dualShadeConfigFlow,
                ) { isDeviceUnlocked, isCommunalAvailable, shadeMode, config ->
                    val (invertSides, smartPullDown, hasNotifications) = config
                    buildList {
                            if (isCommunalAvailable) {
                                add(Swipe.Start to Scenes.Communal)
                            }

                            if (isDeviceUnlocked) {
                                add(Swipe.Up to Scenes.Gone)
                            } else {
                                add(Swipe.Up to Overlays.Bouncer)
                            }

                            addAll(
                                when (shadeMode) {
                                    ShadeMode.Single ->
                                        singleShadeActions(
                                            smartPullDown = smartPullDown,
                                            hasNotifications = hasNotifications,
                                        )
                                    ShadeMode.Split -> splitShadeActions()
                                    ShadeMode.Dual ->
                                        dualShadeActions(
                                            invertSides = invertSides,
                                            smartPullDown = smartPullDown,
                                            hasNotifications = hasNotifications,
                                        )
                                }
                            )
                        }
                        .associate { it }
                }
            }
            .collect { setActions(it) }
    }

    @AssistedFactory
    interface Factory {
        fun create(): LockscreenUserActionsViewModel
    }
}
