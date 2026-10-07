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

package com.android.systemui.qs.ui.composable

import android.content.Intent
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import com.android.systemui.Dependency
import com.android.systemui.plugins.ActivityStarter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onLayoutRectChanged
import com.android.systemui.common.shared.model.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import com.android.compose.PlatformSliderDefaults
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.UserAction
import com.android.compose.animation.scene.UserActionResult
import com.android.compose.animation.scene.content.state.TransitionState
import com.android.compose.gesture.gesturesDisabled
import com.android.compose.lifecycle.DisposableEffectWithLifecycle
import com.android.compose.lifecycle.LaunchedEffectWithLifecycle
import com.android.compose.modifiers.thenIf
import com.android.systemui.brightness.domain.model.GammaBrightness
import com.android.systemui.brightness.ui.compose.BrightnessSliderContainer
import com.android.systemui.brightness.ui.compose.BrightnessSliderDimensions
import com.android.systemui.brightness.ui.compose.ContainerColors
import com.android.systemui.brightness.ui.viewmodel.BrightnessSliderViewModel
import com.android.systemui.brightness.ui.viewmodel.Drag
import com.android.systemui.compose.modifiers.sysuiResTag
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.animation.Expandable
import com.android.systemui.development.ui.compose.BuildNumber
import com.android.systemui.development.ui.viewmodel.BuildNumberViewModel
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.media.remedia.shared.model.MediaSessionState
import com.android.systemui.media.remedia.ui.compose.Media
import com.android.systemui.media.remedia.ui.compose.MediaPresentationStyle
import com.android.systemui.media.remedia.ui.viewmodel.MediaCardViewModel
import com.android.systemui.media.remedia.ui.viewmodel.MediaCarouselVisibility
import com.android.systemui.media.remedia.ui.viewmodel.MediaSecondaryActionViewModel
import com.android.systemui.media.remedia.ui.viewmodel.MediaViewModel
import com.android.systemui.notifications.ui.composable.SnoozableHeadsUpNotificationPlaceholder
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.text.style.TextAlign
import com.android.systemui.qs.composefragment.ui.GridAnchor
import com.android.systemui.qs.flags.QsDetailedView
import com.android.systemui.qs.panels.ui.compose.EditMode
import com.android.systemui.qs.panels.ui.compose.TileDetails
import com.android.systemui.qs.panels.ui.compose.TileGrid
import com.android.systemui.qs.panels.ui.compose.TileListener
import androidx.compose.runtime.produceState
import com.android.systemui.qs.panels.ui.compose.infinitegrid.SmallTileContent
import com.android.systemui.qs.panels.ui.viewmodel.toIconProvider
import com.android.systemui.qs.panels.ui.viewmodel.toUiState
import com.android.systemui.qs.panels.ui.compose.toolbar.Toolbar
import com.android.systemui.qs.panels.ui.viewmodel.EditTileViewModel
import com.android.systemui.qs.panels.ui.viewmodel.EditModeViewModel
import com.android.systemui.qs.panels.ui.viewmodel.toolbar.ToolbarViewModel
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.tiles.dialog.AudioDetailsViewModel
import com.android.systemui.qs.ui.composable.QuickSettingsShade.systemGestureExclusionInShade
import com.android.systemui.qs.ui.viewmodel.QuickSettingsContainerViewModel
import com.android.systemui.qs.ui.viewmodel.QuickSettingsShadeOverlayActionsViewModel
import com.android.systemui.qs.ui.viewmodel.QuickSettingsShadeOverlayContentViewModel
import com.android.systemui.res.R
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.ui.composable.LocalSceneContainerPreloadedResources
import com.android.systemui.scene.ui.composable.Overlay
import com.android.systemui.shade.ui.composable.ChipHighlightModel
import com.android.systemui.shade.ui.composable.OverlayShade
import com.android.systemui.shade.ui.composable.OverlayShadeHeader
import com.android.systemui.shade.ui.composable.QuickSettingsOverlayHeader
import com.android.systemui.shade.ui.composable.QuickSettingsOverlayPrivacyChip
import com.android.systemui.statusbar.notification.stack.shared.model.ShadeScrimBounds
import com.android.systemui.statusbar.notification.stack.shared.model.ShadeScrimShape
import com.android.systemui.statusbar.notification.stack.ui.view.NotificationScrollView
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.NotificationsPlaceholderViewModel
import com.android.systemui.volume.panel.component.volume.slider.ui.viewmodel.AudioStreamSliderViewModel
import com.android.systemui.volume.panel.component.volume.ui.composable.VolumeSlider
import com.android.systemui.volume.panel.component.volume.ui.composable.VolumeSliderDimensions
import dagger.Lazy
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow

@SysUISingleton
class QuickSettingsShadeOverlay
@Inject
constructor(
    private val actionsViewModelFactory: QuickSettingsShadeOverlayActionsViewModel.Factory,
    private val contentViewModelFactory: QuickSettingsShadeOverlayContentViewModel.Factory,
    private val quickSettingsContainerViewModelFactory: QuickSettingsContainerViewModel.Factory,
    private val notificationStackScrollView: Lazy<NotificationScrollView>,
    private val notificationsPlaceholderViewModelFactory: NotificationsPlaceholderViewModel.Factory,
) : Overlay {

    override val key = Overlays.QuickSettingsShade

    private val actionsViewModel: QuickSettingsShadeOverlayActionsViewModel by lazy {
        actionsViewModelFactory.create()
    }

    override val userActions: Flow<Map<UserAction, UserActionResult>> = actionsViewModel.actions

    override val alwaysCompose: Boolean = false

    override suspend fun activate(): Nothing {
        actionsViewModel.activate()
    }

    @Composable
    override fun ContentScope.Content(modifier: Modifier) {
        val coroutineScope = rememberCoroutineScope()
        val contentViewModel =
            rememberViewModel("QuickSettingsShadeOverlayContent", key = coroutineScope) {
                contentViewModelFactory.create(coroutineScope)
            }
        val useBrightnessMirrorInOverlay = useBrightnessMirrorInOverlay()
        val quickSettingsContainerViewModel =
            rememberViewModel("QuickSettingsShadeOverlayContainer") {
                quickSettingsContainerViewModelFactory.create(
                    supportsBrightnessMirroring = useBrightnessMirrorInOverlay
                )
            }
        val hunPlaceholderViewModel =
            rememberViewModel("QuickSettingsShadeOverlayPlaceholder") {
                notificationsPlaceholderViewModelFactory.create(Overlays.QuickSettingsShade)
            }

        val showBrightnessMirror =
            quickSettingsContainerViewModel.brightnessSliderViewModel.showMirror
        val contentAlphaFromBrightnessMirror by
            animateFloatAsState(if (showBrightnessMirror) 0f else 1f)

        val targetBlurRadiusPx: Float by
            remember(layoutState) {
                derivedStateOf {
                    contentViewModel.calculateTargetBlurRadius(layoutState.transitionState)
                }
            }
        val animatedBlurRadiusPx: Float by
            animateFloatAsState(targetValue = targetBlurRadiusPx, label = "NSOverlay-blurRadius")

        // Set the bounds to null when the QuickSettings overlay disappears.
        DisposableEffectWithLifecycle(Unit) {
            onDispose {
                contentViewModel.onPanelShapeInWindowChanged(null)
                contentViewModel.onShadeOverlayBoundsChanged(null)
            }
        }

        LaunchedEffectWithLifecycle(key1 = Unit) { contentViewModel.detectShadeModeChanges() }

        val context = androidx.compose.ui.platform.LocalContext.current
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

        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val navBarStart = WindowInsets.navigationBars.asPaddingValues().calculateStartPadding(androidx.compose.ui.unit.LayoutDirection.Ltr)
        val navBarEnd = WindowInsets.navigationBars.asPaddingValues().calculateEndPadding(androidx.compose.ui.unit.LayoutDirection.Ltr)

        val isEditingTiles by quickSettingsContainerViewModel.editModeViewModel.isEditing.collectAsStateWithLifecycle()
        BackHandler(enabled = !isEditingTiles) {
            contentViewModel.onScrimClicked()
        }

        Box(
            modifier =
                modifier
                    .graphicsLayer { alpha = contentAlphaFromBrightnessMirror }
                    .blur(with(LocalDensity.current) { animatedBlurRadiusPx.toDp() })
                    .thenIf(showBrightnessMirror) { Modifier.gesturesDisabled() }
        ) {
            OverlayShade(
                panelElement = QuickSettingsShade.Elements.Panel,
                alignmentOnWideScreens = Alignment.End,
                statusBarHeightPx = contentViewModel.statusBarHeightPx,
                enableTransparency = contentViewModel.isTransparencyEnabled,
                onScrimClicked = contentViewModel::onScrimClicked,
                onBackgroundPlaced = { bounds, topCornerRadius, bottomCornerRadius ->
                    contentViewModel.onShadeOverlayBoundsChanged(bounds)
                    contentViewModel.onPanelShapeInWindowChanged(
                        ShadeScrimShape(
                            bounds = ShadeScrimBounds(bounds),
                            topRadius = topCornerRadius.roundToInt(),
                            bottomRadius = bottomCornerRadius.roundToInt(),
                        )
                    )
                },
                header = {
                    if (contentViewModel.showHeader) {
                        val headerViewModel = quickSettingsContainerViewModel.shadeHeaderViewModel
                        OverlayShadeHeader(
                            viewModel = headerViewModel,
                            notificationsHighlight = headerViewModel.inactiveChipHighlight,
                            quickSettingsHighlight = ChipHighlightModel.Strong,
                            showClock = true,
                            modifier = Modifier
                                .element(QuickSettingsShade.Elements.StatusBar)
                                .then(
                                    if (isLandscape) {
                                        Modifier.padding(
                                            start = navBarStart + 16.dp,
                                            end = navBarEnd + 16.dp
                                        )
                                    } else Modifier
                                ),
                        )
                    }
                },
            ) {
                QuickSettingsContainer(
                    contentViewModel = contentViewModel,
                    containerViewModel = quickSettingsContainerViewModel,
                )
            }
            SnoozableHeadsUpNotificationPlaceholder(
                tag = "QSShadeOverlay",
                stackScrollView = notificationStackScrollView.get(),
                viewModel = hunPlaceholderViewModel,
            )
        }
    }
}

/** The possible states of the `ShadeBody`. */
private sealed interface ShadeBodyState {
    data object Editing : ShadeBodyState

    data object TileDetails : ShadeBodyState

    data object Default : ShadeBodyState
}

