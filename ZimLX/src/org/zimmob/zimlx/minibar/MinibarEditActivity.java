package org.zimmob.zimlx.minibar;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.widget.CompoundButtonCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.mikepenz.fastadapter.commons.adapters.FastItemAdapter;
import com.mikepenz.fastadapter.items.AbstractItem;
import com.mikepenz.fastadapter_extensions.drag.ItemTouchCallback;
import com.mikepenz.fastadapter_extensions.drag.SimpleDragCallback;

import org.zimmob.zimlx.ZimPreferences;
import org.zimmob.zimlx.theme.ThemeOverride;
import org.zimmob.zimlx.util.ThemeActivity;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MinibarEditActivity extends ThemeActivity implements ItemTouchCallback {
    @BindView(R.id.toolbar)
    public Toolbar toolbar;

    @BindView(R.id.enableSwitch)
    public SwitchCompat _enableSwitch;

    @BindView(R.id.recyclerView)
    public RecyclerView _recyclerView;
    private FastItemAdapter<Item> _adapter;
    private Launcher mLauncher;
    private ZimPreferences prefs;
    private ThemeOverride themeOverride;
    private int currentTheme = 0;
    private ThemeOverride.ThemeSet themeSet;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        themeSet = new ThemeOverride.Settings();
        themeOverride = new ThemeOverride(themeSet, this);
        themeOverride.applyTheme(this);
        currentTheme = themeOverride.getTheme(this);
        super.onCreate(savedInstanceState);
        Utilities.setupPirateLocale(this);

        setContentView(R.layout.activity_minibar_edit);
        ButterKnife.bind(this);

        setSupportActionBar(toolbar);
        prefs = Utilities.getZimPrefs(this);
        toolbar.setBackgroundColor(prefs.getPrimaryColor());
        toolbar.setTitleTextColor(getResources().getColor(R.color.white));
        toolbar.setNavigationIcon(getResources().getDrawable(R.drawable.ic_arrow_back_white_24px));
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        setTitle(R.string.minibar);

        _adapter = new FastItemAdapter<>();

        SimpleDragCallback touchCallback = new SimpleDragCallback(this);
        ItemTouchHelper touchHelper = new ItemTouchHelper(touchCallback);
        touchHelper.attachToRecyclerView(_recyclerView);

        _recyclerView.setLayoutManager(new LinearLayoutManager(this));
        _recyclerView.setAdapter(_adapter);

        mLauncher = Launcher.getLauncher(Launcher.mContext);

        final Set<String> minibarItems = prefs.getMinibarItems();
        for (DashModel item : DashUtils.actionDisplayItems) {
            _adapter.add(new Item(item.id, item, minibarItems.contains(Integer.toString(item.id))));
        }

        int[][] states = new int[][]{
                new int[]{-android.R.attr.state_enabled},
                new int[]{android.R.attr.state_checked},
                new int[]{},
        };
        int[] colors = new int[]{
                androidx.preference.R.color.switch_thumb_normal_material_light,//Normal
                prefs.getAccentColor(), //checked
                androidx.preference.R.color.switch_thumb_disabled_material_light//Disabled
        };
        ColorStateList thstateList = new ColorStateList(states, colors);
        boolean minBarEnable = prefs.getMinibarEnable();
        _enableSwitch.setChecked(minBarEnable);
        _enableSwitch.setThumbTintList(thstateList);
        _enableSwitch.setText(minBarEnable ? R.string.on : R.string.off);
        _enableSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            buttonView.setText(isChecked ? R.string.on : R.string.off);
            prefs.setMinibarEnable(isChecked);
            mLauncher.getDrawerLayout().setDrawerLockMode(isChecked ? DrawerLayout.LOCK_MODE_UNLOCKED : DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        });
        setResult(RESULT_OK);
        updateUpButton();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        updateUpButton();
    }

    private void updateUpButton() {
        updateUpButton(getSupportFragmentManager().getBackStackEntryCount() != 0);
    }

    private void updateUpButton(boolean enabled) {
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(enabled);
    }

    @Override
    protected void onPause() {
        Set<String> minibarArrangement = new HashSet<>();
        for (Item item : _adapter.getAdapterItems()) {
            if (item.enable)
                minibarArrangement.add(Long.toString(item.id));

        }
        prefs.setMinibarItems(minibarArrangement);
        super.onPause();
    }

    @Override
    protected void onStop() {
        if (mLauncher != null) {
            mLauncher.initMinibar();
        }

        super.onStop();
    }

    @Override
    public boolean itemTouchOnMove(int oldPosition, int newPosition) {
        Collections.swap(_adapter.getAdapterItems(), oldPosition, newPosition);
        _adapter.notifyAdapterDataSetChanged();

        Set<String> minibarArrangement = new HashSet<>();
        for (Item item : _adapter.getAdapterItems()) {
            if (item.enable)
                minibarArrangement.add(Long.toString(item.id));

        }
        prefs.setMinibarItems(minibarArrangement);

        return false;
    }

    @Override
    public void itemTouchDropped(int i, int i1) {
    }

    public static class Item extends AbstractItem<Item, Item.ViewHolder> {
        final long id;
        final DashModel item;
        boolean enable;
        boolean edited;

        Item(long id, DashModel item, boolean enable) {
            this.id = id;
            this.item = item;
            this.enable = enable;
        }

        @Override
        public int getType() {
            return 0;
        }

        @Override
        public int getLayoutRes() {
            return R.layout.item_minibar_edit;
        }

        @NonNull
        @Override
        public ViewHolder getViewHolder(@NonNull View v) {
            return new ViewHolder(v);
        }

        public void bindView(@NonNull ViewHolder holder, List payloads) {
            int[][] states = new int[][]{
                    new int[]{-android.R.attr.state_enabled},
                    new int[]{android.R.attr.state_checked},
                    new int[]{},
            };
            int[] colors = new int[]{
                    androidx.preference.R.color.switch_thumb_normal_material_light,//Normal
                    ZimPreferences.Companion.getInstance(Launcher.mContext).getAccentColor(), //checked
                    androidx.preference.R.color.switch_thumb_disabled_material_light//Disabled
            };
            ColorStateList thstateList = new ColorStateList(states, colors);

            holder._tv.setText(item.label);
            holder._tv2.setText(item.description);
            holder._iv.setImageResource(item.icon);

            holder._cb.setChecked(enable);
            CompoundButtonCompat.setButtonTintList(holder._cb, thstateList);
            holder._cb.setOnCheckedChangeListener((compoundButton, b) -> {
                edited = true;
                enable = b;
            });
            super.bindView(holder, payloads);
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView _tv;
            TextView _tv2;
            ImageView _iv;
            CheckBox _cb;

            ViewHolder(View itemView) {
                super(itemView);
                _tv = itemView.findViewById(R.id.tv);
                _tv2 = itemView.findViewById(R.id.tv2);
                _iv = itemView.findViewById(R.id.iv);
                _cb = itemView.findViewById(R.id.cb);
            }
        }
    }
}
