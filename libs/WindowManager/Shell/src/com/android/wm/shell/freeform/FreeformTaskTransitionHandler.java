/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.wm.shell.freeform;

import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.view.WindowManager.TRANSIT_PIP;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.app.WindowConfiguration;
import android.content.Context;
import android.graphics.Rect;
import android.os.Handler;
import android.os.IBinder;
import android.util.ArrayMap;
import android.util.Log;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.window.TransitionInfo;
import android.window.TransitionRequestInfo;
import android.window.WindowContainerTransaction;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.jank.InteractionJankMonitor;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.shared.animation.MinimizeAnimator;
import com.android.wm.shell.transition.Transitions;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@link Transitions.TransitionHandler} that handles freeform task maximizing, closing, and
 * restoring transitions.
 */
public class FreeformTaskTransitionHandler
        implements Transitions.TransitionHandler, FreeformTaskTransitionStarter {
    private static final String TAG = "FreeformTaskTransitionHandler";
    private static final int CLOSE_ANIM_DURATION = 400;
    private static final int OPEN_ANIM_DURATION = 380;
    private static final Interpolator OPEN_INTERPOLATOR =
            new PathInterpolator(0.16f, 1.0f, 0.3f, 1.0f);
    private final Transitions mTransitions;
    private final DisplayController mDisplayController;
    private final ShellExecutor mMainExecutor;
    private final ShellExecutor mAnimExecutor;
    private final Handler mAnimHandler;

    private final List<IBinder> mPendingTransitionTokens = new ArrayList<>();

    private final ArrayMap<IBinder, ArrayList<Animator>> mAnimations = new ArrayMap<>();

    public FreeformTaskTransitionHandler(
            Transitions transitions,
            DisplayController displayController,
            ShellExecutor mainExecutor,
            ShellExecutor animExecutor,
            Handler animHandler) {
        mTransitions = transitions;
        mDisplayController = displayController;
        mMainExecutor = mainExecutor;
        mAnimExecutor = animExecutor;
        mAnimHandler = animHandler;
    }

    @Override
    public void startWindowingModeTransition(
            int targetWindowingMode, WindowContainerTransaction wct) {
        final int type;
        switch (targetWindowingMode) {
            case WINDOWING_MODE_FULLSCREEN:
                type = Transitions.TRANSIT_MAXIMIZE;
                break;
            case WINDOWING_MODE_FREEFORM:
                type = Transitions.TRANSIT_RESTORE_FROM_MAXIMIZE;
                break;
            default:
                throw new IllegalArgumentException("Unexpected target windowing mode "
                        + WindowConfiguration.windowingModeToString(targetWindowingMode));
        }
        final IBinder token = mTransitions.startTransition(type, wct, this);
        mPendingTransitionTokens.add(token);
    }

    @Override
    public IBinder startMinimizedModeTransition(
            WindowContainerTransaction wct, int taskId, boolean isLastTask) {
        final int type = Transitions.TRANSIT_MINIMIZE;
        final IBinder token = mTransitions.startTransition(type, wct, this);
        mPendingTransitionTokens.add(token);
        return token;
    }

    @Override
    public IBinder startPipTransition(WindowContainerTransaction wct) {
        final IBinder token = mTransitions.startTransition(TRANSIT_PIP, wct, null);
        mPendingTransitionTokens.add(token);
        return token;
    }

    @Override
    public IBinder startRemoveTransition(WindowContainerTransaction wct) {
        final int type = WindowManager.TRANSIT_CLOSE;
        final IBinder transition = mTransitions.startTransition(type, wct, this);
        mPendingTransitionTokens.add(transition);
        return transition;
    }

    @Override
    public boolean startAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startT,
            @NonNull SurfaceControl.Transaction finishT,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        boolean transitionHandled = false;
        final ArrayList<Animator> animations = new ArrayList<>();
        final Runnable onAnimFinish = () -> mMainExecutor.execute(() -> {
            if (!animations.isEmpty()) return;
            mAnimations.remove(transition);
            finishCallback.onTransitionFinished(null /* wct */);
        });
        for (TransitionInfo.Change change : info.getChanges()) {
            if ((change.getFlags() & TransitionInfo.FLAG_IS_WALLPAPER) != 0) {
                continue;
            }

            final ActivityManager.RunningTaskInfo taskInfo = change.getTaskInfo();
            if (taskInfo == null || taskInfo.taskId == -1) {
                continue;
            }

            switch (change.getMode()) {
                case WindowManager.TRANSIT_OPEN:
                case WindowManager.TRANSIT_TO_FRONT:
                    if (change.getTaskInfo().getWindowingMode() == WINDOWING_MODE_FREEFORM) {
                        transitionHandled |= startOpenTransition(transition, change,
                                startT, finishT, animations, onAnimFinish);
                    }
                    break;
                case WindowManager.TRANSIT_CHANGE:
                    transitionHandled |= startChangeTransition(
                            transition, info.getType(), change);
                    break;
                case WindowManager.TRANSIT_TO_BACK:
                    transitionHandled |= startMinimizeTransition(
                            transition, info.getType(), change, finishT, animations, onAnimFinish);
                    break;
                case WindowManager.TRANSIT_CLOSE:
                    if (change.getTaskInfo().getWindowingMode() == WINDOWING_MODE_FREEFORM) {
                        transitionHandled |= startCloseTransition(transition, change,
                                finishT, animations, onAnimFinish);
                    }
                    break;
            }
        }
        if (!transitionHandled) {
            return false;
        }
        mAnimations.put(transition, animations);
        // startT must be applied before animations start.
        startT.apply();
        mAnimExecutor.execute(() -> {
            for (Animator anim : animations) {
                anim.start();
            }
        });
        // Run this here in case no animators are created.
        onAnimFinish.run();
        mPendingTransitionTokens.remove(transition);
        return true;
    }

    @Override
    public void mergeAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startT,
            @NonNull SurfaceControl.Transaction finishT,
            @NonNull IBinder mergeTarget,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        ArrayList<Animator> animations = mAnimations.get(mergeTarget);
        if (animations == null) return;
        mAnimExecutor.execute(() -> {
            for (Animator anim : animations) {
                anim.end();
            }
        });

    }

    private boolean startChangeTransition(
            IBinder transition,
            int type,
            TransitionInfo.Change change) {
        if (!mPendingTransitionTokens.contains(transition)) {
            return false;
        }

        boolean handled = false;
        final ActivityManager.RunningTaskInfo taskInfo = change.getTaskInfo();
        if (type == Transitions.TRANSIT_MAXIMIZE
                && taskInfo.getWindowingMode() == WINDOWING_MODE_FULLSCREEN) {
            // TODO: Add maximize animations
            handled = true;
        }

        if (type == Transitions.TRANSIT_RESTORE_FROM_MAXIMIZE
                && taskInfo.getWindowingMode() == WINDOWING_MODE_FREEFORM) {
            // TODO: Add restore animations
            handled = true;
        }

        return handled;
    }

    private boolean startMinimizeTransition(
            IBinder transition,
            int type,
            TransitionInfo.Change change,
            SurfaceControl.Transaction finishT,
            ArrayList<Animator> animations,
            Runnable onAnimFinish) {
        if (!mPendingTransitionTokens.contains(transition)) {
            return false;
        }

        final ActivityManager.RunningTaskInfo taskInfo = change.getTaskInfo();
        if (type != Transitions.TRANSIT_MINIMIZE) {
            return false;
        }

        SurfaceControl.Transaction t = new SurfaceControl.Transaction();
        SurfaceControl sc = change.getLeash();
        finishT.hide(sc);
        final Context displayContext =
                mDisplayController.getDisplayContext(taskInfo.displayId);
        if (displayContext == null) {
            Log.w(TAG, "No displayContext for displayId=" + taskInfo.displayId);
            return false;
        }
        final Animator animator = MinimizeAnimator.create(
                displayContext,
                change,
                t,
                (anim) -> {
                    mMainExecutor.execute(() -> {
                        animations.remove(anim);
                        onAnimFinish.run();
                    });
                    return null;
                },
                InteractionJankMonitor.getInstance(),
                mAnimHandler);
        animations.add(animator);
        return true;
    }

    private boolean startCloseTransition(IBinder transition, TransitionInfo.Change change,
            SurfaceControl.Transaction finishT, ArrayList<Animator> animations,
            Runnable onAnimFinish) {
        if (!mPendingTransitionTokens.contains(transition)) return false;
        int screenHeight = mDisplayController
                .getDisplayLayout(change.getTaskInfo().displayId).height();
        ValueAnimator animator = new ValueAnimator();
        animator.setDuration(CLOSE_ANIM_DURATION)
                .setFloatValues(0f, 1f);
        SurfaceControl.Transaction t = new SurfaceControl.Transaction();
        SurfaceControl sc = change.getLeash();
        finishT.hide(sc);
        final Rect startBounds = new Rect(change.getStartAbsBounds());
        animator.addUpdateListener(animation -> {
            final float newTop = startBounds.top + (animation.getAnimatedFraction() * screenHeight);
            t.setPosition(sc, startBounds.left, newTop);
            if (newTop > screenHeight) {
                // At this point the task surface is off-screen, so hide it to prevent flicker
                // failures. See b/377651666.
                t.hide(sc);
            }
            t.apply();
        });
        animator.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mMainExecutor.execute(() -> {
                            animations.remove(animator);
                            onAnimFinish.run();
                        });
                    }
                });
        animations.add(animator);
        return true;
    }

    private boolean startOpenTransition(IBinder transition, TransitionInfo.Change change,
            SurfaceControl.Transaction startT, SurfaceControl.Transaction finishT,
            ArrayList<Animator> animations, Runnable onAnimFinish) {
        if (!mPendingTransitionTokens.contains(transition)) {
            // If transition was started by another source (e.g. sidebar or WM), still handle it smoothly
            mPendingTransitionTokens.add(transition);
        }

        SurfaceControl sc = change.getLeash();
        final Rect endBounds = new Rect(change.getEndAbsBounds());
        if (endBounds.isEmpty()) {
            return false;
        }

        final float startScale = 0.10f;
        final float startAlpha = 0.0f;

        int screenWidth = 1220;
        int screenHeight = 2712;
        final int displayId = change.getTaskInfo() != null ? change.getTaskInfo().displayId : 0;
        var displayLayout = mDisplayController.getDisplayLayout(displayId);
        final Context displayContext = mDisplayController.getDisplayContext(displayId);
        if (displayLayout != null) {
            screenWidth = displayLayout.width();
            screenHeight = displayLayout.height();
        } else if (displayContext != null) {
            var bounds = displayContext.getResources().getConfiguration().windowConfiguration.getBounds();
            if (bounds != null && !bounds.isEmpty()) {
                screenWidth = bounds.width();
                screenHeight = bounds.height();
            }
        }

        int sidebarPosX = 1;
        int sidebarPosY = 0;
        boolean isPortrait = screenHeight >= screenWidth;
        if (displayContext != null) {
            try {
                sidebarPosX = android.provider.Settings.System.getInt(
                        displayContext.getContentResolver(),
                        "sidebar_position_x",
                        endBounds.centerX() >= screenWidth / 2 ? 1 : -1);
                sidebarPosY = android.provider.Settings.System.getInt(
                        displayContext.getContentResolver(),
                        isPortrait ? "sidebar_position_y_portrait" : "sidebar_position_y_landscape",
                        0);
            } catch (Exception ignored) {
                sidebarPosX = endBounds.centerX() >= screenWidth / 2 ? 1 : -1;
            }
        } else {
            sidebarPosX = endBounds.centerX() >= screenWidth / 2 ? 1 : -1;
        }

        final Rect startAbsBounds = change.getStartAbsBounds();
        final float startPosX;
        final float startPosY;

        float density = displayContext != null ? displayContext.getResources().getDisplayMetrics().density : 2.5f;
        float edgeMargin = 20f * density;

        if (startAbsBounds != null && !startAbsBounds.isEmpty()) {
            boolean fromRightEdge = startAbsBounds.centerX() >= screenWidth / 2;
            startPosX = fromRightEdge
                    ? (screenWidth - (startScale * endBounds.width()) - edgeMargin)
                    : edgeMargin;
            startPosY = Math.max(0f, Math.min(screenHeight - (startScale * endBounds.height()),
                    startAbsBounds.centerY() - (startScale * endBounds.height()) / 2f));
        } else {
            // Emerges from the lateral edge (left or right depending on sidebar/task target side)
            boolean fromRight = (sidebarPosX >= 0) || (endBounds.centerX() >= screenWidth / 2);
            if (sidebarPosX < 0 && endBounds.centerX() <= screenWidth / 2) {
                fromRight = false;
            }
            startPosX = fromRight
                    ? (screenWidth - (startScale * endBounds.width()) - edgeMargin)
                    : edgeMargin;
            if (sidebarPosY != 0) {
                float targetY = (screenHeight / 2f + sidebarPosY) - (startScale * endBounds.height()) / 2f;
                startPosY = Math.max(0f, Math.min(screenHeight - (startScale * endBounds.height()), targetY));
            } else {
                startPosY = endBounds.top + (endBounds.height() * (1f - startScale) / 2f);
            }
        }

        final float endPosX = endBounds.left;
        final float endPosY = endBounds.top;

        // Apply initial frame setup
        startT.setScale(sc, startScale, startScale);
        startT.setPosition(sc, startPosX, startPosY);
        startT.setAlpha(sc, startAlpha);
        startT.show(sc);

        // Ensure finishT resets final state
        finishT.setScale(sc, 1.0f, 1.0f);
        finishT.setPosition(sc, endPosX, endPosY);
        finishT.setAlpha(sc, 1.0f);
        finishT.show(sc);

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(OPEN_ANIM_DURATION);
        animator.setInterpolator(OPEN_INTERPOLATOR);

        SurfaceControl.Transaction t = new SurfaceControl.Transaction();
        animator.addUpdateListener(animation -> {
            final float fraction = animation.getAnimatedFraction();
            final float scale = startScale + (1f - startScale) * fraction;
            final float alpha = Math.min(1.0f, fraction * 2.8f);

            final float posX = startPosX + (endPosX - startPosX) * fraction;
            final float posY = startPosY + (endPosY - startPosY) * fraction;

            t.setScale(sc, scale, scale);
            t.setPosition(sc, posX, posY);
            t.setAlpha(sc, alpha);
            t.apply();
        });

        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mMainExecutor.execute(() -> {
                    animations.remove(animator);
                    onAnimFinish.run();
                });
            }
        });

        animations.add(animator);
        return true;
    }

    @Nullable
    @Override
    public WindowContainerTransaction handleRequest(@NonNull IBinder transition,
            @NonNull TransitionRequestInfo request) {
        final ActivityManager.RunningTaskInfo taskInfo = request.getTriggerTask();
        if (taskInfo != null && taskInfo.getWindowingMode() == WINDOWING_MODE_FREEFORM) {
            if (request.getType() == WindowManager.TRANSIT_OPEN
                    || request.getType() == WindowManager.TRANSIT_TO_FRONT) {
                mPendingTransitionTokens.add(transition);
                return new WindowContainerTransaction();
            }
        }
        return null;
    }
}