@Composable
@ReadOnlyComposable
private fun useBrightnessMirrorInOverlay(): Boolean {
    // The `config_useBrightnessMirrorInOverlay` config is true by default. If false, the Quick
    // Settings shade overlay will remain visible during brightness adjustments.
    return LocalResources.current.getBoolean(R.bool.config_useBrightnessMirrorInOverlay)
}

@Composable
private fun ContentScope.QuickSettingsContainer(
    contentViewModel: QuickSettingsShadeOverlayContentViewModel,
    containerViewModel: QuickSettingsContainerViewModel,
    modifier: Modifier = Modifier,
) {
    val isEditing by containerViewModel.editModeViewModel.isEditing.collectAsStateWithLifecycle()
    val tileDetails =
        if (QsDetailedView.isEnabled) containerViewModel.detailsViewModel.activeTileDetails
        else null

    val accessibilityTitle =
        when {
            isEditing -> stringResource(R.string.accessibility_desc_quick_settings_edit)
            (QsDetailedView.isEnabled && tileDetails != null) -> tileDetails.title
            else -> stringResource(R.string.accessibility_desc_quick_settings)
        }

    val focusRequester = remember { FocusRequester() }

    var currentHeight by remember { mutableStateOf(0) }

    LaunchedEffectWithLifecycle(focusRequester) {
        // Request focus on the `QuickSettingsContainer` without user interaction so that the user
        // can press the tab key once to enter the Quick Settings area. Without this line, the user
        // has to tab through unrelated views of the higher view hierarchy level.
        focusRequester.requestFocus()
    }

    AnimatedContent(
        modifier =
            Modifier.focusRequester(focusRequester)
                .focusable()
                .onLayoutRectChanged { currentHeight = it.height }
                .semantics { paneTitle = accessibilityTitle }
                .sysuiResTag("quick_settings_container"),
        targetState =
            when {
                isEditing -> ShadeBodyState.Editing
                tileDetails != null -> ShadeBodyState.TileDetails
                else -> ShadeBodyState.Default
            },
        transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(500)) },
    ) { state ->
        when (state) {
            ShadeBodyState.Editing -> {
                val context = androidx.compose.ui.platform.LocalContext.current
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
                if (isDualShade && controlCenterStyle == 1) {
                    HyperOSEditMode(
                        viewModel = containerViewModel.editModeViewModel,
                        modifier = modifier.fillMaxWidth(),
                    )
                } else {
                    EditMode(
                        viewModel = containerViewModel.editModeViewModel,
                        modifier =
                            modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = QuickSettingsShade.Dimensions.HorizontalPadding,
                                    vertical = QuickSettingsShade.Dimensions.VerticalPadding,
                                ),
                    )
                }
            }

            ShadeBodyState.TileDetails -> {
                TileDetails(
                    modifier = modifier,
                    containerViewModel.detailsViewModel,
                    initialHeight = { currentHeight },
                )
            }

            ShadeBodyState.Default -> {
                val context = androidx.compose.ui.platform.LocalContext.current
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

                if (isDualShade && controlCenterStyle == 1) {
                    HyperOSQuickSettingsLayout(
                        qsContainerViewModel = containerViewModel,
                        toolbarViewModelFactory = contentViewModel.toolbarViewModelFactory,
                        buildNumberViewModelFactory = contentViewModel.buildNumberViewModelFactory,
                        isTransparencyEnabled = contentViewModel.isTransparencyEnabled,
                        volumeSliderViewModel = contentViewModel.volumeSliderViewModel,
                        audioDetailsViewModelFactory = contentViewModel.audioDetailsViewModelFactory,
                        modifier = modifier.sysuiResTag("quick_settings_panel"),
                    )
                } else {
                    QuickSettingsLayout(
                        qsContainerViewModel = containerViewModel,
                        toolbarViewModelFactory = contentViewModel.toolbarViewModelFactory,
                        buildNumberViewModelFactory = contentViewModel.buildNumberViewModelFactory,
                        isTransparencyEnabled = contentViewModel.isTransparencyEnabled,
                        volumeSliderViewModel = contentViewModel.volumeSliderViewModel,
                        audioDetailsViewModelFactory = contentViewModel.audioDetailsViewModelFactory,
                        modifier = modifier.sysuiResTag("quick_settings_panel"),
                    )
                }
            }
        }
    }
}

/** Column containing Brightness and QS tiles. */
@Composable
private fun ContentScope.QuickSettingsLayout(
    qsContainerViewModel: QuickSettingsContainerViewModel,
    toolbarViewModelFactory: ToolbarViewModel.Factory,
    buildNumberViewModelFactory: BuildNumberViewModel.Factory,
    isTransparencyEnabled: Boolean,
    volumeSliderViewModel: AudioStreamSliderViewModel?,
    audioDetailsViewModelFactory: AudioDetailsViewModel.Factory,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(horizontal = QuickSettingsShade.Dimensions.HorizontalPadding),
    ) {
        if (LocalSceneContainerPreloadedResources.current.isFullWidthShade) {
            QuickSettingsOverlayPrivacyChip(qsContainerViewModel.shadeHeaderViewModel)
            VerticalSeparator(QuickSettingsShade.Dimensions.ShortPadding)
            QuickSettingsOverlayHeader(
                viewModel = qsContainerViewModel.shadeHeaderViewModel,
                modifier = Modifier.element(QuickSettingsShade.Elements.Header),
            )

            VerticalSeparator(QuickSettingsShade.Dimensions.ShortPadding)
        } else {
            VerticalSeparator(QuickSettingsShade.Dimensions.VerticalPadding)
            QuickSettingsOverlayPrivacyChip(
                qsContainerViewModel.shadeHeaderViewModel,
                modifier = Modifier.padding(bottom = QuickSettingsShade.Dimensions.ShortPadding),
            )
        }

        val toolbarViewModel =
            rememberViewModel("QuickSettingsLayout") { toolbarViewModelFactory.create() }

        Toolbar(
            modifier =
                Modifier.fillMaxWidth()
                    .requiredHeight(QuickSettingsShade.Dimensions.ToolbarHeight)
                    .sysuiResTag("quick_settings_toolbar"),
            viewModel = toolbarViewModel,
            isFullyVisible = { layoutState.isIdle(contentKey) },
        )

        VerticalSeparator(QuickSettingsShade.Dimensions.ToolbarBottomPadding)

        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Media(
                    viewModelFactory = qsContainerViewModel.mediaViewModelFactory,
                    presentationStyle = MediaPresentationStyle.Compact,
                    behavior = QuickSettingsContainerViewModel.mediaUiBehavior,
                    onDismissed = qsContainerViewModel::onMediaSwipeToDismiss,
                    modifier = Modifier,
                    location = Media.Location.QS,
                )

                if (qsContainerViewModel.showMedia) {
                    VerticalSeparator(QuickSettingsShade.Dimensions.VerticalPadding)
                }

                if (qsContainerViewModel.isBrightnessSliderVisible) {
                    Box(
                        Modifier.systemGestureExclusionInShade(
                            enabled = { layoutState.transitionState is TransitionState.Idle }
                        )
                    ) {
                        BrightnessSliderContainer(
                            viewModel = qsContainerViewModel.brightnessSliderViewModel,
                            containerColors =
                                ContainerColors(
                                    idleColor = Color.Transparent,
                                    mirrorColor =
                                        OverlayShade.Colors.panelBackground(isTransparencyEnabled),
                                ),
                            modifier = Modifier.fillMaxWidth(),
                            dimensions = QuickSettingsShade.Dimensions.brightnessSliderDimensions,
                        )
                    }
                }

                if (volumeSliderViewModel != null) {
                    val volumeSliderState by volumeSliderViewModel.slider.collectAsStateWithLifecycle()

                    VerticalSeparator(QuickSettingsShade.Dimensions.VolumeSliderExtraPadding)
                    Box(
                        Modifier.systemGestureExclusionInShade(
                            enabled = { layoutState.transitionState is TransitionState.Idle }
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            VolumeSlider(
                                modifier = Modifier.weight(1f),
                                showLabel = false,
                                state = volumeSliderState,
                                onValueChange = { newValue: Float ->
                                    volumeSliderViewModel.onValueChanged(volumeSliderState, newValue)
                                },
                                onValueChangeFinished = {
                                    volumeSliderViewModel.onValueChangeFinished()
                                },
                                onIconTapped = { volumeSliderViewModel.toggleMuted(volumeSliderState) },
                                sliderColors = PlatformSliderDefaults.defaultPlatformSliderColors(),
                                hapticsViewModelFactory =
                                    volumeSliderViewModel.getSliderHapticsViewModelFactory(),
                                dimensions = QuickSettingsShade.Dimensions.VolumeSliderDimensions,
                            )
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                modifier =
                                    Modifier.size(
                                        QuickSettingsShade.Dimensions.VolumeSliderDimensions.trackHeight
                                    ),
                                colors =
                                    IconButtonDefaults.iconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                                onClick = {
                                    qsContainerViewModel.detailsViewModel.onVolumeSettingsButtonClicked(
                                        audioDetailsViewModelFactory.create()
                                    )
                                },
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_more_vert),
                                    contentDescription = "Volume settings",
                                )
                            }
                        }
                    }
                }

            VerticalSeparator(QuickSettingsShade.Dimensions.VerticalPadding)

            GridAnchor()
            TileGrid(
                viewModel = qsContainerViewModel.tileGridViewModel,
                modifier = Modifier.fillMaxWidth(),
                enableRevealEffect = TileRevealFlag.isEnabled,
            )

            val buildNumberViewModel =
                rememberViewModel("QuickSettingsShadeOverlay.BuildNumber") {
                    buildNumberViewModelFactory.create()
                }

            if (buildNumberViewModel.buildNumber != null) {
                VerticalSeparator(QuickSettingsShade.Dimensions.ShortPadding)
                BuildNumber(
                    viewModel = buildNumberViewModel,
                    modifier =
                        Modifier.align(Alignment.Start)
                            .padding(start = QuickSettingsShade.Dimensions.HorizontalPadding),
                )
            }

            VerticalSeparator(QuickSettingsShade.Dimensions.ShortPadding)
        }
    }
}

