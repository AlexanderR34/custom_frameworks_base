package com.android.systemui.notifications.ui.viewmodel

import com.android.compose.animation.scene.Back
import com.android.compose.animation.scene.Swipe
import com.android.compose.animation.scene.UserAction
import com.android.compose.animation.scene.UserActionResult
import com.android.compose.animation.scene.UserActionResult.HideOverlay
import com.android.compose.animation.scene.UserActionResult.ReplaceByOverlay
import com.android.compose.animation.scene.UserActionResult.ShowOverlay
import com.android.compose.animation.scene.UserActionResult.ShowOverlay.HideCurrentOverlays
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.ui.viewmodel.SceneContainerArea.TopEdgeEndHalf
import com.android.systemui.scene.ui.viewmodel.SceneContainerArea.TopEdgeStartHalf
import com.android.systemui.scene.ui.viewmodel.UserActionsViewModel
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf

/** Models the UI state for the user actions for navigating to other scenes or overlays. */
class NotificationsShadeOverlayActionsViewModel @AssistedInject constructor() :
    UserActionsViewModel() {

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

        invertFlow.collect { invertSides ->
            val swipeHorizontal = if (invertSides) Swipe.Right else Swipe.Left
            val topEdgeToQs = if (invertSides) TopEdgeStartHalf else TopEdgeEndHalf
            setActions(
                mapOf(
                    Swipe.Up to HideOverlay(Overlays.NotificationsShade),
                    Back to HideOverlay(Overlays.NotificationsShade),
                    Swipe.Down(fromSource = topEdgeToQs) to
                        ShowOverlay(
                            Overlays.QuickSettingsShade,
                            hideCurrentOverlays = HideCurrentOverlays.Some(Overlays.NotificationsShade),
                        ),
                    swipeHorizontal to ReplaceByOverlay(Overlays.QuickSettingsShade),
                )
            )
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(): NotificationsShadeOverlayActionsViewModel
    }
}
