package com.android.systemui.qs.ui.viewmodel

import com.android.compose.animation.scene.Back
import com.android.compose.animation.scene.Swipe
import com.android.compose.animation.scene.UserAction
import com.android.compose.animation.scene.UserActionResult
import com.android.compose.animation.scene.UserActionResult.HideOverlay
import com.android.compose.animation.scene.UserActionResult.ReplaceByOverlay
import com.android.compose.animation.scene.UserActionResult.ShowOverlay
import com.android.compose.animation.scene.UserActionResult.ShowOverlay.HideCurrentOverlays
import com.android.systemui.qs.panels.ui.viewmodel.EditModeViewModel
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.ui.viewmodel.SceneContainerArea.BottomEdge
import com.android.systemui.scene.ui.viewmodel.SceneContainerArea.TopEdgeEndHalf
import com.android.systemui.scene.ui.viewmodel.SceneContainerArea.TopEdgeStartHalf
import com.android.systemui.scene.ui.viewmodel.UserActionsViewModel
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/** Models the UI state for the user actions for navigating to other scenes or overlays. */
class QuickSettingsShadeOverlayActionsViewModel
@AssistedInject
constructor(private val editModeViewModel: EditModeViewModel) : UserActionsViewModel() {

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

        combine(editModeViewModel.isEditing, invertFlow) { isEditing, invertSides ->
            val hideQuickSettings = HideOverlay(Overlays.QuickSettingsShade)
            val topEdgeToNotifs = if (invertSides) TopEdgeEndHalf else TopEdgeStartHalf
            val swipeHorizontal = if (invertSides) Swipe.Left else Swipe.Right
            buildMap {
                if (isEditing) {
                    put(Swipe.Up(fromSource = BottomEdge), hideQuickSettings)
                } else {
                    put(Back, hideQuickSettings)
                    put(Swipe.Up, hideQuickSettings)
                }

                put(
                    Swipe.Down(fromSource = topEdgeToNotifs),
                    ShowOverlay(
                        Overlays.NotificationsShade,
                        hideCurrentOverlays =
                            HideCurrentOverlays.Some(Overlays.QuickSettingsShade),
                    ),
                )

                if (!isEditing) {
                    put(
                        swipeHorizontal, ReplaceByOverlay(Overlays.NotificationsShade)
                    )
                }
            }
        }.collect { actions -> setActions(actions) }
    }

    @AssistedFactory
    interface Factory {
        fun create(): QuickSettingsShadeOverlayActionsViewModel
    }
}
