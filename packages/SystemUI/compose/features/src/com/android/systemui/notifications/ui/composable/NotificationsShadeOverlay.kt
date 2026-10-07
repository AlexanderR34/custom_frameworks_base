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

package com.android.systemui.notifications.ui.composable

import android.content.Context
import android.content.res.Configuration
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.UserAction
import com.android.compose.animation.scene.UserActionResult
import com.android.compose.lifecycle.DisposableEffectWithLifecycle
import com.android.compose.lifecycle.LaunchedEffectWithLifecycle
import com.android.internal.jank.InteractionJankMonitor
import com.android.systemui.animation.Expandable
import com.android.systemui.common.shared.model.asImageBitmap
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.keyguard.ui.composable.elements.LockscreenElements
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.media.remedia.ui.compose.Media
import com.android.systemui.media.remedia.ui.compose.MediaPresentationStyle
import com.android.systemui.media.remedia.ui.viewmodel.MediaCarouselVisibility
import com.android.systemui.media.remedia.ui.viewmodel.MediaNavigationViewModel
import com.android.systemui.media.remedia.ui.viewmodel.MediaSecondaryActionViewModel
import com.android.systemui.notifications.intelligence.rules.shared.NmContextualDisplayLaunch
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.NotificationRulesParentViewModel
import com.android.systemui.notifications.ui.viewmodel.NotificationsShadeOverlayActionsViewModel
import com.android.systemui.notifications.ui.viewmodel.NotificationsShadeOverlayContentViewModel
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys
import com.android.systemui.res.R
import com.android.systemui.scene.session.ui.composable.SaveableSession
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.ui.composable.LocalSceneContainerPreloadedResources
import com.android.systemui.scene.ui.composable.Overlay
import com.android.systemui.shade.ui.composable.ChipHighlightModel
import com.android.systemui.shade.ui.composable.OverlayShade
import com.android.systemui.shade.ui.composable.OverlayShadeHeader
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout
import com.android.systemui.statusbar.notification.stack.ui.view.NotificationScrollView
import dagger.Lazy
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

object HyperOSMediaHelper {
    fun getActiveController(context: Context, cardPackage: String? = null): MediaController? {
        val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        val controllers = try {
            msm?.getActiveSessions(null) ?: emptyList()
        } catch (e: Exception) {
            try {
                msm?.getActiveSessionsForUser(null, android.os.Process.myUserHandle()) ?: emptyList()
            } catch (e2: Exception) {
                emptyList()
            }
        }
        if (!cardPackage.isNullOrBlank()) {
            val matching = controllers.firstOrNull { it.packageName == cardPackage }
            if (matching != null) return matching
        }
        return controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull()
    }

