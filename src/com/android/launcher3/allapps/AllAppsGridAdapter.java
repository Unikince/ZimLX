/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.launcher3.allapps;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.view.accessibility.AccessibilityEventCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityRecordCompat;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.AppInfo;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.AlphabeticalAppsList.AdapterItem;
import com.android.launcher3.anim.SpringAnimationHandler;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.touch.ItemClickHandler;
import com.android.launcher3.touch.ItemLongClickListener;
import com.android.launcher3.util.PackageManagerHelper;

import org.zimmob.zimlx.globalsearch.SearchProvider;
import org.zimmob.zimlx.globalsearch.SearchProviderController;
import org.zimmob.zimlx.globalsearch.providers.web.WebSearchProvider;

import java.util.List;

/**
 * The grid view adapter of all the apps.
 */
public class AllAppsGridAdapter extends RecyclerView.Adapter<AllAppsGridAdapter.ViewHolder> {

    public static final String TAG = "AppsGridAdapter";

    // A normal icon
    public static final int VIEW_TYPE_ICON = 1 << 1;
    // The message shown when there are no filtered results
    public static final int VIEW_TYPE_EMPTY_SEARCH = 1 << 2;
    // The message to continue to a market search when there are no filtered results
    public static final int VIEW_TYPE_SEARCH_MARKET = 1 << 3;

    // We use various dividers for various purposes.  They share enough attributes to reuse layouts,
    // but differ in enough attributes to require different view types

    // A divider that separates the apps list and the search market button
    public static final int VIEW_TYPE_ALL_APPS_DIVIDER = 1 << 4;
    public static final int VIEW_TYPE_WORK_TAB_FOOTER = 1 << 5;

    // Drawer folders
    public static final int VIEW_TYPE_FOLDER = 1 << 6;
    // Web search suggestions
    public static final int VIEW_TYPE_SEARCH_SUGGESTION = 1 << 7;

    // Common view type masks
    public static final int VIEW_TYPE_MASK_DIVIDER = VIEW_TYPE_ALL_APPS_DIVIDER;
    public static final int VIEW_TYPE_MASK_ICON = VIEW_TYPE_ICON | VIEW_TYPE_FOLDER;



    public interface BindViewCallback {
        void onBindView(ViewHolder holder);
    }

    private SpringAnimationHandler<ViewHolder> mSpringAnimationHandler;
    /**
     * ViewHolder for each icon.
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {

        public ViewHolder(View v) {
            super(v);
        }
    }

    /**
     * A subclass of GridLayoutManager that overrides accessibility values during app search.
     */
    public class AppsGridLayoutManager extends GridLayoutManager {

        public AppsGridLayoutManager(Context context) {
            super(context, 1, GridLayoutManager.VERTICAL, false);
        }

        @Override
        public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
            super.onInitializeAccessibilityEvent(event);

            // Ensure that we only report the number apps for accessibility not including other
            // adapter views
            final AccessibilityRecordCompat record = AccessibilityEventCompat
                    .asRecord(event);
            record.setItemCount(mApps.getNumFilteredApps());
            record.setFromIndex(Math.max(0,
                    record.getFromIndex() - getRowsNotForAccessibility(record.getFromIndex())));
            record.setToIndex(Math.max(0,
                    record.getToIndex() - getRowsNotForAccessibility(record.getToIndex())));
        }

        @Override
        public int getRowCountForAccessibility(RecyclerView.Recycler recycler,
                                               RecyclerView.State state) {
            return super.getRowCountForAccessibility(recycler, state) -
                    getRowsNotForAccessibility(mApps.getAdapterItems().size() - 1);
        }

        @Override
        public void onInitializeAccessibilityNodeInfoForItem(RecyclerView.Recycler recycler,
                                                             RecyclerView.State state, View host, AccessibilityNodeInfoCompat info) {
            super.onInitializeAccessibilityNodeInfoForItem(recycler, state, host, info);

            ViewGroup.LayoutParams lp = host.getLayoutParams();
            AccessibilityNodeInfoCompat.CollectionItemInfoCompat cic = info.getCollectionItemInfo();
            if (!(lp instanceof LayoutParams) || (cic == null)) {
                return;
            }
            LayoutParams glp = (LayoutParams) lp;
            info.setCollectionItemInfo(AccessibilityNodeInfoCompat.CollectionItemInfoCompat.obtain(
                    cic.getRowIndex() - getRowsNotForAccessibility(glp.getViewAdapterPosition()),
                    cic.getRowSpan(),
                    cic.getColumnIndex(),
                    cic.getColumnSpan(),
                    cic.isHeading(),
                    cic.isSelected()));
        }

