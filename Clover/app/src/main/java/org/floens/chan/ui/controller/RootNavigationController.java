/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.floens.chan.ui.controller;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.floens.chan.Chan;
import org.floens.chan.R;
import org.floens.chan.controller.Controller;
import org.floens.chan.controller.ControllerTransition;
import org.floens.chan.controller.NavigationController;
import org.floens.chan.core.manager.WatchManager;
import org.floens.chan.core.model.Pin;
import org.floens.chan.ui.adapter.PinAdapter;
import org.floens.chan.ui.helper.SwipeListener;
import org.floens.chan.ui.theme.ThemeHelper;
import org.floens.chan.ui.toolbar.Toolbar;
import org.floens.chan.utils.AndroidUtils;

import java.util.List;

import de.greenrobot.event.EventBus;

import static org.floens.chan.ui.theme.ThemeHelper.theme;
import static org.floens.chan.utils.AndroidUtils.ROBOTO_MEDIUM;
import static org.floens.chan.utils.AndroidUtils.dp;
import static org.floens.chan.utils.AndroidUtils.fixSnackbarText;

public class RootNavigationController extends NavigationController implements PinAdapter.Callback, View.OnClickListener {
    private WatchManager watchManager;

    public DrawerLayout drawerLayout;
    public LinearLayout drawer;
    private RecyclerView recyclerView;
    private LinearLayout settings;
    private PinAdapter pinAdapter;

    public RootNavigationController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        watchManager = Chan.getWatchManager();

        EventBus.getDefault().register(this);

        view = inflateRes(R.layout.controller_navigation_drawer);
        toolbar = (Toolbar) view.findViewById(R.id.toolbar);
        container = (FrameLayout) view.findViewById(R.id.container);
        drawerLayout = (DrawerLayout) view.findViewById(R.id.drawer_layout);
        drawerLayout.setDrawerShadow(R.drawable.drawer_shadow, Gravity.LEFT);
        drawer = (LinearLayout) view.findViewById(R.id.drawer);
        recyclerView = (RecyclerView) view.findViewById(R.id.drawer_recycler_view);
        recyclerView.setHasFixedSize(true);
        settings = (LinearLayout) view.findViewById(R.id.settings);
        settings.setOnClickListener(this);
        theme().settingsDrawable.apply((ImageView) settings.findViewById(R.id.image));
        ((TextView) settings.findViewById(R.id.text)).setTypeface(ROBOTO_MEDIUM);

        toolbar.setBackgroundColor(ThemeHelper.getInstance().getTheme().primaryColor.color);

        pinAdapter = new PinAdapter(this);
        recyclerView.setAdapter(pinAdapter);

        new SwipeListener(context, recyclerView, pinAdapter);

        pinAdapter.onPinsChanged(watchManager.getPins());

        toolbar.setCallback(this);

        updateBadge();

        AndroidUtils.waitForMeasure(drawer, new AndroidUtils.OnMeasuredCallback() {
            @Override
            public boolean onMeasured(View view) {
                return setDrawerWidth();
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        AndroidUtils.waitForLayout(drawer, new AndroidUtils.OnMeasuredCallback() {
            @Override
            public boolean onMeasured(View view) {
                return setDrawerWidth();
            }
        });
    }

    @Override
    public void onClick(View v) {
        if (v == settings) {
            pushController(new MainSettingsController(context));
        }
    }

    @Override
    public void onMenuClicked() {
        super.onMenuClicked();

        drawerLayout.openDrawer(drawer);
    }

    @Override
    public boolean onBack() {
        if (drawerLayout.isDrawerOpen(drawer)) {
            drawerLayout.closeDrawer(drawer);
            return true;
        } else {
            return super.onBack();
        }
    }

    @Override
    protected void controllerPushed(Controller controller) {
        super.controllerPushed(controller);
        setDrawerEnabled(controller.navigationItem.hasDrawer);
    }

    @Override
    protected void controllerPopped(Controller controller) {
        super.controllerPopped(controller);
        setDrawerEnabled(controller.navigationItem.hasDrawer);
    }

    @Override
    public void onControllerTransitionCompleted(ControllerTransition transition) {
        super.onControllerTransitionCompleted(transition);
        updateHighlighted();
    }

    public void updateHighlighted() {
        pinAdapter.updateHighlighted(recyclerView);
    }

    @Override
    public void onPinClicked(Pin pin) {
        Controller top = getTop();
        if (top instanceof DrawerCallbacks) {
            ((DrawerCallbacks) top).onPinClicked(pin);
            drawerLayout.closeDrawer(Gravity.LEFT);
            pinAdapter.updateHighlighted(recyclerView);
        }
    }

    public boolean isHighlighted(Pin pin) {
        Controller top = getTop();
        if (top instanceof DrawerCallbacks) {
            return ((DrawerCallbacks) top).isPinCurrent(pin);
        }
        return false;
    }

    @Override
    public void onWatchCountClicked(Pin pin) {
        watchManager.toggleWatch(pin);
    }

    @Override
    public void onHeaderClicked(PinAdapter.HeaderHolder holder) {
        pushController(new WatchSettingsController(context));
    }

    @Override
    public void onPinRemoved(final Pin pin) {
        watchManager.removePin(pin);

        Snackbar snackbar = Snackbar.make(drawerLayout, context.getString(R.string.drawer_pin_removed, pin.loadable.title), Snackbar.LENGTH_LONG);
        fixSnackbarText(context, snackbar);
        snackbar.setAction(R.string.undo, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                watchManager.addPin(pin);
            }
        });
        snackbar.show();
    }