@Composable
private fun ContentScope.HyperOSQuickSettingsLayout(
    qsContainerViewModel: QuickSettingsContainerViewModel,
    toolbarViewModelFactory: ToolbarViewModel.Factory,
    buildNumberViewModelFactory: BuildNumberViewModel.Factory,
    isTransparencyEnabled: Boolean,
    volumeSliderViewModel: AudioStreamSliderViewModel?,
    audioDetailsViewModelFactory: AudioDetailsViewModel.Factory,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val audioManager = remember(context) { context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager }

    val allTiles = qsContainerViewModel.tileGridViewModel.tileViewModels
    // Start tile listening so all tiles get live state updates and animations
    TileListener(tiles = allTiles, listeningEnabled = { true })

    val internetTile = allTiles.find { it.spec.spec == "internet" || it.spec.spec == "wifi" }
    val cellTile = allTiles.find { it.spec.spec == "cell" }
    val gridTiles = remember(allTiles) {
        allTiles.filter { it.spec.spec != "internet" && it.spec.spec != "wifi" && it.spec.spec != "cell" }
    }
    val wifiManager = remember(context) { context.getSystemService(android.net.wifi.WifiManager::class.java) }
    val telephonyManager = remember(context) { context.getSystemService(android.telephony.TelephonyManager::class.java) }

    var isWifiEnabled by remember {
        mutableStateOf(wifiManager?.isWifiEnabled ?: false)
    }
    var isDataEnabled by remember {
        val globalOn = try {
            android.provider.Settings.Global.getInt(
                context.contentResolver,
                android.provider.Settings.Global.MOBILE_DATA,
                0
            ) == 1
        } catch (e: Exception) { false }
        mutableStateOf(globalOn || (telephonyManager?.isDataEnabled ?: false))
    }

    DisposableEffect(context, wifiManager, telephonyManager) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, intent: android.content.Intent?) {
                isWifiEnabled = wifiManager?.isWifiEnabled ?: false
                val globalOn = try {
                    android.provider.Settings.Global.getInt(
                        (c ?: context).contentResolver,
                        android.provider.Settings.Global.MOBILE_DATA,
                        0
                    ) == 1
                } catch (e: Exception) { false }
                isDataEnabled = globalOn || (telephonyManager?.isDataEnabled ?: false)
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction(android.net.wifi.WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(android.net.wifi.WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(android.net.ConnectivityManager.CONNECTIVITY_ACTION)
            addAction("android.intent.action.ANY_DATA_STATE")
            addAction(android.telephony.TelephonyManager.ACTION_MULTI_SIM_CONFIG_CHANGED)
        }
        context.registerReceiver(receiver, filter, android.content.Context.RECEIVER_EXPORTED_UNAUDITED)
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {}
        }
    }

    val navBarStart = WindowInsets.navigationBars.asPaddingValues().calculateStartPadding(androidx.compose.ui.unit.LayoutDirection.Ltr)
    val navBarEnd = WindowInsets.navigationBars.asPaddingValues().calculateEndPadding(androidx.compose.ui.unit.LayoutDirection.Ltr)

    val showTileLabels by produceState(
        initialValue = try {
            android.provider.Settings.System.getInt(
                context.contentResolver,
                "show_qs_tile_labels",
                0
            ) == 1
        } catch (e: Exception) { false },
        key1 = context
    ) {
        val uri = android.provider.Settings.System.getUriFor("show_qs_tile_labels")
        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                value = try {
                    android.provider.Settings.System.getInt(
                        context.contentResolver,
                        "show_qs_tile_labels",
                        0
                    ) == 1
                } catch (e: Exception) { false }
            }
        }
        try {
            context.contentResolver.registerContentObserver(uri, false, observer)
        } catch (e: Exception) {}
        awaitDispose {
            try {
                context.contentResolver.unregisterContentObserver(observer)
            } catch (e: Exception) {}
        }
    }

    var showMobileDataDialog by remember {
        mutableStateOf(false)
    }

    Box(modifier = modifier) {
        if (isLandscape) {
            // Authentic HyperOS Landscape 2-Column Layout (Matching Image 4)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = navBarStart + 16.dp, end = navBarEnd + 16.dp, top = 4.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                // LEFT COLUMN: Circular Quick Settings Tiles Grid (4 columns x 3 rows) + Editar button
                Column(
                    modifier = Modifier
                        .weight(1.05f)
                        .padding(horizontal = 6.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    HyperOSTilesGrid(
                        tiles = gridTiles,
                        view = view,
                        showLabels = showTileLabels,
                        modifier = Modifier.fillMaxWidth()
                    )

                    VerticalSeparator(12.dp)

                    HyperOSEditPill(view = view, qsContainerViewModel = qsContainerViewModel)

                    val buildNumberViewModel = rememberViewModel("QuickSettingsShadeOverlay.BuildNumber") {
                        buildNumberViewModelFactory.create()
                    }
                    if (buildNumberViewModel.buildNumber != null) {
                        VerticalSeparator(QuickSettingsShade.Dimensions.ShortPadding)
                        BuildNumber(
                            viewModel = buildNumberViewModel,
                            modifier = Modifier.align(Alignment.Start).padding(start = 14.dp),
                        )
                    }

                    VerticalSeparator(16.dp)
                }

                // RIGHT COLUMN: Top Dual Connectivity (Wi-Fi & Data) + Bottom (Media + Brightness + Volume)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 1. Top Dual Connectivity Cards (Wi-Fi and Mobile Data)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (internetTile != null) {
                            val tileState by internetTile.state.collectAsStateWithLifecycle(internetTile.currentState)
                            val isWifiActive = isWifiEnabled || (tileState.state == android.service.quicksettings.Tile.STATE_ACTIVE)
                            val wifiSubtitle = when {
                                !isWifiActive -> "Desactivado"
                                tileState.state == android.service.quicksettings.Tile.STATE_ACTIVE && !tileState.secondaryLabel.isNullOrBlank() -> tileState.secondaryLabel.toString()
                                !tileState.secondaryLabel.isNullOrBlank() -> tileState.secondaryLabel.toString()
                                else -> "Activado"
                            }
                            HyperOSConnectivityCard(
                                title = "Wi-Fi",
                                subtitle = wifiSubtitle,
                                icon = tileState.icon,
                                iconSupplier = tileState.iconSupplier,
                                fallbackIconRes = R.drawable.vd_wifi,
                                isActive = isWifiActive,
                                onClick = {
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                    val targetWifi = !isWifiActive
                                    isWifiEnabled = targetWifi
                                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                        try {
                                            val wm = wifiManager ?: context.applicationContext.getSystemService(android.net.wifi.WifiManager::class.java)
                                            wm?.setWifiEnabled(targetWifi)
                                        } catch (e: Exception) {
                                            android.util.Log.e("HyperOS", "Error toggling wifi", e)
                                        }
                                    }
                                },
                                onLongClick = {
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                                    internetTile.mainClick(internetTile.expandable)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (cellTile != null) {
                            val tileState by cellTile.state.collectAsStateWithLifecycle(cellTile.currentState)
                            val isCellActive = (tileState.state == android.service.quicksettings.Tile.STATE_ACTIVE) || isDataEnabled
                            val cellSubtitle = when {
                                !isCellActive -> "Desactivado"
                                !tileState.secondaryLabel.isNullOrBlank() -> tileState.secondaryLabel.toString()
                                else -> "Activado"
                            }
                            HyperOSConnectivityCard(
                                title = tileState.label?.toString() ?: "Datos móviles",
                                subtitle = cellSubtitle,
                                icon = tileState.icon,
                                iconSupplier = tileState.iconSupplier,
                                fallbackIconRes = R.drawable.ic_swap_vert,
                                isActive = isCellActive,
                                onClick = {
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                    val targetData = !isCellActive
                                    isDataEnabled = targetData
                                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                        try {
                                            val tm = telephonyManager ?: context.applicationContext.getSystemService(android.telephony.TelephonyManager::class.java)
                                            if (tm != null) {
                                                tm.setDataEnabledForReason(
                                                    android.telephony.TelephonyManager.DATA_ENABLED_REASON_USER,
                                                    targetData
                                                )
                                            }
                                        } catch (e: Exception) {
                                            try {
                                                val tm = telephonyManager ?: context.applicationContext.getSystemService(android.telephony.TelephonyManager::class.java)
                                                val subId = android.telephony.SubscriptionManager.getDefaultDataSubscriptionId()
                                                val subTm = tm?.createForSubscriptionId(subId)
                                                subTm?.setDataEnabledForReason(
                                                    android.telephony.TelephonyManager.DATA_ENABLED_REASON_USER,
                                                    targetData
                                                )
                                            } catch (e2: Exception) {
                                                android.util.Log.e("HyperOS", "Error toggling mobile data", e2)
                                            }
                                        }
                                    }
                                },
                                onLongClick = {
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                                    showMobileDataDialog = true
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    VerticalSeparator(10.dp)

                    // 2. Middle Row: HyperOS Media Card (Large) + Vertical Brightness & Volume Sliders (Slim)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HyperOSMediaCard(
                            viewModelFactory = qsContainerViewModel.mediaViewModelFactory,
                            view = view,
                            modifier = Modifier
                                .weight(2.2f)
                                .fillMaxHeight()
                        )

                        HyperOSVerticalBrightnessSlider(
                            brightnessSliderViewModel = qsContainerViewModel.brightnessSliderViewModel,
                            view = view,
                            modifier = Modifier
                                .weight(0.85f)
                                .fillMaxHeight()
                        )

                        HyperOSVerticalVolumeSlider(
                            volumeSliderViewModel = volumeSliderViewModel,
                            audioManager = audioManager,
                            view = view,
                            modifier = Modifier
                                .weight(0.85f)
                                .fillMaxHeight()
                        )
                    }
                }
            }
        } else {
            // Portrait Layout (Matching Image 3)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 1. Top Dual Connectivity Cards (Wi-Fi and Mobile Data)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (internetTile != null) {
                            val tileState by internetTile.state.collectAsStateWithLifecycle(internetTile.currentState)
                            val isWifiActive = isWifiEnabled || (tileState.state == android.service.quicksettings.Tile.STATE_ACTIVE)
                            val wifiSubtitle = when {
                                !isWifiActive -> "Desactivado"
                                tileState.state == android.service.quicksettings.Tile.STATE_ACTIVE && !tileState.secondaryLabel.isNullOrBlank() -> tileState.secondaryLabel.toString()
                                !tileState.secondaryLabel.isNullOrBlank() -> tileState.secondaryLabel.toString()
                                else -> "Activado"
                            }
                            HyperOSConnectivityCard(
                                title = "Wi-Fi",
                                subtitle = wifiSubtitle,
                                icon = tileState.icon,
                                iconSupplier = tileState.iconSupplier,
                                fallbackIconRes = R.drawable.vd_wifi,
                                isActive = isWifiActive,
                                onClick = {
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                    val targetWifi = !isWifiActive
                                    isWifiEnabled = targetWifi
                                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                        try {
                                            val wm = wifiManager ?: context.applicationContext.getSystemService(android.net.wifi.WifiManager::class.java)
                                            wm?.setWifiEnabled(targetWifi)
                                        } catch (e: Exception) {
                                            android.util.Log.e("HyperOS", "Error toggling wifi", e)
                                        }
                                    }
                                },
                                onLongClick = {
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                                    internetTile.mainClick(internetTile.expandable)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (cellTile != null) {
                            val tileState by cellTile.state.collectAsStateWithLifecycle(cellTile.currentState)
                            val isCellActive = (tileState.state == android.service.quicksettings.Tile.STATE_ACTIVE) || isDataEnabled
                            val cellSubtitle = when {
                                !isCellActive -> "Desactivado"
                                !tileState.secondaryLabel.isNullOrBlank() -> tileState.secondaryLabel.toString()
                                else -> "Activado"
                            }
                            HyperOSConnectivityCard(
                                title = tileState.label?.toString() ?: "Datos móviles",
                                subtitle = cellSubtitle,
                                icon = tileState.icon,
                                iconSupplier = tileState.iconSupplier,
                                fallbackIconRes = R.drawable.ic_swap_vert,
                                isActive = isCellActive,
                                onClick = {
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                    val targetData = !isCellActive
                                    isDataEnabled = targetData
                                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                        try {
                                            val tm = telephonyManager ?: context.applicationContext.getSystemService(android.telephony.TelephonyManager::class.java)
                                            if (tm != null) {
                                                tm.setDataEnabledForReason(
                                                    android.telephony.TelephonyManager.DATA_ENABLED_REASON_USER,
                                                    targetData
                                                )
                                            }
                                        } catch (e: Exception) {
                                            try {
                                                val tm = telephonyManager ?: context.applicationContext.getSystemService(android.telephony.TelephonyManager::class.java)
                                                val subId = android.telephony.SubscriptionManager.getDefaultDataSubscriptionId()
                                                val subTm = tm?.createForSubscriptionId(subId)
                                                subTm?.setDataEnabledForReason(
                                                    android.telephony.TelephonyManager.DATA_ENABLED_REASON_USER,
                                                    targetData
                                                )
                                            } catch (e2: Exception) {
                                                android.util.Log.e("HyperOS", "Error toggling mobile data", e2)
                                            }
                                        }
                                    }
                                },
                                onLongClick = {
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                                    showMobileDataDialog = true
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    VerticalSeparator(10.dp)

                    // 2. Middle Row: HyperOS Large Media Card + Vertical Brightness Slider + Vertical Volume Slider
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(185.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HyperOSMediaCard(
                            viewModelFactory = qsContainerViewModel.mediaViewModelFactory,
                            view = view,
                            modifier = Modifier
                                .weight(2f)
                                .fillMaxHeight()
                        )

                        HyperOSVerticalBrightnessSlider(
                            brightnessSliderViewModel = qsContainerViewModel.brightnessSliderViewModel,
                            view = view,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )

                        HyperOSVerticalVolumeSlider(
                            volumeSliderViewModel = volumeSliderViewModel,
                            audioManager = audioManager,
                            view = view,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }

                    VerticalSeparator(14.dp)

                    // 3. Circular Quick Settings Tiles Grid (12 buttons / 4 columns)
                    HyperOSTilesGrid(
                        tiles = gridTiles,
                        view = view,
                        showLabels = showTileLabels,
                        modifier = Modifier.fillMaxWidth()
                    )

                    VerticalSeparator(14.dp)

                    // 4. Centered "Editar" Pill Button
                    HyperOSEditPill(view = view, qsContainerViewModel = qsContainerViewModel)

                    val buildNumberViewModel = rememberViewModel("QuickSettingsShadeOverlay.BuildNumber") {
                        buildNumberViewModelFactory.create()
                    }
                    if (buildNumberViewModel.buildNumber != null) {
                        VerticalSeparator(QuickSettingsShade.Dimensions.ShortPadding)
                        BuildNumber(
                            viewModel = buildNumberViewModel,
                            modifier = Modifier.align(Alignment.Start).padding(start = 14.dp),
                        )
                    }

                    VerticalSeparator(16.dp)
                }
            }
        }

        if (showMobileDataDialog) {
            BackHandler(enabled = true) {
                showMobileDataDialog = false
            }
            HyperOSMobileDataSelectorDialog(
                isDataEnabled = isDataEnabled,
                onToggleData = { targetData ->
                    isDataEnabled = targetData
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        try {
                            val tm = telephonyManager ?: context.applicationContext.getSystemService(android.telephony.TelephonyManager::class.java)
                            val currentSubId = android.telephony.SubscriptionManager.getDefaultDataSubscriptionId()
                            val subTm = tm?.createForSubscriptionId(currentSubId) ?: tm
                            subTm?.setDataEnabledForReason(
                                android.telephony.TelephonyManager.DATA_ENABLED_REASON_USER,
                                targetData
                            )
                        } catch (e: Exception) {
                            android.util.Log.e("HyperOS", "Error toggling mobile data from dialog", e)
                        }
                    }
                },
                onDismiss = { showMobileDataDialog = false },
                view = view
            )
        }
    }
}