        /**
         * Returns the number of rows before {@param adapterPosition}, including this position
         * which should not be counted towards the collection info.
         */
        private int getRowsNotForAccessibility(int adapterPosition) {
            List<AdapterItem> items = mApps.getAdapterItems();
            adapterPosition = Math.max(adapterPosition, mApps.getAdapterItems().size() - 1);
            int extraRows = 0;
            for (int i = 0; i <= adapterPosition; i++) {
                if (!isViewType(items.get(i).viewType, VIEW_TYPE_MASK_ICON)) {
                    extraRows++;
                }
            }
            return extraRows;
        }
    }

    /**
     * Helper class to size the grid items.
     */
    public class GridSpanSizer extends GridLayoutManager.SpanSizeLookup {

        public GridSpanSizer() {
            super();
            setSpanIndexCacheEnabled(true);
        }

        @Override
        public int getSpanSize(int position) {
            if (isIconViewType(mApps.getAdapterItems().get(position).viewType)) {
                return 1;
            } else {
                // Section breaks span the full width
                return mAppsPerRow;
            }
        }
    }

    private final Launcher mLauncher;
    private final LayoutInflater mLayoutInflater;
    private final AlphabeticalAppsList mApps;
    private final GridLayoutManager mGridLayoutMgr;
    private final GridSpanSizer mGridSizer;

    private final int mAppsPerRow;

    private BindViewCallback mBindViewCallback;
    private OnFocusChangeListener mIconFocusListener;

    // The text to show when there are no search results and no market search handler.
    private String mEmptySearchMessage;
    // The intent to send off to the market app, updated each time the search query changes.
    private Intent mMarketSearchIntent;

    public AllAppsGridAdapter(Launcher launcher, AlphabeticalAppsList apps) {
        Resources res = launcher.getResources();
        mLauncher = launcher;
        mApps = apps;
        mEmptySearchMessage = res.getString(R.string.all_apps_loading_message);
        mGridSizer = new GridSpanSizer();
        mGridLayoutMgr = new AppsGridLayoutManager(launcher);
        mGridLayoutMgr.setSpanSizeLookup(mGridSizer);
        mLayoutInflater = LayoutInflater.from(launcher);

        mAppsPerRow = mLauncher.getDeviceProfile().inv.numColumns;
        mGridLayoutMgr.setSpanCount(mAppsPerRow);
        if (FeatureFlags.LAUNCHER3_PHYSICS) {
            mSpringAnimationHandler = new SpringAnimationHandler<>(
                    SpringAnimationHandler.Y_DIRECTION, new AllAppsSpringAnimationFactory());
        }
    }

    public static boolean isDividerViewType(int viewType) {
        return isViewType(viewType, VIEW_TYPE_MASK_DIVIDER);
    }

    public static boolean isIconViewType(int viewType) {
        return isViewType(viewType, VIEW_TYPE_MASK_ICON);
    }

    public static boolean isViewType(int viewType, int viewTypeMask) {
        return (viewType & viewTypeMask) != 0;
    }

    public void setIconFocusListener(OnFocusChangeListener focusListener) {
        mIconFocusListener = focusListener;
    }

    /**
     * Sets the last search query that was made, used to show when there are no results and to also
     * seed the intent for searching the market.
     */
    public void setLastSearchQuery(String query) {
        Resources res = mLauncher.getResources();
        mEmptySearchMessage = res.getString(R.string.all_apps_no_search_results, query);
        mMarketSearchIntent = PackageManagerHelper.getMarketSearchIntent(mLauncher, query);
    }

    /**
     * Sets the callback for when views are bound.
     */
    public void setBindViewCallback(BindViewCallback cb) {
        mBindViewCallback = cb;
    }

    /**
     * Returns the grid layout manager.
     */
    public GridLayoutManager getLayoutManager() {
        return mGridLayoutMgr;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        switch (viewType) {
            case VIEW_TYPE_ICON:
                BubbleTextView icon = (BubbleTextView) mLayoutInflater.inflate(
                        R.layout.all_apps_icon, parent, false);
                icon.setOnClickListener(ItemClickHandler.INSTANCE);
                icon.setOnLongClickListener(ItemLongClickListener.INSTANCE_ALL_APPS);
                icon.setLongPressTimeout(ViewConfiguration.getLongPressTimeout());
                icon.setOnFocusChangeListener(mIconFocusListener);

                // Ensure the all apps icon height matches the workspace icons in portrait mode.
                icon.getLayoutParams().height = mLauncher.getDeviceProfile().allAppsCellHeightPx;
                return new ViewHolder(icon);

            case VIEW_TYPE_EMPTY_SEARCH:
                return new ViewHolder(mLayoutInflater.inflate(R.layout.all_apps_empty_search,
                        parent, false));

            case VIEW_TYPE_SEARCH_MARKET:
                View searchMarketView = mLayoutInflater.inflate(R.layout.all_apps_search_market,
                        parent, false);
                searchMarketView.setOnClickListener(v -> mLauncher.startActivitySafely(v, mMarketSearchIntent, null));
                return new ViewHolder(searchMarketView);

            case VIEW_TYPE_ALL_APPS_DIVIDER:
                return new ViewHolder(mLayoutInflater.inflate(
                        R.layout.all_apps_divider, parent, false));
            case VIEW_TYPE_WORK_TAB_FOOTER:
                View footer = mLayoutInflater.inflate(R.layout.work_tab_footer, parent, false);
                return new ViewHolder(footer);
            case VIEW_TYPE_FOLDER:
                FrameLayout layout = new FrameLayout(mLauncher);

                ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        mLauncher.getDeviceProfile().allAppsCellHeightPx);
                layout.setLayoutParams(lp);
                return new ViewHolder(layout);
            case VIEW_TYPE_SEARCH_SUGGESTION:
                return new ViewHolder(mLayoutInflater.inflate(R.layout.all_apps_search_suggestion, parent, false));

            default:
                throw new RuntimeException("Unexpected view type");
        }
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        switch (holder.getItemViewType()) {
            case VIEW_TYPE_ICON:
                AppInfo info = mApps.getAdapterItems().get(position).appInfo;
                BubbleTextView icon = (BubbleTextView) holder.itemView;
                icon.reset();
                icon.applyFromApplicationInfo(info);
                break;

            case VIEW_TYPE_EMPTY_SEARCH:
                TextView emptyViewText = (TextView) holder.itemView;
                emptyViewText.setText(mEmptySearchMessage);
                emptyViewText.setGravity(mApps.hasNoFilteredResults() ? Gravity.CENTER :
                        Gravity.START | Gravity.CENTER_VERTICAL);
                break;
            case VIEW_TYPE_SEARCH_MARKET:
                TextView searchView = (TextView) holder.itemView;
                if (mMarketSearchIntent != null) {
                    searchView.setVisibility(View.VISIBLE);
                } else {
                    searchView.setVisibility(View.GONE);
                }
                break;

            case VIEW_TYPE_ALL_APPS_DIVIDER:
                // nothing to do
                break;
            case VIEW_TYPE_WORK_TAB_FOOTER:
                WorkModeSwitch workModeToggle = holder.itemView.findViewById(R.id.work_mode_toggle);
                workModeToggle.refresh();
                TextView managedByLabel = holder.itemView.findViewById(R.id.managed_by_label);
                boolean anyProfileQuietModeEnabled = UserManagerCompat.getInstance(
                        managedByLabel.getContext()).isAnyProfileQuietModeEnabled();
                managedByLabel.setText(anyProfileQuietModeEnabled
                        ? R.string.work_mode_off_label : R.string.work_mode_on_label);
                break;
            case VIEW_TYPE_FOLDER:
                ViewGroup container = (ViewGroup) holder.itemView;
                FolderIcon folderIcon = mApps.getAdapterItems().get(position)
                        .folderItem.getFolderIcon(mLauncher, container);

                container.removeAllViews();
                container.addView(folderIcon);

                folderIcon.verifyHighRes();
                break;
            case VIEW_TYPE_SEARCH_SUGGESTION:
                int color = getDrawerTextColor();
                ViewGroup group = (ViewGroup) holder.itemView;
                TextView textView = group.findViewById(R.id.suggestion);
                String suggestion = mApps.getAdapterItems().get(position).suggestion;
                textView.setText(suggestion);
                textView.setTextColor(color);
                ((ImageView) group.findViewById(android.R.id.icon)).getDrawable().setTint(color);
                group.setOnClickListener(v -> {
                    SearchProvider provider = getSearchProvider();
                    if (provider instanceof WebSearchProvider) {
                        ((WebSearchProvider) provider).openResults(suggestion);
                    }
                });
                break;
        }
        if (mBindViewCallback != null) {
            mBindViewCallback.onBindView(holder);
        }
    }

    @Override
    public boolean onFailedToRecycleView(ViewHolder holder) {
        // Always recycle and we will reset the view when it is bound
        return true;
    }

    @Override
    public int getItemCount() {
        return mApps.getAdapterItems().size();
    }

    @Override
    public int getItemViewType(int position) {
        AlphabeticalAppsList.AdapterItem item = mApps.getAdapterItems().get(position);
        return item.viewType;
    }

    public int getDrawerTextColor() {
        return Utilities.getZimPrefs(mLauncher).getAllAppsColor();
    }


    private SearchProvider getSearchProvider() {
        return SearchProviderController.Companion.getInstance(mLauncher).getSearchProvider();
    }

    public SpringAnimationHandler getSpringAnimationHandler() {
        return mSpringAnimationHandler;
    }

    /**
     * Helper class to set the SpringAnimation values for an item in the adapter.
     */
    private class AllAppsSpringAnimationFactory
            implements SpringAnimationHandler.AnimationFactory<ViewHolder> {
        private static final float DEFAULT_MAX_VALUE_PX = 100;
        private static final float DEFAULT_MIN_VALUE_PX = -DEFAULT_MAX_VALUE_PX;

        // Damping ratio range is [0, 1]
        private static final float SPRING_DAMPING_RATIO = 0.55f;

        // Stiffness is a non-negative number.
        private static final float MIN_SPRING_STIFFNESS = 580f;
        private static final float MAX_SPRING_STIFFNESS = 900f;

        // The amount by which each adjacent rows' stiffness will differ.
        private static final float ROW_STIFFNESS_COEFFICIENT = 50f;

        @Override
        public SpringAnimation initialize(ViewHolder vh) {
            return SpringAnimationHandler.forView(vh.itemView, DynamicAnimation.TRANSLATION_Y, 0);
        }

        /**
         * @param spring A new or recycled SpringAnimation.
         * @param vh     The ViewHolder that {@param spring} is related to.
         */
        @Override
        public void update(SpringAnimation spring, ViewHolder vh) {
            int numPredictedApps = Math.min(mAppsPerRow, mApps.getPredictedApps().size());
            int appPosition = getAppPosition(vh.getAdapterPosition(), numPredictedApps,
                    mAppsPerRow);

            int col = appPosition % mAppsPerRow;
            int row = appPosition / mAppsPerRow;

            int numTotalRows = mApps.getNumAppRows() - 1; // zero-based count
            if (row > (numTotalRows / 2)) {
                // Mirror the rows so that the top row acts the same as the bottom row.
                row = Math.abs(numTotalRows - row);
            }

            calculateSpringValues(spring, row, col);
        }

        @Override
        public void setDefaultValues(SpringAnimation spring) {
            calculateSpringValues(spring, 0, mAppsPerRow / 2);
        }

        /**
         * We manipulate the stiffness, min, and max values based on the items distance to the
         * first row and the items distance to the center column to create the ^-shaped motion
         * effect.
         */
        private void calculateSpringValues(SpringAnimation spring, int row, int col) {
            float rowFactor = (1 + row) * 0.5f;
            float colFactor = getColumnFactor(col, mAppsPerRow);

            float minValue = DEFAULT_MIN_VALUE_PX * (rowFactor + colFactor);
            float maxValue = DEFAULT_MAX_VALUE_PX * (rowFactor + colFactor);

            float stiffness = Utilities.boundToRange(
                    MAX_SPRING_STIFFNESS - (row * ROW_STIFFNESS_COEFFICIENT),
                    MIN_SPRING_STIFFNESS,
                    MAX_SPRING_STIFFNESS);

            spring.setMinValue(minValue)
                    .setMaxValue(maxValue)
                    .getSpring()
                    .setStiffness(stiffness)
                    .setDampingRatio(SPRING_DAMPING_RATIO);
        }

        /**
         * @return The app position is the position of the app in the Adapter if we ignored all
         * other view types.
         * <p>
         * The first app is at position 0, and the first app each following row is at a
         * position that is a multiple of {@param appsPerRow}.
         * <p>
         * ie. If there are 5 apps per row, and there are two rows of apps:
         * 0 1 2 3 4
         * 5 6 7 8 9
         */
        private int getAppPosition(int position, int numPredictedApps, int appsPerRow) {
            if (position < numPredictedApps) {
                // Predicted apps are first in the adapter.
                return position;
            }

            // There is at most 1 divider view between the predicted apps and the alphabetical apps.
            int numDividerViews = numPredictedApps == 0 ? 0 : 1;

            // This offset takes into consideration an incomplete row of predicted apps.
            int numPredictedAppsOffset = appsPerRow - numPredictedApps;
            return position + numPredictedAppsOffset - numDividerViews;
        }

        /**
         * Increase the column factor as the distance increases between the column and the center
         * column(s).
         */
        private float getColumnFactor(int col, int numCols) {
            float centerColumn = numCols / 2;
            int distanceToCenter = (int) Math.abs(col - centerColumn);

            boolean evenNumberOfColumns = numCols % 2 == 0;
            if (evenNumberOfColumns && col < centerColumn) {
                distanceToCenter -= 1;
            }

            float factor = 0;
            while (distanceToCenter > 0) {
                if (distanceToCenter == 1) {
                    factor += 0.2f;
                } else {
                    factor += 0.1f;
                }
                --distanceToCenter;
            }

            return factor;
        }
    }
}