    fun playPause(context: Context, cardPackage: String? = null, fallbackOnClick: (() -> Unit)? = null) {
        if (fallbackOnClick != null) {
            try {
                fallbackOnClick.invoke()
                return
            } catch (e: Exception) {}
        }
        val controller = getActiveController(context, cardPackage)
        if (controller != null) {
            val state = controller.playbackState?.state
            if (state == PlaybackState.STATE_PLAYING) {
                try {
                    controller.transportControls.pause()
                    return
                } catch (e: Exception) {}
            } else {
                try {
                    controller.transportControls.play()
                    return
                } catch (e: Exception) {}
            }
        }
        dispatchMediaKey(context, controller, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    }

    fun skipNext(context: Context, cardPackage: String? = null, fallbackOnClick: (() -> Unit)? = null) {
        if (fallbackOnClick != null) {
            try {
                fallbackOnClick.invoke()
                return
            } catch (e: Exception) {}
        }
        val controller = getActiveController(context, cardPackage)
        if (controller != null) {
            try {
                controller.transportControls.skipToNext()
                return
            } catch (e: Exception) {}
        }
        dispatchMediaKey(context, controller, KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    fun skipPrev(context: Context, cardPackage: String? = null, fallbackOnClick: (() -> Unit)? = null) {
        if (fallbackOnClick != null) {
            try {
                fallbackOnClick.invoke()
                return
            } catch (e: Exception) {}
        }
        val controller = getActiveController(context, cardPackage)
        if (controller != null) {
            try {
                controller.transportControls.skipToPrevious()
                return
            } catch (e: Exception) {}
        }
        dispatchMediaKey(context, controller, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    fun seekTo(context: Context, positionMs: Long, cardPackage: String? = null) {
        val controller = getActiveController(context, cardPackage)
        try {
            controller?.transportControls?.seekTo(positionMs)
        } catch (e: Exception) {}
    }

    private fun dispatchMediaKey(context: Context, controller: MediaController?, keyCode: Int) {
        if (controller != null) {
            try {
                val down = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
                val up = KeyEvent(KeyEvent.ACTION_UP, keyCode)
                controller.dispatchMediaButtonEvent(down)
                controller.dispatchMediaButtonEvent(up)
                return
            } catch (e: Exception) {}
        }
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val down = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            val up = KeyEvent(KeyEvent.ACTION_UP, keyCode)
            audioManager?.dispatchMediaKeyEvent(down)
            audioManager?.dispatchMediaKeyEvent(up)
        } catch (e: Exception) {}
    }
}

@SysUISingleton
class NotificationsShadeOverlay
@Inject
constructor(
    private val actionsViewModelFactory: NotificationsShadeOverlayActionsViewModel.Factory,
    private val contentViewModelFactory: NotificationsShadeOverlayContentViewModel.Factory,
    private val notificationRulesParentViewModelFactory: NotificationRulesParentViewModel.Factory,
    private val lockscreenElements: LockscreenElements,
    private val shadeSession: SaveableSession,
    private val stackScrollView: Lazy<NotificationScrollView>,
    private val jankMonitor: InteractionJankMonitor,
) : Overlay {
    override val key = Overlays.NotificationsShade

    private val actionsViewModel: NotificationsShadeOverlayActionsViewModel by lazy {
        actionsViewModelFactory.create()
    }

    override val userActions: Flow<Map<UserAction, UserActionResult>> = actionsViewModel.actions

    override val alwaysCompose: Boolean = false

    override suspend fun activate(): Nothing {
        actionsViewModel.activate()
    }

    @Composable
    override fun ContentScope.Content(modifier: Modifier) {
        val context = LocalContext.current
        val isDualShade = remember(context) {
            android.provider.Settings.Secure.getInt(
                context.contentResolver,
                android.provider.Settings.Secure.DUAL_SHADE,
                1
            ) == 1
        }
        val controlCenterStyle = remember(context) {
            android.provider.Settings.System.getInt(
                context.contentResolver,
                "control_center_style",
                1
            )
        }
        val isHyperOS = isDualShade && controlCenterStyle == 1

        val notificationStackPadding =
            dimensionResource(id = R.dimen.notification_side_paddings_dual)

        val viewModel =
            rememberViewModel("NotificationsShadeOverlay-viewModel") {
                contentViewModelFactory.create()
            }

        BackHandler {
            viewModel.onScrimClicked()
        }
        val placeholderViewModel =
            rememberViewModel("NotificationsShadeOverlay-notifPlaceholderViewModel") {
                viewModel.notificationsPlaceholderViewModelFactory.create(
                    Overlays.NotificationsShade
                )
            }
        val notificationRulesParentViewModel =
            if (NmContextualDisplayLaunch.isEnabled) {
                rememberViewModel("NotificationsShadeOverlay-notifRulesParentViewModel") {
                    notificationRulesParentViewModelFactory.create()
                }
            } else {
                null
            }

        DisposableEffectWithLifecycle(Unit) {
            onDispose { viewModel.onShadeOverlayBoundsChanged(null) }
        }

        val isFullWidth = LocalSceneContainerPreloadedResources.current.isFullWidthShade

        val targetBlurRadiusPx: Float by
            remember(layoutState) {
                derivedStateOf { viewModel.calculateTargetBlurRadius(layoutState.transitionState) }
            }
        val animatedBlurRadiusPx: Float by
            animateFloatAsState(targetValue = targetBlurRadiusPx, label = "NSOverlay-blurRadius")

        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

        OverlayShade(
            panelElement = NotificationsShade.Elements.Panel,
            alignmentOnWideScreens = if (isHyperOS && isLandscape) Alignment.CenterHorizontally else viewModel.alignmentOnWideScreens,
            statusBarHeightPx = viewModel.statusBarHeightPx,
            enableTransparency = viewModel.isTransparencyEnabled,
            modifier = modifier.blur(with(LocalDensity.current) { animatedBlurRadiusPx.toDp() }),
            onScrimClicked = viewModel::onScrimClicked,
            onBackgroundPlaced = { bounds, _, _ -> viewModel.onShadeOverlayBoundsChanged(bounds) },
            header = {
                if (isHyperOS) {
                    HyperOSNotificationsHeader(
                        modifier = Modifier.element(NotificationsShade.Elements.StatusBar)
                    )
                } else if (viewModel.showHeader) {
                    val headerViewModel =
                        rememberViewModel("NotificationsShadeOverlayHeader") {
                            viewModel.shadeHeaderViewModelFactory.create()
                        }
                    OverlayShadeHeader(
                        viewModel = headerViewModel,
                        notificationsHighlight = ChipHighlightModel.Strong,
                        quickSettingsHighlight = headerViewModel.inactiveChipHighlight,
                        showClock = true,
                        modifier = Modifier.element(NotificationsShade.Elements.StatusBar),
                    )
                }
            },
        ) {
            val focusRequester = remember { FocusRequester() }

            LaunchedEffectWithLifecycle(focusRequester) {
                focusRequester.requestFocus()
            }

            val accessibilityTitle = stringResource(R.string.accessibility_desc_notification_shade)
            val stackScrollViewInstance = stackScrollView.get()

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = if (isHyperOS && isLandscape) {
                        Modifier
                            .fillMaxHeight()
                            .width(560.dp)
                    } else {
                        Modifier.fillMaxSize()
                    }
                ) {
                    ScrollingNotificationPanel(
                        tag = "NotifShadeOverlay",
                        shadeSession = shadeSession,
                        stackScrollView = stackScrollViewInstance,
                        viewModel = placeholderViewModel,
                        notificationRulesParentViewModel = notificationRulesParentViewModel,
                        jankMonitor = jankMonitor,
                        shouldPunchHoleBehindScrim = false,
                        isTransparencyEnabled = viewModel.isTransparencyEnabled,
                        stackTopPadding = notificationStackPadding,
                        stackBottomPadding = { 4.dp },
                        shouldFillMaxHeight = true,
                        aboveNotifications = {
                            if (!isHyperOS && isFullWidth) {
                                Box(
                                    Modifier.padding(
                                        start = notificationStackPadding,
                                        end = notificationStackPadding,
                                        bottom = 8.dp,
                                    )
                                ) {
                                    with(lockscreenElements) {
                                        LockscreenElement(
                                            LockscreenElementKeys.Clock.Small,
                                            Modifier.height(88.dp),
                                        )
                                    }
                                }
                            }

                            if (!isHyperOS && viewModel.showMedia) {
                                Box(
                                    modifier =
                                        Modifier.fillMaxWidth().padding(
                                            start = notificationStackPadding,
                                            end = notificationStackPadding,
                                            bottom = 8.dp,
                                        )
                                ) {
                                    Media(
                                        viewModelFactory = viewModel.mediaViewModelFactory,
                                        presentationStyle = MediaPresentationStyle.Default,
                                        behavior = viewModel.mediaUiBehavior,
                                        onDismissed = viewModel::onMediaSwipeToDismiss,
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)),
                                        location = Media.Location.SHADE,
                                    )
                                }
                            }
                        },
                        shouldDrawScrimBackground = false,
                        useVerticalOverscrollEffect = false,
                        modifier =
                            Modifier.fillMaxWidth().focusRequester(focusRequester).focusable().semantics {
                                paneTitle = accessibilityTitle
                            }.focusProperties {
                                onEnter = { stackScrollViewInstance.asView().requestFocus() }
                            },
                    )
                }

                if (isHyperOS) {
                    val nsslView = stackScrollViewInstance.asView()
                    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    val navBarEnd = WindowInsets.navigationBars.asPaddingValues().calculateEndPadding(androidx.compose.ui.unit.LayoutDirection.Ltr)
                    val isDark = isSystemInDarkTheme()

                    DisposableEffect(nsslView, isLandscape, isDark, navBarBottom, navBarEnd) {
                        val container = (nsslView.parent as? ViewGroup) ?: (nsslView as? ViewGroup)
                        if (container == null) return@DisposableEffect onDispose {}

                        val floatingClearBtnView = ComposeView(context).apply {
                            setViewCompositionStrategy(
                                ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool
                            )
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setContent {
                                val clearBtnBg = if (isDark) Color(0xEE2A2A2E) else Color.White
                                val clearIconTint = if (isDark) Color.White else Color(0xFF1C1B1F)

                                val interactionSource = remember { MutableInteractionSource() }
                                val isPressed by interactionSource.collectIsPressedAsState()
                                val scale by animateFloatAsState(
                                    targetValue = if (isPressed) 0.88f else 1.0f,
                                    animationSpec = androidx.compose.animation.core.spring(
                                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                        stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                                    ),
                                    label = "ClearAllButtonScale"
                                )

                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = if (isLandscape) Alignment.CenterEnd else Alignment.BottomCenter
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(
                                                bottom = if (isLandscape) 0.dp else navBarBottom + 28.dp,
                                                end = if (isLandscape) navBarEnd + 36.dp else 0.dp,
                                            )
                                            .graphicsLayer {
                                                scaleX = scale
                                                scaleY = scale
                                            }
                                            .shadow(elevation = 8.dp, shape = CircleShape)
                                            .size(54.dp)
                                            .clip(CircleShape)
                                            .background(clearBtnBg)
                                            .clickable(
                                                interactionSource = interactionSource,
                                                indication = null
                                            ) {
                                                nsslView.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                                (nsslView as? NotificationStackScrollLayout)?.clearAllNotifications(false)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_close),
                                            contentDescription = "Borrar notificaciones",
                                            tint = clearIconTint,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }

                        container.addView(floatingClearBtnView)
                        onDispose {
                            container.removeView(floatingClearBtnView)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HyperOSNotificationMediaCard(
    viewModelFactory: com.android.systemui.media.remedia.ui.viewmodel.MediaViewModel.Factory,
    view: android.view.View,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mediaViewModel = rememberViewModel("HyperOSNotifMediaCard") {
        viewModelFactory.create(context, MediaCarouselVisibility.WhenNotEmpty)
    }
    val cards = mediaViewModel.cards
    val currentCard = cards.firstOrNull() ?: return

    val backgroundBitmap = remember(currentCard.background) {
        (currentCard.background as? com.android.systemui.common.shared.model.Icon.Loaded)?.asImageBitmap()
    }

    val isDark = isSystemInDarkTheme()
    val cardBg = if (isDark) Color(0xEE2A2A2E) else Color(0xF5FFFFFF)
    val textColor = if (isDark) Color.White else Color(0xFF191C1E)
    val textSubColor = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF74777F)
    val iconTintColor = if (isDark) Color.White else Color(0xFF191C1E)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1.0f,
        label = "NotifMediaCardScale"
    )

    val cardPkg = (currentCard.icon as? com.android.systemui.common.shared.model.Icon.Loaded)?.packageName
        ?: (currentCard.key as? String)?.substringBefore(":") ?: ""

    val appIconBitmap = remember(currentCard.icon, cardPkg) {
        (currentCard.icon as? com.android.systemui.common.shared.model.Icon.Loaded)?.asImageBitmap()
            ?: try {
                if (cardPkg.isNotBlank()) {
                    val drawable = context.packageManager.getApplicationIcon(cardPkg)
                    val bmp = android.graphics.Bitmap.createBitmap(
                        drawable.intrinsicWidth.coerceAtLeast(1),
                        drawable.intrinsicHeight.coerceAtLeast(1),
                        android.graphics.Bitmap.Config.ARGB_8888
                    )
                    val canvas = android.graphics.Canvas(bmp)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    bmp.asImageBitmap()
                } else null
            } catch (e: Exception) {
                null
            }
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(elevation = 2.dp, shape = RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(cardBg)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    try {
                        val controller = HyperOSMediaHelper.getActiveController(context, cardPkg)
                        if (controller?.sessionActivity != null) {
                            controller.sessionActivity?.send()
                        } else {
                            currentCard.onClick.invoke(Expandable.fromView(view))
                        }
                    } catch (e: Exception) {
                        try {
                            val launchIntent = context.packageManager.getLaunchIntentForPackage(cardPkg)
                            if (launchIntent != null) {
                                context.startActivity(launchIntent)
                            }
                        } catch (e2: Exception) {}
                    }
                },
                onLongClick = {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    try {
                        currentCard.onLongClick.invoke()
                    } catch (e: Exception) {}
                }
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Row: Album cover + Title & Artist + Cast Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album Art
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x22888888)),
                    contentAlignment = Alignment.Center
                ) {
                    if (backgroundBitmap != null) {
                        Image(
                            bitmap = backgroundBitmap,
                            contentDescription = "Portada",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (appIconBitmap != null) {
                        Image(
                            bitmap = appIconBitmap,
                            contentDescription = "Música",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(32.dp)
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_music_note),
                            contentDescription = "Música",
                            tint = textColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // HyperOS app indicator badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(2.dp)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (appIconBitmap != null) {
                            Image(
                                bitmap = appIconBitmap,
                                contentDescription = "App Icon",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                            )
                        } else {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_music_note),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.width(12.dp))

                // Title + Subtitle
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = currentCard.title.ifBlank { "Música" },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            fontSize = 15.sp,
                            color = textColor
                        ),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentCard.subtitle.ifBlank { "" },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            color = textSubColor
                        ),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }

                // Output Switcher / Cast
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            try {
                                currentCard.outputSwitcherChipButton.onClick?.invoke()
                            } catch (e: Exception) {}
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_cast),
                        contentDescription = "Compartir audio",
                        tint = textSubColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Middle Controls: Previous, Play/Pause, Next
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Prev
                val prevInteraction = remember { MutableInteractionSource() }
                val isPrevPressed by prevInteraction.collectIsPressedAsState()
                val prevScale by animateFloatAsState(targetValue = if (isPrevPressed) 0.82f else 1.0f, label = "PrevScale")

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .graphicsLayer { scaleX = prevScale; scaleY = prevScale }
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = prevInteraction,
                            indication = null
                        ) {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            HyperOSMediaHelper.skipPrev(context, cardPkg)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_media_previous),
                        contentDescription = "Anterior",
                        tint = iconTintColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(Modifier.width(42.dp))

                // Play / Pause
                val isPlaying = currentCard.playPauseAction?.state != com.android.systemui.media.remedia.shared.model.MediaSessionState.Paused
                val playInteraction = remember { MutableInteractionSource() }
                val isPlayPressed by playInteraction.collectIsPressedAsState()
                val playScale by animateFloatAsState(targetValue = if (isPlayPressed) 0.85f else 1.0f, label = "PlayScale")

                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .graphicsLayer { scaleX = playScale; scaleY = playScale }
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = playInteraction,
                            indication = null
                        ) {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            HyperOSMediaHelper.playPause(context, cardPkg)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val iconRes = if (isPlaying) R.drawable.ic_media_pause else R.drawable.ic_media_play
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                        tint = iconTintColor,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(Modifier.width(42.dp))

                // Next
                val nextInteraction = remember { MutableInteractionSource() }
                val isNextPressed by nextInteraction.collectIsPressedAsState()
                val nextScale by animateFloatAsState(targetValue = if (isNextPressed) 0.82f else 1.0f, label = "NextScale")

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .graphicsLayer { scaleX = nextScale; scaleY = nextScale }
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = nextInteraction,
                            indication = null
                        ) {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            HyperOSMediaHelper.skipNext(context, cardPkg)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_media_next),
                        contentDescription = "Siguiente",
                        tint = iconTintColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Bottom Seekbar Row: Current Time + Slider Track + Duration
            val seekbar = currentCard.navigation as? MediaNavigationViewModel.Showing
            if (seekbar != null) {
                var isScrubbingLocal by remember { mutableStateOf(false) }
                var localScrubFraction by remember { mutableFloatStateOf(0f) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = seekbar.progressText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            color = textSubColor
                        )
                    )

                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .height(20.dp)
                            .pointerInput(seekbar) {
                                detectHorizontalDragGestures(
                                    onDragStart = { offset ->
                                        isScrubbingLocal = true
                                        val width = size.width
                                        localScrubFraction = (offset.x / width).coerceIn(0f, 1f)
                                        seekbar.onScrubChange?.invoke(localScrubFraction)
                                    },
                                    onHorizontalDrag = { change, _ ->
                                        change.consume()
                                        val width = size.width
                                        localScrubFraction = (change.position.x / width).coerceIn(0f, 1f)
                                        seekbar.onScrubChange?.invoke(localScrubFraction)
                                    },
                                    onDragEnd = {
                                        seekbar.onScrubFinished?.invoke(androidx.compose.ui.geometry.Offset(10f, 0f), true)
                                        val dur = HyperOSMediaHelper.getActiveController(context, cardPkg)
                                            ?.metadata?.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION) ?: 0L
                                        if (dur > 0L) {
                                            HyperOSMediaHelper.seekTo(context, (localScrubFraction * dur).toLong(), cardPkg)
                                        }
                                        isScrubbingLocal = false
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val displayProgress = if (isScrubbingLocal) localScrubFraction else seekbar.progress.coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(textSubColor.copy(alpha = 0.25f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(displayProgress)
                                    .background(textSubColor.copy(alpha = 0.85f))
                            )
                        }
                    }

                    Text(
                        text = seekbar.durationText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            color = textSubColor
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun HyperOSNotificationsHeader(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTimeMillis = System.currentTimeMillis()
            delay(1000)
        }
    }

    val timeStr = remember(currentTimeMillis) {
        val is24 = android.text.format.DateFormat.is24HourFormat(context)
        val pattern = if (is24) "H:mm" else "h:mm"
        val sdf = java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
        sdf.format(java.util.Date(currentTimeMillis))
    }
    val dateStr = remember(currentTimeMillis) {
        val sdf = java.text.SimpleDateFormat("EEE, d MMM", java.util.Locale.getDefault())
        val raw = sdf.format(java.util.Date(currentTimeMillis))
        raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
    }

    val carrierName = remember(currentTimeMillis) {
        try {
            val subManager = context.getSystemService(android.telephony.SubscriptionManager::class.java)
            val activeSubs = subManager?.activeSubscriptionInfoList
            if (!activeSubs.isNullOrEmpty()) {
                activeSubs.mapNotNull { it.displayName?.toString()?.takeIf { s -> s.isNotBlank() } }.joinToString(" | ")
            } else {
                val telephonyManager = context.getSystemService(android.telephony.TelephonyManager::class.java)
                telephonyManager?.networkOperatorName?.takeIf { it.isNotBlank() } ?: ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        val navBarStart = WindowInsets.navigationBars.asPaddingValues().calculateStartPadding(androidx.compose.ui.unit.LayoutDirection.Ltr)
        val navBarEnd = WindowInsets.navigationBars.asPaddingValues().calculateEndPadding(androidx.compose.ui.unit.LayoutDirection.Ltr)
        // Single row header matching Image 3: Left = Date & Time, Right = Carrier
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(start = navBarStart + 24.dp, end = navBarEnd + 24.dp, top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$dateStr   $timeStr",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 17.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    color = Color.White,
                    letterSpacing = (-0.5).sp
                )
            )

            if (carrierName.isNotBlank()) {
                Text(
                    text = carrierName,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                )
            }
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(
                    start = 28.dp,
                    end = 24.dp,
                    top = if (statusBarTop > 0.dp) statusBarTop + 12.dp else 28.dp,
                    bottom = 10.dp
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 68.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        color = Color.White,
                        letterSpacing = (-2.5).sp,
                        lineHeight = 70.sp
                    )
                )
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 16.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.90f),
                        letterSpacing = (-0.2).sp
                    )
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 4.dp, end = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            try {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_ALL_APPS_NOTIFICATION_SETTINGS).apply {
                                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    val intent2 = android.content.Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    }
                                    context.startActivity(intent2)
                                } catch (e2: Exception) {}
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.notif_footer_btn_settings),
                        contentDescription = "Ajustes de notificaciones",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (carrierName.isNotBlank()) {
                    Text(
                        text = carrierName,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    )
                }
            }
        }
    }
}

object NotificationsShade {
    object Elements {
        val Panel = ElementKey("NotificationsShadeOverlayPanel")
        val StatusBar = ElementKey("NotificationsShadeOverlayStatusBar")
    }
}

