package org.zimmob.zimlx.activity.homeparts;

import android.graphics.Point;

import org.zimmob.zimlx.R;
import org.zimmob.zimlx.activity.Home;
import org.zimmob.zimlx.interfaces.DialogListener;
import org.zimmob.zimlx.manager.Setup;
import org.zimmob.zimlx.model.Item;
import org.zimmob.zimlx.util.Tool;

public class HpDesktopPickAction implements DialogListener.OnAddAppDrawerItemListener {
    private Home _home;

    public HpDesktopPickAction(Home home) {
        _home = home;
    }

    public void onPickDesktopAction() {
        Setup.eventHandler().showPickAction(_home, this);
    }

    @Override
    public void onAdd(int type) {
        Point pos = _home.getDesktop().getCurrentPage().findFreeSpace();
        if (pos != null) {
            _home.getDesktop().addItemToCell(Item.newActionItem(type), pos.x, pos.y);
        } else {
            Tool.toast(_home, R.string.toast_not_enough_space);
        }
    }
}