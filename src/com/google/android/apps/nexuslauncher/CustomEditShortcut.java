package com.google.android.apps.nexuslauncher;

import android.content.Context;
import android.view.View;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.popup.SystemShortcut;

import org.zimmob.zimlx.override.CustomInfoProvider;

import androidx.annotation.Keep;

@Keep
public class CustomEditShortcut extends SystemShortcut.Custom {
    public CustomEditShortcut(Context context) {
        super();
    }

    @Override
    public View.OnClickListener getOnClickListener(final Launcher launcher, final ItemInfo itemInfo) {
        boolean enabled = CustomInfoProvider.Companion.isEditable(itemInfo);
        return enabled ? new View.OnClickListener() {
            private boolean mOpened = false;

            @Override
            public void onClick(View view) {
                if (!mOpened) {
                    mOpened = true;
                    AbstractFloatingView.closeAllOpenViews(launcher);
                    CustomBottomSheet.show(launcher, itemInfo);
                }
            }
        } : null;
    }
}