@Composable
private fun HyperOSMobileDataSelectorDialog(
    isDataEnabled: Boolean,
    onToggleData: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    view: android.view.View,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val subscriptionManager = remember(context) {
        context.getSystemService(android.telephony.SubscriptionManager::class.java)
    }
    val telephonyManager = remember(context) {
        context.getSystemService(android.telephony.TelephonyManager::class.java)
    }

    var activeSubs by remember {
        mutableStateOf(
            try {
                subscriptionManager?.activeSubscriptionInfoList ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        )
    }
    var defaultDataSubId by remember {
        mutableStateOf(android.telephony.SubscriptionManager.getDefaultDataSubscriptionId())
    }

    var localDataEnabled by remember {
        mutableStateOf(
            try {
                val globalSetting = android.provider.Settings.Global.getInt(
                    context.contentResolver,
                    android.provider.Settings.Global.MOBILE_DATA,
                    if (isDataEnabled) 1 else 0
                ) == 1
                globalSetting || isDataEnabled
            } catch (e: Exception) {
                isDataEnabled
            }
        )
    }

    DisposableEffect(context, subscriptionManager) {
        val listener = object : android.telephony.SubscriptionManager.OnSubscriptionsChangedListener() {
            override fun onSubscriptionsChanged() {
                try {
                    activeSubs = subscriptionManager?.activeSubscriptionInfoList ?: emptyList()
                    defaultDataSubId = android.telephony.SubscriptionManager.getDefaultDataSubscriptionId()
                } catch (e: Exception) {}
            }
        }
        subscriptionManager?.addOnSubscriptionsChangedListener(context.mainExecutor, listener)
        onDispose {
            try {
                subscriptionManager?.removeOnSubscriptionsChangedListener(listener)
            } catch (e: Exception) {}
        }
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth(if (isLandscape) 0.52f else 0.92f)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF222327))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* Consume touch inside dialog */ }
                )
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: "Datos móviles" + Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.hyperos_qs_mobile_data),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 20.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = Color.White
                    )
                )

                HyperOSSwitch(
                    checked = localDataEnabled,
                    onCheckedChange = { checked ->
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        localDataEnabled = checked
                        onToggleData(checked)
                    }
                )
            }

            // SIM Cards List (Material Expressive 3 Cards)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (activeSubs.isEmpty()) {
                    HyperOSSimCardItem(
                        slotIndex = 0,
                        carrierName = "SIM 1",
                        phoneNumber = null,
                        isSelected = true,
                        onClick = {}
                    )
                } else {
                    activeSubs.forEach { sub ->
                        val isSelected = (sub.subscriptionId == defaultDataSubId)
                        val carrierName = sub.carrierName?.toString()?.takeIf { it.isNotBlank() }
                            ?: sub.displayName?.toString()?.takeIf { it.isNotBlank() }
                            ?: "SIM ${sub.simSlotIndex + 1}"
                        val number = sub.number?.takeIf { it.isNotBlank() }

                        HyperOSSimCardItem(
                            slotIndex = sub.simSlotIndex,
                            carrierName = carrierName,
                            phoneNumber = number,
                            isSelected = isSelected,
                            onClick = {
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                if (!isSelected) {
                                    val wasDataEnabled = localDataEnabled
                                    defaultDataSubId = sub.subscriptionId
                                    localDataEnabled = wasDataEnabled
                                    onToggleData(wasDataEnabled)
                                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                        try {
                                            subscriptionManager?.setDefaultDataSubId(sub.subscriptionId)
                                            try {
                                                android.provider.Settings.Global.putInt(
                                                    context.contentResolver,
                                                    "user_preferred_data_sub",
                                                    sub.subscriptionId
                                                )
                                                android.provider.Settings.Global.putInt(
                                                    context.contentResolver,
                                                    android.provider.Settings.Global.MOBILE_DATA,
                                                    if (wasDataEnabled) 1 else 0
                                                )
                                            } catch (e: Exception) {}

                                            if (wasDataEnabled) {
                                                telephonyManager?.createForSubscriptionId(sub.subscriptionId)?.setDataEnabledForReason(
                                                    android.telephony.TelephonyManager.DATA_ENABLED_REASON_USER,
                                                    true
                                                )
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("HyperOS", "Error setting default data sub", e)
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // "Más ajustes" Material 3 Tonal Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF33353A))
                    .clickable {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        try {
                            val intent = Intent(android.provider.Settings.ACTION_NETWORK_OPERATOR_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            try {
                                 val intent = Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } catch (e2: Exception) {}
                        }
                        onDismiss()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.hyperos_qs_more_settings),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        color = Color.White
                    )
                )
            }
        }
    }
}

@Composable
private fun HyperOSSimCardItem(
    slotIndex: Int,
    carrierName: String,
    phoneNumber: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor = if (isSelected) Color(0xFFFFFFFF) else Color(0xFF2E3035)
    val titleColor = if (isSelected) Color(0xFF1C1B1F) else Color(0xFFE6E1E5)
    val subtitleColor = if (isSelected) Color(0xFF49454F) else Color(0xFF938F99)

    // SIM 1 is Green (#00C853), SIM 2 is Blue (#2979FF)
    val badgeBgColor = when (slotIndex) {
        0 -> Color(0xFF00C853) // SIM 1: Green
        1 -> Color(0xFF2979FF) // SIM 2: Blue
        else -> Color(0xFFFF9100) // SIM 3+: Amber
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            // SIM Slot Badge (Icon with cut-corner SIM card shape + slot number)
            Box(
                modifier = Modifier
                    .size(26.dp, 30.dp)
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 9.dp, bottomStart = 5.dp, bottomEnd = 5.dp))
                    .background(badgeBgColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${slotIndex + 1}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 13.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = Color.White
                    )
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = carrierName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 16.sp,
                        fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.SemiBold,
                        color = titleColor
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!phoneNumber.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = phoneNumber,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            color = subtitleColor
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2979FF)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }
    }
}