    @Override
    public void onPinLongClocked(final Pin pin) {
        LinearLayout wrap = new LinearLayout(context);
        wrap.setPadding(dp(16), dp(16), dp(16), 0);
        final EditText text = new EditText(context);
        text.setSingleLine();
        text.setText(pin.loadable.title);
        wrap.addView(text, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setPositiveButton(R.string.action_rename, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String value = text.getText().toString();

                        if (!TextUtils.isEmpty(value)) {
                            pin.loadable.title = value;
                            pinAdapter.notifyDataSetChanged();
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .setTitle(R.string.action_rename_pin)
                .setView(wrap)
                .create();

        AndroidUtils.requestKeyboardFocus(dialog, text);

        dialog.show();
    }

    @Override
    public void openBoardEditor() {
        pushController(new BoardEditController(context));
    }

    @Override
    public void openHistory() {
    }

    public void onEvent(WatchManager.PinAddedMessage message) {
        pinAdapter.onPinAdded(message.pin);
        drawerLayout.openDrawer(drawer);
        updateBadge();
    }

    public void onEvent(WatchManager.PinRemovedMessage message) {
        pinAdapter.onPinRemoved(message.pin);
        updateBadge();
    }

    public void onEvent(WatchManager.PinChangedMessage message) {
        pinAdapter.onPinChanged(recyclerView, message.pin);
        updateBadge();
    }

    private void setDrawerEnabled(boolean enabled) {
        drawerLayout.setDrawerLockMode(enabled ? DrawerLayout.LOCK_MODE_UNLOCKED : DrawerLayout.LOCK_MODE_LOCKED_CLOSED, Gravity.LEFT);
    }

    private void updateBadge() {
        List<Pin> list = watchManager.getWatchingPins();
        int count = 0;
        boolean color = false;
        if (list.size() > 0) {
            for (Pin p : list) {
                count += p.getNewPostCount();
                if (p.getNewQuoteCount() > 0) {
                    color = true;
                }
            }
        }

        toolbar.getArrowMenuDrawable().setBadge(count, color);
    }

    private boolean setDrawerWidth() {
        int width = Math.min(view.getWidth() - dp(56), dp(56) * 6);
        if (drawer.getWidth() != width) {
            drawer.getLayoutParams().width = width;
            drawer.requestLayout();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public String getSearchHint() {
        return context.getString(R.string.search_hint);
    }

    @Override
    public void onSearchVisibilityChanged(boolean visible) {
        Controller top = getTop();
        if (top instanceof DrawerCallbacks) {
            ((DrawerCallbacks) top).onSearchVisibilityChanged(visible);
        }
    }

    @Override
    public void onSearchEntered(String entered) {
        Controller top = getTop();
        if (top instanceof DrawerCallbacks) {
            ((DrawerCallbacks) top).onSearchEntered(entered);
        }
    }

    public interface DrawerCallbacks {
        void onPinClicked(Pin pin);

        boolean isPinCurrent(Pin pin);

        void onSearchVisibilityChanged(boolean visible);

        void onSearchEntered(String entered);
    }
}
