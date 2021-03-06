/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.launcher3;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.dynamicui.ExtractedColors;
import com.android.launcher3.logging.UserEventDispatcher.LogContainerProvider;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import com.android.launcher3.userevent.nano.LauncherLogProto.ControlType;
import com.android.launcher3.userevent.nano.LauncherLogProto.Target;

import org.zimmob.zimlx.ZimPreferences;
import org.zimmob.zimlx.blur.BlurDrawable;
import org.zimmob.zimlx.blur.BlurWallpaperProvider;

import androidx.core.graphics.ColorUtils;

import static com.android.launcher3.LauncherState.ALL_APPS;

public class Hotseat extends FrameLayout implements LogContainerProvider, Insettable {

    private final Launcher mLauncher;
    private CellLayout mContent;

    @ViewDebug.ExportedProperty(category = "launcher")
    private boolean mHasVerticalHotseat;
    @ViewDebug.ExportedProperty(category = "launcher")
    private int mBackgroundColor;
    @ViewDebug.ExportedProperty(category = "launcher")
    private Drawable mBackground;
    private ValueAnimator mBackgroundColorAnimator;

    public float dockRadius;

    public Hotseat(Context context) {
        this(context, null);
    }

    public Hotseat(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Hotseat(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mLauncher = Launcher.getLauncher(context);
        dockRadius = Utilities.getZimPrefs(context).getDockRadius();
        if (Utilities.getZimPrefs(context).getTransparentDock() || mHasVerticalHotseat) {
            setBackgroundColor(Color.TRANSPARENT);
        } else {
            mBackgroundColor = ColorUtils.setAlphaComponent(
                    Utilities.resolveAttributeData(context, R.attr.allAppsContainerColor), 0);
            mBackground = BlurWallpaperProvider.Companion.isEnabled(BlurWallpaperProvider.BLUR_ALLAPPS) ?
                    mLauncher.getBlurWallpaperProvider().createDrawable() : new ColorDrawable(mBackgroundColor);
            setBackground(mBackground);
        }
    }

    public CellLayout getLayout() {
        return mContent;
    }

    /* Get the orientation invariant order of the item in the hotseat for persistence. */
    int getOrderInHotseat(int x, int y) {
        int xOrder = mHasVerticalHotseat ? (mContent.getCountY() - y - 1) : x;
        int yOrder = mHasVerticalHotseat ? x * mContent.getCountY() : y * mContent.getCountX();
        return xOrder + yOrder;
    }

    /* Get the orientation specific coordinates given an invariant order in the hotseat. */
    int getCellXFromOrder(int rank) {
        int size = mHasVerticalHotseat ? mContent.getCountY() : mContent.getCountX();
        return mHasVerticalHotseat ? rank / size : rank % size;
    }

    int getCellYFromOrder(int rank) {
        int size = mHasVerticalHotseat ? mContent.getCountY() : mContent.getCountX();
        return mHasVerticalHotseat ? (mContent.getCountY() - ((rank % size) + 1)) : rank / size;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        ZimPreferences prefs = Utilities.getZimPrefs(getContext());
        if (prefs.getDockHide()) {
            setVisibility(GONE);
        }

        mContent = findViewById(R.id.layout);
    }

    void resetLayout(boolean hasVerticalHotseat) {
        mContent.removeAllViewsInLayout();
        mHasVerticalHotseat = hasVerticalHotseat;
        InvariantDeviceProfile idp = mLauncher.getDeviceProfile().inv;
        int rows = Utilities.getZimPrefs(mLauncher).getDockRowsCount();
        if (hasVerticalHotseat) {
            mContent.setGridSize(rows, idp.numHotseatIcons);
        } else {
            mContent.setGridSize(idp.numHotseatIcons, rows);
        }

        if (!FeatureFlags.NO_ALL_APPS_ICON && !Utilities.getZimPrefs(mLauncher.getApplicationContext()).getHideDockButton()) {
            // Add the Apps button
            Context context = getContext();
            DeviceProfile grid = mLauncher.getDeviceProfile();
            int allAppsButtonRank = grid.inv.getAllAppsButtonRank();

            LayoutInflater inflater = LayoutInflater.from(context);
            TextView allAppsButton = (TextView)
                    inflater.inflate(R.layout.all_apps_button, mContent, false);
            Drawable d = context.getResources().getDrawable(R.drawable.all_apps_button_icon);
            d.setBounds(0, 0, grid.iconSizePx, grid.iconSizePx);

            int scaleDownPx = getResources().getDimensionPixelSize(R.dimen.all_apps_button_scale_down);
            Rect bounds = d.getBounds();
            d.setBounds(bounds.left, bounds.top + scaleDownPx / 2, bounds.right - scaleDownPx,
                    bounds.bottom - scaleDownPx / 2);
            allAppsButton.setCompoundDrawables(null, d, null, null);

            allAppsButton.setContentDescription(context.getString(R.string.all_apps_button_label));
            if (mLauncher != null) {
                allAppsButton.setOnClickListener((v) -> {
                    if (!mLauncher.isInState(ALL_APPS)) {
                        mLauncher.getUserEventDispatcher().logActionOnControl(Action.Touch.TAP,
                                ControlType.ALL_APPS_BUTTON);
                        mLauncher.getStateManager().goToState(ALL_APPS);
                    }
                });
                allAppsButton.setOnFocusChangeListener(mLauncher.mFocusHandler);
            }

            // Note: We do this to ensure that the hotseat is always laid out in the orientation of
            // the hotseat in order regardless of which orientation they were added
            int x = getCellXFromOrder(allAppsButtonRank);
            int y = getCellYFromOrder(allAppsButtonRank);
            CellLayout.LayoutParams lp = new CellLayout.LayoutParams(x, y, 1, 1);
            lp.canReorder = false;
            mContent.addViewToCellLayout(allAppsButton, -1, allAppsButton.getId(), lp, true);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // We don't want any clicks to go through to the hotseat unless the workspace is in
        // the normal state or an accessible drag is in progress.
        return !mLauncher.getWorkspace().workspaceIconsCanBeDragged() &&
                !mLauncher.getAccessibilityDelegate().isInAccessibleDrag();
    }

    @Override
    public void fillInLogContainerData(View v, ItemInfo info, Target target, Target targetParent) {
        target.gridX = info.cellX;
        target.gridY = info.cellY;
        targetParent.containerType = ContainerType.HOTSEAT;
    }

    @Override
    public void setInsets(Rect insets) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        DeviceProfile grid = mLauncher.getDeviceProfile();

        if (grid.isVerticalBarLayout()) {
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            if (grid.isSeascape()) {
                lp.gravity = Gravity.LEFT;
                lp.width = grid.hotseatBarSizePx + insets.left;
            } else {
                lp.gravity = Gravity.RIGHT;
                lp.width = grid.hotseatBarSizePx + insets.right;
            }
        } else {
            lp.gravity = Gravity.BOTTOM;
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = grid.hotseatBarSizePx + insets.bottom;
        }
        Rect padding = grid.getHotseatLayoutPadding();
        getLayout().setPadding(padding.left, padding.top, padding.right, padding.bottom);
        setLayoutParams(lp);
        InsettableFrameLayout.dispatchInsets(this, insets);
    }

    public void updateColor(ExtractedColors extractedColors, boolean animate) {
        if (!(mBackground instanceof ColorDrawable)) return;
        if (!mHasVerticalHotseat) {
            int color = extractedColors.getDockColor(getContext());
            if (mBackgroundColorAnimator != null) {
                mBackgroundColorAnimator.cancel();
            }
            if (!animate) {
                setBackgroundColor(color);
            } else {
                mBackgroundColorAnimator = ValueAnimator.ofInt(mBackgroundColor, color);
                mBackgroundColorAnimator.setEvaluator(new ArgbEvaluator());
                mBackgroundColorAnimator.addUpdateListener(animation -> ((ColorDrawable) mBackground).setColor((Integer) animation.getAnimatedValue()));
                mBackgroundColorAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mBackgroundColorAnimator = null;
                    }
                });
                mBackgroundColorAnimator.start();
            }
            mBackgroundColor = color;
        }
    }

    public void setBackgroundTransparent(boolean enable) {
        if (mBackground == null) return;
        if (enable) {
            mBackground.setAlpha(0);
        } else {
            mBackground.setAlpha(255);
        }
    }

    public int getBackgroundDrawableColor() {
        return mBackgroundColor;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mBackground instanceof BlurDrawable) {
            ((BlurDrawable) mBackground).stopListening();
        }
    }

    public void setOverscroll(float progress) {
        if (mBackground instanceof BlurDrawable) {
            ((BlurDrawable) mBackground).setOverscroll(progress);
        }
    }

    @Override
    public void setTranslationX(float translationX) {
        super.setTranslationX(translationX);
        LauncherAppState.getInstance(getContext()).getLauncher().mHotseat.setOverscroll(translationX);
    }
}