@Composable
private fun HyperOSSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) Color(0xFF007AFF) else Color(0xFF48484A),
        animationSpec = tween(durationMillis = 200)
    )
    val thumbOffset by animateFloatAsState(
        targetValue = if (checked) 22f else 2f,
        animationSpec = tween(durationMillis = 200)
    )

    Box(
        modifier = Modifier
            .size(50.dp, 30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(trackColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCheckedChange(!checked) }
            )
            .padding(vertical = 2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset.dp)
                .size(26.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

@Composable
private fun HyperOSMediaCard(
    viewModelFactory: com.android.systemui.media.remedia.ui.viewmodel.MediaViewModel.Factory,
    view: android.view.View,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val mediaViewModel = rememberViewModel("HyperOSMediaCard") {
        viewModelFactory.create(context, MediaCarouselVisibility.WhenNotEmpty)
    }
    val cards = mediaViewModel.cards
    val currentCard = cards.firstOrNull()

    val activeController = remember(currentCard?.key) { getActiveMediaController(context, null) }
    val cardPkg = remember(currentCard?.key, activeController) {
        val keyStr = (currentCard?.key as? String) ?: ""
        when {
            keyStr.contains(":") -> keyStr.substringBefore(":")
            keyStr.contains(".") -> keyStr
            activeController != null -> activeController.packageName
            else -> ""
        }
    }

    var cachedImageBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var cachedAppIconBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var lastCoverKey by remember { mutableStateOf<String?>(null) }
    var lastIconPkg by remember { mutableStateOf<String?>(null) }

    // Update artwork only when track or background source actually changes, preventing constant recomposition crossfades
    LaunchedEffect(currentCard?.background, currentCard?.title, currentCard?.subtitle, cardPkg) {
        val currentTrackKey = "${currentCard?.key ?: ""}_${currentCard?.title ?: ""}_${currentCard?.subtitle ?: ""}_${cardPkg}"
        if (currentTrackKey != lastCoverKey || cachedImageBitmap == null) {
            val loadedBmp = (currentCard?.background as? com.android.systemui.common.shared.model.Icon.Loaded)?.let { loaded ->
                val drawable = loaded.drawable
                if (drawable is android.graphics.drawable.BitmapDrawable) {
                    drawable.bitmap
                } else null
            }
            val bmp = loadedBmp ?: try {
                val controller = getActiveMediaController(context, cardPkg.ifBlank { null })
                val meta = controller?.metadata
                meta?.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: meta?.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART)
            } catch (e: Exception) {
                null
            }

            if (bmp != null) {
                cachedImageBitmap = bmp.asImageBitmap()
                lastCoverKey = currentTrackKey
            } else if (currentCard?.background == null) {
                cachedImageBitmap = null
                lastCoverKey = currentTrackKey
            }
        }
    }

    // Load application icon once per target package
    LaunchedEffect(cardPkg) {
        if (cardPkg.isNotBlank() && cardPkg != lastIconPkg) {
            try {
                val drawable = context.packageManager.getApplicationIcon(cardPkg)
                val bmp = android.graphics.Bitmap.createBitmap(
                    drawable.intrinsicWidth.coerceAtLeast(1),
                    drawable.intrinsicHeight.coerceAtLeast(1),
                    android.graphics.Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                cachedAppIconBitmap = bmp.asImageBitmap()
                lastIconPkg = cardPkg
            } catch (e: Exception) {
                cachedAppIconBitmap = null
            }
        }
    }

    val cardBg = Color(0x597F7F7F)
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        ),
        label = "HyperOSMediaCardScale"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(24.dp))
            .background(cardBg)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                openMediaApp(context, view, currentCard, cardPkg)
            }
    ) {
        // Smooth background crossfade between album arts only when artwork actually changes
        Crossfade(
            targetState = cachedImageBitmap,
            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
            label = "HyperOSMediaBackgroundCrossfade"
        ) { bmp ->
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.45f),
                                        Color.Black.copy(alpha = 0.75f)
                                    )
                                )
                            )
                        }
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Row: App / Album thumbnail + Cast / Output Switcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App / Album Thumbnail with stable transition
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x33FFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Crossfade(
                        targetState = cachedImageBitmap ?: cachedAppIconBitmap,
                        animationSpec = tween(durationMillis = 300),
                        label = "HyperOSMediaThumbCrossfade"
                    ) { thumb ->
                        if (thumb != null) {
                            Image(
                                bitmap = thumb,
                                contentDescription = "Portada del álbum",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (currentCard != null) {
                            SmallTileContent(
                                iconProvider = { currentCard.icon },
                                color = Color.White,
                                size = { 24.dp },
                                modifier = Modifier
                            )
                        } else {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_music_note),
                                contentDescription = "Música",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Output switcher / Cast Button
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x22FFFFFF))
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            if (currentCard != null) {
                                try {
                                    currentCard.outputSwitcherChipButton.onClick?.invoke()
                                } catch (e: Exception) {}
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_cast),
                        contentDescription = "Compartir audio",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }


            // Middle: Title and Subtitle with smooth slide-and-fade song transitions
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val emptyTitle = stringResource(R.string.hyperos_qs_media_empty_title)
                val emptySubtitle = stringResource(R.string.hyperos_qs_media_empty_subtitle)
                val titleText = if (currentCard != null && currentCard.title.isNotBlank()) {
                    currentCard.title
                } else {
                    emptyTitle
                }
                val subtitleText = if (currentCard != null && currentCard.subtitle.isNotBlank()) {
                    currentCard.subtitle
                } else {
                    emptySubtitle
                }

                AnimatedContent(
                    targetState = titleText,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(350)) + slideInVertically(animationSpec = tween(350)) { it / 2 })
                            .togetherWith(fadeOut(animationSpec = tween(250)) + slideOutVertically(animationSpec = tween(250)) { -it / 2 })
                    },
                    label = "HyperOSMediaTitleTransition"
                ) { targetTitle ->
                    Text(
                        text = targetTitle,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        ),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }

                AnimatedContent(
                    targetState = subtitleText,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(350, delayMillis = 60)) + slideInVertically(animationSpec = tween(350)) { it / 2 })
                            .togetherWith(fadeOut(animationSpec = tween(250)) + slideOutVertically(animationSpec = tween(250)) { -it / 2 })
                    },
                    label = "HyperOSMediaSubtitleTransition"
                ) { targetSubtitle ->
                    Text(
                        text = targetSubtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.75f)
                        ),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }

            // Bottom Row: Previous, Play/Pause, Next Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous Button
                val prevInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val isPrevPressed by prevInteraction.collectIsPressedAsState()
                val prevScale by animateFloatAsState(targetValue = if (isPrevPressed) 0.82f else 1.0f, label = "PrevScale")

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .graphicsLayer { scaleX = prevScale; scaleY = prevScale }
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(
                            interactionSource = prevInteraction,
                            indication = null
                        ) {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            controlMediaSkipPrev(
                                context = context,
                                cardPackage = cardPkg
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_media_previous),
                        contentDescription = "Anterior",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Play / Pause Button
                val isPlaying = currentCard?.playPauseAction?.state != com.android.systemui.media.remedia.shared.model.MediaSessionState.Paused
                val playInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val isPlayPressed by playInteraction.collectIsPressedAsState()
                val playScale by animateFloatAsState(targetValue = if (isPlayPressed) 0.85f else 1.0f, label = "PlayScale")

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .graphicsLayer { scaleX = playScale; scaleY = playScale }
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(
                            interactionSource = playInteraction,
                            indication = null
                        ) {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            controlMediaPlayPause(
                                context = context,
                                cardPackage = cardPkg
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = isPlaying,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(200)).togetherWith(fadeOut(animationSpec = tween(200)))
                        },
                        label = "HyperOSMediaPlayPauseTransition"
                    ) { playing ->
                        val iconRes = if (currentCard != null && playing) {
                            R.drawable.ic_media_pause
                        } else {
                            R.drawable.ic_media_play
                        }
                        Icon(
                            painter = painterResource(id = iconRes),
                            contentDescription = if (playing) "Pausar" else "Reproducir",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                // Next Button
                val nextInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val isNextPressed by nextInteraction.collectIsPressedAsState()
                val nextScale by animateFloatAsState(targetValue = if (isNextPressed) 0.82f else 1.0f, label = "NextScale")

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .graphicsLayer { scaleX = nextScale; scaleY = nextScale }
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(
                            interactionSource = nextInteraction,
                            indication = null
                        ) {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            controlMediaSkipNext(
                                context = context,
                                cardPackage = cardPkg
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_media_next),
                        contentDescription = "Siguiente",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

private fun openMediaApp(
    context: android.content.Context,
    view: android.view.View,
    currentCard: com.android.systemui.media.remedia.ui.viewmodel.MediaCardViewModel?,
    cardPkg: String,
) {
    val activityStarter = try {
        com.android.systemui.Dependency.get(com.android.systemui.plugins.ActivityStarter::class.java)
    } catch (e: Exception) {
        null
    }

    val activeController = getActiveMediaController(context, cardPkg.ifBlank { null })
    val targetPkg = when {
        cardPkg.isNotBlank() -> cardPkg
        activeController?.packageName?.isNotBlank() == true -> activeController.packageName
        else -> ""
    }

    // 1. Try launching through MediaCardViewModel's onClick (handles keyguard/shade dismiss & CUJ animation)
    if (currentCard != null) {
        try {
            currentCard.onClick.invoke(com.android.systemui.animation.Expandable(mutableSetOf()))
            return
        } catch (e: Exception) {
            android.util.Log.e("HyperOSMediaCard", "Error triggering currentCard.onClick", e)
        }
    }

    // 2. Try launching through MediaController sessionActivity
    if (activeController?.sessionActivity != null) {
        try {
            if (activityStarter != null) {
                activityStarter.postStartActivityDismissingKeyguard(activeController.sessionActivity)
                return
            } else {
                val opts = android.app.ActivityOptions.makeBasic().apply {
                    pendingIntentBackgroundActivityStartMode =
                        android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                }
                activeController.sessionActivity?.send(context, 0, null, null, null, null, opts.toBundle())
                return
            }
        } catch (e: Exception) {
            android.util.Log.e("HyperOSMediaCard", "Error sending sessionActivity", e)
        }
    }

    // 3. Try launching package launch intent
    if (targetPkg.isNotBlank()) {
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPkg)?.apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            if (launchIntent != null) {
                if (activityStarter != null) {
                    activityStarter.startActivity(launchIntent, true /* dismissShade */)
                } else {
                    context.startActivity(launchIntent)
                }
                return
            }
        } catch (e: Exception) {
            android.util.Log.e("HyperOSMediaCard", "Error launching target package $targetPkg", e)
        }
    }

    // 4. Default fallback: open music category intent
    try {
        val musicIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_APP_MUSIC)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (activityStarter != null) {
            activityStarter.startActivity(musicIntent, true)
        } else {
            context.startActivity(musicIntent)
        }
    } catch (e: Exception) {
        android.util.Log.e("HyperOSMediaCard", "Error launching default music intent", e)
    }
}

private fun getActiveMediaController(context: android.content.Context, cardPackage: String? = null): android.media.session.MediaController? {
    val msm = context.getSystemService(android.content.Context.MEDIA_SESSION_SERVICE) as? android.media.session.MediaSessionManager
    val controllers = try {
        msm?.getActiveSessions(null) ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
    if (!cardPackage.isNullOrBlank()) {
        val matching = controllers.firstOrNull { it.packageName == cardPackage }
        if (matching != null) return matching
    }
    return controllers.firstOrNull { it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING }
        ?: controllers.firstOrNull()
}

private fun controlMediaPlayPause(context: android.content.Context, cardPackage: String? = null) {
    val controller = getActiveMediaController(context, cardPackage)
    if (controller != null) {
        val state = controller.playbackState?.state
        if (state == android.media.session.PlaybackState.STATE_PLAYING) {
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
    sendMediaKeyFallback(context, controller, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
}

private fun controlMediaSkipNext(context: android.content.Context, cardPackage: String? = null) {
    val controller = getActiveMediaController(context, cardPackage)
    if (controller != null) {
        try {
            controller.transportControls.skipToNext()
            return
        } catch (e: Exception) {}
    }
    sendMediaKeyFallback(context, controller, android.view.KeyEvent.KEYCODE_MEDIA_NEXT)
}

private fun controlMediaSkipPrev(context: android.content.Context, cardPackage: String? = null) {
    val controller = getActiveMediaController(context, cardPackage)
    if (controller != null) {
        try {
            controller.transportControls.skipToPrevious()
            return
        } catch (e: Exception) {}
    }
    sendMediaKeyFallback(context, controller, android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)
}

private fun sendMediaKeyFallback(context: android.content.Context, controller: android.media.session.MediaController?, keyCode: Int) {
    if (controller != null) {
        try {
            val down = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode)
            val up = android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode)
            controller.dispatchMediaButtonEvent(down)
            controller.dispatchMediaButtonEvent(up)
            return
        } catch (e: Exception) {}
    }
    try {
        val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
        val eventDown = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode)
        val eventUp = android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode)
        audioManager?.dispatchMediaKeyEvent(eventDown)
        audioManager?.dispatchMediaKeyEvent(eventUp)
    } catch (e: Exception) {}
}

@Composable
private fun HyperOSEditPill(
    view: android.view.View,
    qsContainerViewModel: QuickSettingsContainerViewModel,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val privacyController = remember {
        try { Dependency.get(com.android.systemui.privacy.PrivacyItemController::class.java) } catch (e: Exception) { null }
    }
    var privacyItems by remember { mutableStateOf(privacyController?.privacyList ?: emptyList()) }

    androidx.compose.runtime.DisposableEffect(privacyController) {
        if (privacyController == null) return@DisposableEffect onDispose {}
        val callback = object : com.android.systemui.privacy.PrivacyItemController.Callback {
            override fun onPrivacyItemsChanged(items: List<com.android.systemui.privacy.PrivacyItem>) {
                privacyItems = items
            }
        }
        privacyController.addCallback(callback)
        privacyItems = privacyController.privacyList
        onDispose {
            privacyController.removeCallback(callback)
        }
    }

    val activePrivacyItems = remember(privacyItems) {
        privacyItems.filter { !it.paused && (
            it.privacyType == com.android.systemui.privacy.PrivacyType.TYPE_CAMERA ||
            it.privacyType == com.android.systemui.privacy.PrivacyType.TYPE_MICROPHONE ||
            it.privacyType == com.android.systemui.privacy.PrivacyType.TYPE_LOCATION
        )}
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (activePrivacyItems.isNotEmpty()) {
            val hasMic = activePrivacyItems.any { it.privacyType == com.android.systemui.privacy.PrivacyType.TYPE_MICROPHONE }
            val hasCam = activePrivacyItems.any { it.privacyType == com.android.systemui.privacy.PrivacyType.TYPE_CAMERA }
            val hasLocation = activePrivacyItems.any { it.privacyType == com.android.systemui.privacy.PrivacyType.TYPE_LOCATION }

            val primaryItem = activePrivacyItems.first()
            val pm = context.packageManager
            val appInfo = remember(primaryItem.application.packageName) {
                try { pm.getApplicationInfo(primaryItem.application.packageName, 0) } catch (e: Exception) { null }
            }
            val appName = remember(appInfo, primaryItem) {
                appInfo?.let { pm.getApplicationLabel(it).toString() } ?: primaryItem.application.packageName
            }
            val appIconDrawable = remember(appInfo) {
                appInfo?.let { pm.getApplicationIcon(it) }
            }
            val appIconBitmap = remember(appIconDrawable) {
                appIconDrawable?.let { d ->
                    try { d.toBitmap(48, 48).asImageBitmap() } catch (e: Exception) { null }
                }
            }

            fun getPrivacyCustomColor(settingKey: String, defaultColor: Color): Color {
                val hex = try {
                    android.provider.Settings.System.getString(context.contentResolver, settingKey)?.trim()
                } catch (e: Exception) {
                    null
                }
                if (!hex.isNullOrEmpty()) {
                    try {
                        val formatted = if (hex.startsWith("#")) hex else "#$hex"
                        return Color(android.graphics.Color.parseColor(formatted))
                    } catch (e: Exception) {}
                }
                return defaultColor
            }

            val accentColor = when {
                hasCam && hasMic && hasLocation -> getPrivacyCustomColor("status_bar_chips_color_all", Color(0xFFFFB300))
                (hasCam || hasMic) && hasLocation -> getPrivacyCustomColor("status_bar_chips_color_combo", Color(0xFF00F5D4))
                hasLocation && !hasCam && !hasMic -> getPrivacyCustomColor("status_bar_chips_color_location", Color(0xFF0091EA))
                else -> getPrivacyCustomColor("status_bar_chips_color_cam_mic", Color(0xFF00E676))
            }
            val bgColor = accentColor.copy(alpha = 0.20f)

            val sensorIcons = when {
                hasCam && hasMic && hasLocation -> "📷 🎙️ 📍"
                hasCam && hasMic -> "📷 🎙️"
                hasCam && hasLocation -> "📷 📍"
                hasMic && hasLocation -> "🎙️ 📍"
                hasCam -> "📷"
                hasMic -> "🎙️"
                hasLocation -> "📍"
                else -> ""
            }

            val privacyInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val isPrivacyPressed by privacyInteractionSource.collectIsPressedAsState()
            val privacyScale by animateFloatAsState(
                targetValue = if (isPrivacyPressed) 0.92f else 1.0f,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                ),
                label = "PrivacyPillScale"
            )

            Row(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = privacyScale
                        scaleY = privacyScale
                    }
                    .clip(RoundedCornerShape(20.dp))
                    .background(bgColor)
                    .clickable(
                        interactionSource = privacyInteractionSource,
                        indication = null
                    ) {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        try {
                            Dependency.get(com.android.systemui.privacy.PrivacyDialogControllerV2::class.java).showDialog(context)
                        } catch (e: Exception) {
                            try {
                                Dependency.get(com.android.systemui.privacy.PrivacyDialogController::class.java).showDialog(context)
                            } catch (e2: Exception) {}
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (appIconBitmap != null) {
                    Image(
                        bitmap = appIconBitmap,
                        contentDescription = appName,
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                    )
                }
                Text(
                    text = sensorIcons,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.width(10.dp))
        }

        val editInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        val isEditPressed by editInteractionSource.collectIsPressedAsState()
        val editScale by animateFloatAsState(
            targetValue = if (isEditPressed) 0.90f else 1.0f,
            animationSpec = androidx.compose.animation.core.spring(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                stiffness = androidx.compose.animation.core.Spring.StiffnessLow
            ),
            label = "EditPillScale"
        )

        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = editScale
                    scaleY = editScale
                }
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0x597F7F7F))
                .clickable(
                    interactionSource = editInteractionSource,
                    indication = null
                ) {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    qsContainerViewModel.editModeViewModel.startEditing()
                }
                .padding(horizontal = 24.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.hyperos_qs_edit),
                style = MaterialTheme.typography.labelMedium.copy(
                    color = Color.White,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    fontSize = 13.sp
                )
            )
        }
    }
}

@Composable
private fun HyperOSVerticalBrightnessSlider(
    brightnessSliderViewModel: BrightnessSliderViewModel,
    view: android.view.View,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val gamma = brightnessSliderViewModel.currentBrightness
    val minGamma = brightnessSliderViewModel.minBrightness
    val maxGamma = brightnessSliderViewModel.maxBrightness
    val fraction = remember(gamma, minGamma, maxGamma) {
        if (gamma.value >= 0 && maxGamma.value > minGamma.value) {
            ((gamma.value - minGamma.value).toFloat() / (maxGamma.value - minGamma.value).toFloat()).coerceIn(0f, 1f)
        } else 0.5f
    }
    var dragFraction: Float by remember { mutableFloatStateOf(-1f) }
    var isInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(gamma.value) {
        if (gamma.value >= 0) {
            isInitialized = true
        }
    }

    val animatedFraction by animateFloatAsState(
        targetValue = if (dragFraction >= 0f) dragFraction else fraction,
        animationSpec = if (!isInitialized || dragFraction >= 0f) {
            androidx.compose.animation.core.snap()
        } else {
            androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)
        },
        label = "HyperOSBrightnessFraction"
    )

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0x593A3A3A))
            .pointerInput(minGamma, maxGamma) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        val height = size.height.toFloat()
                        val calculatedFraction = (1f - (offset.y / height)).coerceIn(0f, 1f)
                        dragFraction = calculatedFraction
                        val targetGamma = GammaBrightness((minGamma.value + calculatedFraction * (maxGamma.value - minGamma.value)).roundToInt())
                        coroutineScope.launch {
                            brightnessSliderViewModel.onDrag(Drag.Dragging(targetGamma))
                        }
                    },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        val height = size.height.toFloat()
                        val calculatedFraction = (1f - (change.position.y / height)).coerceIn(0f, 1f)
                        dragFraction = calculatedFraction
                        val targetGamma = GammaBrightness((minGamma.value + calculatedFraction * (maxGamma.value - minGamma.value)).roundToInt())
                        coroutineScope.launch {
                            brightnessSliderViewModel.onDrag(Drag.Dragging(targetGamma))
                        }
                    },
                    onDragEnd = {
                        val currentDrag = dragFraction
                        dragFraction = -1f
                        val targetGamma = if (currentDrag >= 0f) {
                            GammaBrightness((minGamma.value + currentDrag * (maxGamma.value - minGamma.value)).roundToInt())
                        } else gamma
                        coroutineScope.launch {
                            brightnessSliderViewModel.onDrag(Drag.Stopped(targetGamma))
                        }
                    }
                )
            }
    ) {
        val fillColor = MaterialTheme.colorScheme.primary

        // Active filled level (from bottom to top)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(animatedFraction.coerceIn(0f, 1f))
                .background(fillColor)
        )

        // Sun Icon at bottom
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp)
                .size(32.dp),
            contentAlignment = Alignment.Center
        ) {
            val iconColor = if (animatedFraction > 0.15f) MaterialTheme.colorScheme.onPrimary else Color.White
            Icon(
                painter = painterResource(id = R.drawable.ic_brightness_medium),
                contentDescription = "Brillo",
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun HyperOSVerticalVolumeSlider(
    volumeSliderViewModel: AudioStreamSliderViewModel?,
    audioManager: android.media.AudioManager,
    view: android.view.View,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sliderState by volumeSliderViewModel?.slider?.collectAsStateWithLifecycle(null) ?: remember { mutableStateOf(null) }

    val maxVolume = remember {
        try { audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
        catch (e: Exception) { 15 }
    }
    val minVolume = remember {
        try { audioManager.getStreamMinVolume(android.media.AudioManager.STREAM_MUSIC) }
        catch (e: Exception) { 0 }
    }
    var currentVolume by remember {
        mutableStateOf(
            try { audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) }
            catch (e: Exception) { maxVolume / 2 }
        )
    }

    // Live receiver for physical volume buttons events
    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, intent: android.content.Intent?) {
                try {
                    currentVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                } catch (e: Exception) {}
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction("android.media.VOLUME_CHANGED_ACTION")
            addAction(android.media.AudioManager.STREAM_MUTE_CHANGED_ACTION)
            addAction(android.media.AudioManager.RINGER_MODE_CHANGED_ACTION)
        }
        context.registerReceiver(receiver, filter, android.content.Context.RECEIVER_EXPORTED_UNAUDITED)
        onDispose {
            try { context.unregisterReceiver(receiver) } catch (e: Exception) {}
        }
    }

    val stateFraction = if (sliderState != null) {
        val range = sliderState!!.valueRange.endInclusive - sliderState!!.valueRange.start
        if (range > 0f) {
            ((sliderState!!.value - sliderState!!.valueRange.start) / range).coerceIn(0f, 1f)
        } else 0f
    } else {
        ((currentVolume - minVolume).toFloat() / (maxVolume - minVolume).toFloat()).coerceIn(0f, 1f)
    }

    var dragFraction: Float by remember { mutableFloatStateOf(-1f) }
    val effectiveFraction = if (dragFraction >= 0f) dragFraction else stateFraction

    val animatedFraction by animateFloatAsState(
        targetValue = effectiveFraction,
        animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
        label = "HyperOSVolumeFraction"
    )

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0x593A3A3A))
            .pointerInput(minVolume, maxVolume, sliderState) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        val height = size.height.toFloat()
                        val calculatedFraction = (1f - (offset.y / height)).coerceIn(0f, 1f)
                        dragFraction = calculatedFraction
                        if (sliderState != null && volumeSliderViewModel != null) {
                            val targetVal = sliderState!!.valueRange.start + calculatedFraction * (sliderState!!.valueRange.endInclusive - sliderState!!.valueRange.start)
                            volumeSliderViewModel.onValueChanged(sliderState!!, targetVal)
                        } else {
                            val targetVol = (minVolume + calculatedFraction * (maxVolume - minVolume)).roundToInt()
                            if (targetVol != currentVolume) {
                                currentVolume = targetVol
                                try { audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, targetVol, 0) } catch (e: Exception) {}
                            }
                        }
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.SEGMENT_TICK)
                    },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        val height = size.height.toFloat()
                        val calculatedFraction = (1f - (change.position.y / height)).coerceIn(0f, 1f)
                        dragFraction = calculatedFraction
                        if (sliderState != null && volumeSliderViewModel != null) {
                            val targetVal = sliderState!!.valueRange.start + calculatedFraction * (sliderState!!.valueRange.endInclusive - sliderState!!.valueRange.start)
                            volumeSliderViewModel.onValueChanged(sliderState!!, targetVal)
                        } else {
                            val targetVol = (minVolume + calculatedFraction * (maxVolume - minVolume)).roundToInt()
                            if (targetVol != currentVolume) {
                                currentVolume = targetVol
                                try { audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, targetVol, 0) } catch (e: Exception) {}
                            }
                        }
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.SEGMENT_TICK)
                    },
                    onDragEnd = {
                        dragFraction = -1f
                        volumeSliderViewModel?.onValueChangeFinished()
                    },
                    onDragCancel = {
                        dragFraction = -1f
                        volumeSliderViewModel?.onValueChangeFinished()
                    }
                )
            }
    ) {
        val fillColor = MaterialTheme.colorScheme.primary

        // Active filled level (from bottom to top)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(animatedFraction.coerceIn(0f, 1f))
                .background(fillColor)
        )

        // Speaker Icon at bottom (tap to mute/unmute)
        val currentSliderState = sliderState
        val isMuted = if (currentSliderState != null) {
            currentSliderState.value <= currentSliderState.valueRange.start
        } else {
            currentVolume <= minVolume
        }
        val iconColor = if (animatedFraction > 0.15f) MaterialTheme.colorScheme.onPrimary else Color.White
        val iconRes = if (isMuted) R.drawable.ic_volume_media_mute else R.drawable.ic_volume_media

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp)
                .size(32.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    if (currentSliderState != null && volumeSliderViewModel != null) {
                        volumeSliderViewModel.toggleMuted(currentSliderState)
                    } else {
                        val targetVol = if (currentVolume > minVolume) minVolume else (maxVolume * 0.7f).roundToInt()
                        currentVolume = targetVol
                        try { audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, targetVol, 0) } catch (e: Exception) {}
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = "Volumen",
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun HyperOSTilesGrid(
    tiles: List<com.android.systemui.qs.panels.ui.viewmodel.TileViewModel>,
    view: android.view.View,
    showLabels: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val rows = tiles.chunked(4)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (showLabels) 14.dp else 12.dp)
    ) {
        for (row in rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (tile in row) {
                    HyperOSTileItem(
                        tile = tile,
                        view = view,
                        showLabels = showLabels,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size < 4) {
                    for (i in 0 until (4 - row.size)) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HyperOSTileItem(
    tile: com.android.systemui.qs.panels.ui.viewmodel.TileViewModel,
    view: android.view.View,
    showLabels: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val resources = context.resources
    val uiState by produceState(tile.currentState.toUiState(resources), tile, resources) {
        tile.state.collect { value = it.toUiState(resources) }
    }
    val tileState by tile.state.collectAsStateWithLifecycle(tile.currentState)
    val isActive = tileState.state == android.service.quicksettings.Tile.STATE_ACTIVE
    val isUnavailable = tileState.state == android.service.quicksettings.Tile.STATE_UNAVAILABLE

    val targetBgColor = when {
        isActive -> MaterialTheme.colorScheme.primary
        isUnavailable -> Color(0x33444444)
        else -> Color(0x593A3A3A)
    }
    val targetIconColor = when {
        isActive -> MaterialTheme.colorScheme.onPrimary
        isUnavailable -> Color(0x55FFFFFF)
        else -> Color.White
    }

    val animatedBgColor by animateColorAsState(targetBgColor, label = "TileBgColor")
    val animatedIconColor by animateColorAsState(targetIconColor, label = "TileIconColor")

    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.86f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        ),
        label = "TileBounceScale"
    )

    val iconProvider by produceState(tile.currentState.toIconProvider(), tile) {
        tile.state.collect { value = it.toIconProvider() }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(28.dp))
                .background(animatedBgColor)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        if (uiState.handlesToggleClick) {
                            tile.toggleClick()
                        } else {
                            tile.mainClick(tile.expandable)
                        }
                    },
                    onLongClick = {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                        if (uiState.handlesToggleClick) {
                            tile.mainClick(tile.expandable)
                        } else {
                            tile.settingsClick(tile.expandable)
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            SmallTileContent(
                iconProvider = {
                    iconProvider.icon?.let {
                        if (it is com.android.systemui.qs.tileimpl.QSTileImpl.ResourceIcon) {
                            com.android.systemui.common.shared.model.Icon.Resource(it.resId, null)
                        } else {
                            com.android.systemui.common.shared.model.Icon.Loaded(it.getDrawable(this), null)
                        }
                    } ?: com.android.systemui.common.shared.model.Icon.Resource(R.drawable.ic_error_outline, null)
                },
                color = animatedIconColor,
                size = { 26.dp },
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (showLabels) {
            Spacer(Modifier.height(5.dp))

            Text(
                text = tileState.label?.toString() ?: "",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
                    color = Color.White
                ),
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun HyperOSEditMode(
    viewModel: EditModeViewModel,
    modifier: Modifier = Modifier,
) {
    val view = androidx.compose.ui.platform.LocalView.current
    val allEditTiles by viewModel.tiles.collectAsStateWithLifecycle(emptyList())

    BackHandler { viewModel.stopEditing() }
    DisposableEffect(Unit) { onDispose { viewModel.stopEditing() } }

    val activeTiles = remember(allEditTiles) {
        allEditTiles.filter { it.isCurrent && it.tileSpec.spec != "internet" && it.tileSpec.spec != "wifi" && it.tileSpec.spec != "cell" }
    }
    val availableTiles = remember(allEditTiles) {
        allEditTiles.filter { !it.isCurrent && it.tileSpec.spec != "internet" && it.tileSpec.spec != "wifi" && it.tileSpec.spec != "cell" }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            viewModel.stopEditing()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = stringResource(R.string.hyperos_qs_close),
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column {
                    Text(
                        text = stringResource(R.string.hyperos_qs_edit_cards),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Color.White
                        )
                    )
                    Text(
                        text = stringResource(R.string.hyperos_qs_edit_cards_subtitle),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.65f)
                        )
                    )
                }
            }

            // "Listo" capsule button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        viewModel.stopEditing()
                    }
                    .padding(horizontal = 18.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.hyperos_qs_done),
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        fontSize = 13.sp
                    )
                )
            }
        }

        VerticalSeparator(14.dp)

        // Section 1: Active Switches
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.hyperos_qs_added_switches),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.9f)
                )
            )
            Text(
                text = "${activeTiles.size}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            )
        }

        VerticalSeparator(10.dp)

        // Active Tiles Grid (4 columns)
        val activeRows = activeTiles.chunked(4)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            for (row in activeRows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (tile in row) {
                        HyperOSEditTileItem(
                            tile = tile,
                            isAdd = false,
                            onBadgeClick = {
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                viewModel.removeTile(tile.tileSpec)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (row.size < 4) {
                        for (i in 0 until (4 - row.size)) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        VerticalSeparator(24.dp)

        // Section 2: Available Switches
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.hyperos_qs_available_switches),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.9f)
                )
            )
            Text(
                text = "${availableTiles.size}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color(0xFF4CAF50)
                )
            )
        }

        VerticalSeparator(10.dp)

        // Available Tiles Grid (4 columns)
        val availableRows = availableTiles.chunked(4)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            for (row in availableRows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (tile in row) {
                        HyperOSEditTileItem(
                            tile = tile,
                            isAdd = true,
                            onBadgeClick = {
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                viewModel.addTile(tile.tileSpec)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (row.size < 4) {
                        for (i in 0 until (4 - row.size)) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        VerticalSeparator(36.dp)
    }
}

@Composable
private fun HyperOSEditTileItem(
    tile: EditTileViewModel,
    isAdd: Boolean,
    onBadgeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val badgeInteractionSource = remember { MutableInteractionSource() }
    val isPressed by badgeInteractionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.82f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        ),
        label = "BadgeBounceScale"
    )

    val iconProvider: android.content.Context.() -> com.android.systemui.common.shared.model.Icon = {
        tile.icon
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(60.dp),
            contentAlignment = Alignment.Center
        ) {
            // Main Tile Circular Button
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(if (isAdd) Color(0x387F7F7F) else Color(0x597F7F7F)),
                contentAlignment = Alignment.Center
            ) {
                SmallTileContent(
                    iconProvider = iconProvider,
                    color = Color.White,
                    size = { 24.dp },
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // Badge (+ or -) with dedicated click target (Unclipped, cleanly floating in top-right)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 1.dp, y = (-1).dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (isAdd) Color(0xFF34A853) else Color(0xFFEA4335))
                    .clickable(
                        interactionSource = badgeInteractionSource,
                        indication = null,
                        onClick = onBadgeClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = if (isAdd) R.drawable.ic_add else R.drawable.ic_remove),
                    contentDescription = if (isAdd) "Añadir" else "Quitar",
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = tile.label.text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
                color = Color.White
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HyperOSConnectivityCard(
    title: String,
    subtitle: String,
    icon: com.android.systemui.plugins.qs.QSTile.Icon?,
    iconSupplier: java.util.function.Supplier<com.android.systemui.plugins.qs.QSTile.Icon?>? = null,
    fallbackIconRes: Int,
    isActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val targetBackgroundColor = if (isActive) {
        MaterialTheme.colorScheme.primary
    } else {
        Color(0x593A3A3A)
    }
    val targetIconColor = if (isActive) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        Color.White
    }
    val targetContentColor = if (isActive) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        Color.White
    }
    val targetSubColor = if (isActive) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
    } else {
        Color(0x99FFFFFF)
    }

    var hasRendered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        hasRendered = true
    }

    val animatedBackgroundColor by animateColorAsState(
        targetValue = targetBackgroundColor,
        animationSpec = if (!hasRendered) androidx.compose.animation.core.snap() else androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
        label = "CardBgColor"
    )
    val animatedIconColor by animateColorAsState(
        targetValue = targetIconColor,
        animationSpec = if (!hasRendered) androidx.compose.animation.core.snap() else androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
        label = "CardIconColor"
    )
    val animatedContentColor by animateColorAsState(
        targetValue = targetContentColor,
        animationSpec = if (!hasRendered) androidx.compose.animation.core.snap() else androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
        label = "CardContentColor"
    )
    val animatedSubColor by animateColorAsState(
        targetValue = targetSubColor,
        animationSpec = if (!hasRendered) androidx.compose.animation.core.snap() else androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
        label = "CardSubColor"
    )

    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        ),
        label = "ConnCardScale"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .height(72.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(animatedBackgroundColor)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp)
    ) {
        val qsIcon = iconSupplier?.get() ?: icon
        if (qsIcon != null) {
            SmallTileContent(
                iconProvider = {
                    if (qsIcon is com.android.systemui.qs.tileimpl.QSTileImpl.ResourceIcon) {
                        com.android.systemui.common.shared.model.Icon.Resource(qsIcon.resId, null)
                    } else {
                        com.android.systemui.common.shared.model.Icon.Loaded(qsIcon.getDrawable(this), null)
                    }
                },
                color = animatedIconColor,
                size = { 28.dp },
                modifier = Modifier
            )
        } else {
            Icon(
                painter = painterResource(id = fallbackIconRes),
                contentDescription = title,
                tint = animatedIconColor,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(verticalArrangement = Arrangement.Center) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    fontSize = 15.sp,
                    color = animatedContentColor
                ),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    color = animatedSubColor
                ),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun VerticalSeparator(height: Dp) {
    Spacer(Modifier.height(height = height))
}

object QuickSettingsShade {
    object Elements {
        val StatusBar = ElementKey("QuickSettingsShadeOverlayStatusBar")
        val Panel = ElementKey("QuickSettingsShadeOverlayPanel")
        val Header = ElementKey("QuickSettingsShadeOverlayHeader")
    }

    object Dimensions {
        val brightnessSliderDimensions: BrightnessSliderDimensions
            @Composable
            @ReadOnlyComposable
            get() =
                BrightnessSliderDimensions(
                    DpSize(sliderIconSize, sliderIconSize),
                    brightnessThumbHeight,
                    brightnessThumbWidth,
                    brightnessTrackHeight,
                    brightnessVerticalPadding,
                    brightnessRoundedCorner,
                    brightnessFrameWidth,
                    brightnessFrameHeight,
                )

        val HorizontalPadding: Dp
            @Composable
            @ReadOnlyComposable
            get() = dimensionResource(id = R.dimen.overlay_qs_layout_horizontal_padding)

        val VerticalPadding: Dp
            @Composable
            @ReadOnlyComposable
            get() = dimensionResource(id = R.dimen.overlay_qs_layout_vertical_padding)

        val VolumeSliderDimensions: VolumeSliderDimensions
            @Composable
            @ReadOnlyComposable
            get() =
                VolumeSliderDimensions(
                    sliderIconSize,
                    volumeThumbHeight,
                    volumeThumbWidth,
                    volumeTrackHeight,
                    volumeVerticalPadding,
                )

        val ToolbarBottomPadding: Dp
            @Composable
            @ReadOnlyComposable
            get() = dimensionResource(id = R.dimen.toolbar_bottom_padding)

        val VolumeSliderExtraPadding: Dp
            @Composable
            @ReadOnlyComposable
            get() = dimensionResource(id = R.dimen.overlay_qs_layout_volume_extra_padding)

        val ToolbarHeight: Dp
            @Composable
            @ReadOnlyComposable
            get() = dimensionResource(id = R.dimen.overlay_qs_layout_toolbar_height)

        // This is used around the header and toolbar
        val ShortPadding = 8.dp

        private val brightnessThumbHeight: Dp
            @Composable
            @ReadOnlyComposable
            get() = dimensionResource(id = R.dimen.overlay_qs_layout_brightness_thumb_height)

        private val brightnessThumbWidth: Dp
            @Composable
            @ReadOnlyComposable
            get() = dimensionResource(id = R.dimen.overlay_qs_layout_brightness_thumb_width)

        private val brightnessTrackHeight: Dp
            @Composable
            @ReadOnlyComposable
            get() = dimensionResource(id = R.dimen.overlay_qs_layout_brightness_track_height)

        private val brightnessVerticalPadding: Dp
            @Composable
            @ReadOnlyComposable
            get() = dimensionResource(id = R.dimen.overlay_qs_layout_brightness_vertical_padding)

        private val brightnessRoundedCorner: Dp
            @Composable
            @ReadOnlyComposable
            get() = dimensionResource(id = R.dimen.overlay_qs_layout_brightness_rounded_corner)

        private val brightnessFrameWidth: Dp
            @Composable
            @ReadOnlyComposable
            get() = dimensionResource(id = R.dimen.overlay_qs_layout_brightness_frame_width)

        private val brightnessFrameHeight: Dp
            @Composable
            @ReadOnlyComposable
            get() = dimensionResource(id = R.dimen.overlay_qs_layout_brightness_frame_height)

        private val sliderIconSize: Dp
            @Composable
            @ReadOnlyComposable
            get() = dimensionResource(id = R.dimen.overlay_qs_layout_brightness_icon_size)

        private val volumeVerticalPadding: Dp
            @Composable
            @ReadOnlyComposable
            get() = dimensionResource(id = R.dimen.overlay_qs_layout_volume_vertical_padding)

        private val volumeThumbHeight: Dp
            @Composable
            @ReadOnlyComposable
            get() = dimensionResource(id = R.dimen.overlay_qs_layout_volume_thumb_height)

        private val volumeThumbWidth: Dp
            @Composable
            @ReadOnlyComposable
            get() = dimensionResource(id = R.dimen.overlay_qs_layout_volume_thumb_width)

        private val volumeTrackHeight: Dp
            @Composable
            @ReadOnlyComposable
            get() = dimensionResource(id = R.dimen.overlay_qs_layout_volume_track_height)
    }

    /**
     * Applies system gesture exclusion to a component adding [Dimensions.Padding] to left and
     * right.
     */
    @Composable
    fun Modifier.systemGestureExclusionInShade(enabled: () -> Boolean): Modifier {
        val density = LocalDensity.current
        val padding = Dimensions.HorizontalPadding
        return thenIf(enabled()) {
            Modifier.systemGestureExclusion { layoutCoordinates ->
                val sidePadding = with(density) { padding.toPx() }
                Rect(
                    offset = Offset(x = -sidePadding, y = 0f),
                    size =
                        Size(
                            width = layoutCoordinates.size.width.toFloat() + 2 * sidePadding,
                            height = layoutCoordinates.size.height.toFloat(),
                        ),
                )
            }
        }
    }
}
