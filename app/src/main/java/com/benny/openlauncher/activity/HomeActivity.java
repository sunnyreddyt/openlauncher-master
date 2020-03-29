package com.benny.openlauncher.activity;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.util.ArrayMap;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.benny.openlauncher.BuildConfig;
import com.benny.openlauncher.R;
import com.benny.openlauncher.activity.homeparts.HpAppDrawer;
import com.benny.openlauncher.activity.homeparts.HpDesktopOption;
import com.benny.openlauncher.activity.homeparts.HpDragOption;
import com.benny.openlauncher.activity.homeparts.HpInitSetup;
import com.benny.openlauncher.activity.homeparts.HpSearchBar;
import com.benny.openlauncher.interfaces.AppDeleteListener;
import com.benny.openlauncher.interfaces.AppUpdateListener;
import com.benny.openlauncher.manager.Setup;
import com.benny.openlauncher.model.App;
import com.benny.openlauncher.model.Item;
import com.benny.openlauncher.model.TimerListModel;
import com.benny.openlauncher.notifications.NotificationListener;
import com.benny.openlauncher.receivers.AppUpdateReceiver;
import com.benny.openlauncher.receivers.ShortcutReceiver;
import com.benny.openlauncher.util.AppManager;
import com.benny.openlauncher.util.AppSettings;
import com.benny.openlauncher.util.DatabaseHelper;
import com.benny.openlauncher.util.Definitions.ItemPosition;
import com.benny.openlauncher.util.IconPackHelper;
import com.benny.openlauncher.util.LauncherAction;
import com.benny.openlauncher.util.LauncherAction.Action;
import com.benny.openlauncher.util.Tool;
import com.benny.openlauncher.viewutil.DialogHelper;
import com.benny.openlauncher.viewutil.MinibarAdapter;
import com.benny.openlauncher.viewutil.WidgetHost;
import com.benny.openlauncher.widget.AppDrawerController;
import com.benny.openlauncher.widget.AppItemView;
import com.benny.openlauncher.widget.Desktop;
import com.benny.openlauncher.widget.Desktop.OnDesktopEditListener;
import com.benny.openlauncher.widget.DesktopOptionView;
import com.benny.openlauncher.widget.Dock;
import com.benny.openlauncher.widget.GroupPopupView;
import com.benny.openlauncher.widget.ItemOptionView;
import com.benny.openlauncher.widget.MinibarView;
import com.benny.openlauncher.widget.PagerIndicator;
import com.benny.openlauncher.widget.SearchBar;
import com.jakewharton.threetenabp.AndroidThreeTen;

import net.gsantner.opoc.util.ContextUtils;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class HomeActivity extends Activity implements OnDesktopEditListener {
    public static final Companion Companion = new Companion();
    public static final int REQUEST_CREATE_APPWIDGET = 0x6475;
    public static final int REQUEST_PERMISSION_STORAGE = 0x3648;
    public static final int REQUEST_PICK_APPWIDGET = 0x2678;
    public static WidgetHost _appWidgetHost;
    public static AppWidgetManager _appWidgetManager;
    public static boolean ignoreResume;
    public static float _itemTouchX;
    public static float _itemTouchY;
    public ArrayList<String> _listActivitiesHidden;
    // static launcher variables
    public static HomeActivity _launcher;
    public static DatabaseHelper _db;
    public static HpDesktopOption _desktopOption;

    // receiver variables
    private static final IntentFilter _appUpdateIntentFilter = new IntentFilter();
    private static final IntentFilter _shortcutIntentFilter = new IntentFilter();
    private static final IntentFilter _timeChangedIntentFilter = new IntentFilter();
    private AppUpdateReceiver _appUpdateReceiver;
    private ShortcutReceiver _shortcutReceiver;
    private BroadcastReceiver _timeChangedReceiver;

    private int cx;
    private int cy;
    Context context;
    private UsageStatsManager mUsageStatsManager;
    private PackageManager mPm;

    public static final class Companion {
        private Companion() {
        }

        public final HomeActivity getLauncher() {
            return _launcher;
        }

        public final void setLauncher(@Nullable HomeActivity v) {
            _launcher = v;
        }
    }

    static {
        _timeChangedIntentFilter.addAction(Intent.ACTION_TIME_TICK);
        _timeChangedIntentFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        _timeChangedIntentFilter.addAction(Intent.ACTION_TIME_CHANGED);
        _appUpdateIntentFilter.addDataScheme("package");
        _appUpdateIntentFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        _appUpdateIntentFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        _appUpdateIntentFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        _shortcutIntentFilter.addAction("com.android.launcher.action.INSTALL_SHORTCUT");
    }

    public final DrawerLayout getDrawerLayout() {
        return findViewById(R.id.drawer_layout);
    }

    public final Desktop getDesktop() {
        return findViewById(R.id.desktop);
    }

    public final Dock getDock() {
        return findViewById(R.id.dock);
    }

    public final AppDrawerController getAppDrawerController() {
        return findViewById(R.id.appDrawerController);
    }

    public final GroupPopupView getGroupPopup() {
        return findViewById(R.id.groupPopup);
    }

    public final SearchBar getSearchBar() {
        return findViewById(R.id.searchBar);
    }

    public final View getBackground() {
        return findViewById(R.id.background_frame);
    }

    public final PagerIndicator getDesktopIndicator() {
        return findViewById(R.id.desktopIndicator);
    }

    public final DesktopOptionView getDesktopOptionView() {
        return findViewById(R.id.desktop_option);
    }

    public final ItemOptionView getItemOptionView() {
        return findViewById(R.id.item_option);
    }

    public final FrameLayout getMinibarFrame() {
        return findViewById(R.id.minibar_frame);
    }

    public final View getStatusView() {
        return findViewById(R.id.status_frame);
    }

    public final View getNavigationView() {
        return findViewById(R.id.navigation_frame);
    }

    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Companion.setLauncher(this);
        context = HomeActivity.this;
        mUsageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        mPm = getPackageManager();
        AndroidThreeTen.init(this);

        AppSettings appSettings = AppSettings.get();

        ContextUtils contextUtils = new ContextUtils(getApplicationContext());
        contextUtils.setAppLanguage(appSettings.getLanguage());
        super.onCreate(savedInstanceState);
        if (!Setup.wasInitialised()) {
            Setup.init(new HpInitSetup(this));
        }

        Companion.setLauncher(this);
        _db = Setup.dataManager();

        setContentView(getLayoutInflater().inflate(R.layout.activity_home, null));

        // transparent status and navigation
        if (VERSION.SDK_INT >= 21) {
            Window window = getWindow();
            View decorView = window.getDecorView();
            decorView.setSystemUiVisibility(1536);
        }

        init();
    }

    private void init() {
        _appWidgetManager = AppWidgetManager.getInstance(this);
        _appWidgetHost = new WidgetHost(getApplicationContext(), R.id.app_widget_host);
        _appWidgetHost.startListening();

        // item drag and drop
        HpDragOption hpDragOption = new HpDragOption();
        View findViewById = findViewById(R.id.leftDragHandle);
        View findViewById2 = findViewById(R.id.rightDragHandle);
        hpDragOption.initDragNDrop(this, findViewById, findViewById2, getItemOptionView());

        registerBroadcastReceiver();
        initAppManager();
        initSettings();
        initViews();
    }

    protected void initAppManager() {
        if (Setup.appSettings().getAppFirstLaunch()) {
            Setup.appSettings().setAppFirstLaunch(false);
            Setup.appSettings().setAppShowIntro(false);
            Item appDrawerBtnItem = Item.newActionItem(8);
            appDrawerBtnItem._x = 2;
            _db.saveItem(appDrawerBtnItem, 0, ItemPosition.Dock);
        }
        Setup.appLoader().addUpdateListener(new AppUpdateListener() {
            @Override
            public boolean onAppUpdated(List<App> it) {
                getDesktop().initDesktop();
                getDock().initDock();
                return false;
            }
        });
        Setup.appLoader().addDeleteListener(new AppDeleteListener() {
            @Override
            public boolean onAppDeleted(List<App> apps) {
                getDesktop().initDesktop();
                getDock().initDock();
                return false;
            }
        });

        new AsyncGetApps().execute();
        //AppManager.getInstance(this).init();
    }


    private class AsyncGetApps extends AsyncTask {

        List<App> _apps = new ArrayList<App>();
        private List<App> _nonFilteredApps = new ArrayList<>();
        private PackageManager _packageManager = context.getPackageManager();
        private List<App> tempApps;

        @Override
        protected void onPreExecute() {
            tempApps = new ArrayList<>(_apps);
            super.onPreExecute();
        }

        @Override
        protected void onCancelled() {
            tempApps = null;
            super.onCancelled();
        }

        @Override
        protected Object doInBackground(Object[] p1) {
            _nonFilteredApps.clear();

            // work profile support
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                LauncherApps launcherApps = (LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);
                List<UserHandle> profiles = launcherApps.getProfiles();
                for (UserHandle userHandle : profiles) {
                    List<LauncherActivityInfo> apps = launcherApps.getActivityList(null, userHandle);
                    for (LauncherActivityInfo info : apps) {
                        App app = new App(_packageManager, info);
                        app._userHandle = userHandle;

                        _nonFilteredApps.add(app);
                    }
                }
            } else {
                Intent intent = new Intent(Intent.ACTION_MAIN, null);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> activitiesInfo = _packageManager.queryIntentActivities(intent, 0);
                for (ResolveInfo info : activitiesInfo) {
                    App app = new App(_packageManager, info);

                    _nonFilteredApps.add(app);
                }
            }

            // sort the apps by label here
            Collections.sort(_nonFilteredApps, new Comparator<App>() {
                @Override
                public int compare(App one, App two) {
                    return Collator.getInstance().compare(one._label, two._label);
                }
            });

            List<String> hiddenList = AppSettings.get().getHiddenAppsList();
            if (hiddenList != null) {
                for (int i = 0; i < _nonFilteredApps.size(); i++) {
                    boolean shouldGetAway = false;
                    for (String hidItemRaw : hiddenList) {
                        if ((_nonFilteredApps.get(i).getPackageName()).equals(hidItemRaw)) {
                            shouldGetAway = true;
                            break;
                        }
                    }

                    if (!shouldGetAway) {
                        _apps.add(_nonFilteredApps.get(i));
                    }
                }
            } else {
                _apps.addAll(_nonFilteredApps);
            }


            // requestPermissions();

            /*AppSettings appSettings = AppSettings.get();
            if (!appSettings.getIconPack().isEmpty() && Tool.isPackageInstalled(appSettings.getIconPack(), _packageManager)) {
                IconPackHelper.applyIconPack(AppManager.this, Tool.dp2px(appSettings.getIconSize()), appSettings.getIconPack(), _apps);
            }*/
            return null;
        }

        @Override
        protected void onPostExecute(Object result) {

            requestPermissions(_apps);

            /*notifyUpdateListeners(_apps);

            List<App> removed = getRemovedApps(tempApps, _apps);
            if (removed.size() > 0) {
                notifyRemoveListeners(removed);
            }

            if (_recreateAfterGettingApps) {
                _recreateAfterGettingApps = false;
                if (_context instanceof HomeActivity)
                    ((HomeActivity) _context).recreate();
            }*/

            super.onPostExecute(result);
        }
    }

    protected void initViews() {
        new HpSearchBar(this, getSearchBar()).initSearchBar();
        getAppDrawerController().init();
        getDock().setHome(this);

        getDesktop().setDesktopEditListener(this);
        getDesktop().setPageIndicator(getDesktopIndicator());
        getDesktopIndicator().setMode(Setup.appSettings().getDesktopIndicatorMode());

        AppSettings appSettings = Setup.appSettings();

        _desktopOption = new HpDesktopOption(this);

        getDesktopOptionView().setDesktopOptionViewListener(_desktopOption);
        getDesktopOptionView().postDelayed(new Runnable() {
            @Override
            public void run() {
                getDesktopOptionView().updateLockIcon(appSettings.getDesktopLock());
            }
        }, 100);

        getDesktop().addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            public void onPageSelected(int position) {
                getDesktopOptionView().updateHomeIcon(appSettings.getDesktopPageCurrent() == position);
            }

            public void onPageScrollStateChanged(int state) {
            }
        });

        new HpAppDrawer(this, findViewById(R.id.appDrawerIndicator)).initAppDrawer(getAppDrawerController());
        initMinibar();
    }

    public final void initMinibar() {
        final ArrayList<LauncherAction.ActionDisplayItem> items = AppSettings.get().getMinibarArrangement();
        MinibarView minibar = findViewById(R.id.minibar);
        minibar.setAdapter(new MinibarAdapter(this, items));
        minibar.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int i, long id) {
                LauncherAction.RunAction(items.get(i), HomeActivity.this);
            }
        });
    }

    public final void initSettings() {
        updateHomeLayout();

        AppSettings appSettings = Setup.appSettings();
        if (appSettings.getDesktopFullscreen()) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        // set background colors
        getDesktop().setBackgroundColor(appSettings.getDesktopBackgroundColor());
        getDock().setBackgroundColor(appSettings.getDockColor());

        // set frame colors
        getMinibarFrame().setBackgroundColor(appSettings.getMinibarBackgroundColor());
        getStatusView().setBackgroundColor(appSettings.getDesktopInsetColor());
        getNavigationView().setBackgroundColor(appSettings.getDesktopInsetColor());

        // lock the minibar
        getDrawerLayout().setDrawerLockMode(appSettings.getMinibarEnable() ? DrawerLayout.LOCK_MODE_UNLOCKED : DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
    }

    private void registerBroadcastReceiver() {
        _appUpdateReceiver = new AppUpdateReceiver();
        _shortcutReceiver = new ShortcutReceiver();
        _timeChangedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(Intent.ACTION_TIME_TICK)
                        || intent.getAction().equals(Intent.ACTION_TIMEZONE_CHANGED)
                        || intent.getAction().equals(Intent.ACTION_TIME_CHANGED)) {
                    updateSearchClock();
                }
            }
        };

        // register all receivers
        registerReceiver(_appUpdateReceiver, _appUpdateIntentFilter);
        registerReceiver(_shortcutReceiver, _shortcutIntentFilter);
        registerReceiver(_timeChangedReceiver, _timeChangedIntentFilter);
    }

    public final void onStartApp(@NonNull Context context, @NonNull App app, @Nullable View view) {
        if (BuildConfig.APPLICATION_ID.equals(app._packageName)) {
            LauncherAction.RunAction(Action.LauncherSettings, context);
            return;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && app._userHandle != null) {
                LauncherApps launcherApps = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
                List<LauncherActivityInfo> activities = launcherApps.getActivityList(app.getPackageName(), app._userHandle);
                for (int intent = 0; intent < activities.size(); intent++) {
                    if (app.getComponentName().equals(activities.get(intent).getComponentName().toString()))
                        launcherApps.startMainActivity(activities.get(intent).getComponentName(), app._userHandle, null, getActivityAnimationOpts(view));
                }
            } else {
                Intent intent = Tool.getIntentFromApp(app);
                context.startActivity(intent, getActivityAnimationOpts(view));
            }

            // close app drawer and other items in advance
            // annoying to wait for app drawer to close
            handleLauncherResume();
        } catch (Exception e) {
            e.printStackTrace();
            Tool.toast(context, R.string.toast_app_uninstalled);
        }
    }

    private Bundle getActivityAnimationOpts(View view) {
        Bundle bundle = null;
        if (view == null) {
            return null;
        }

        ActivityOptions options = null;
        if (VERSION.SDK_INT >= 23) {
            int left = 0;
            int top = 0;
            int width = view.getMeasuredWidth();
            int height = view.getMeasuredHeight();
            if (view instanceof AppItemView) {
                width = (int) ((AppItemView) view).getIconSize();
                left = (int) ((AppItemView) view).getDrawIconLeft();
                top = (int) ((AppItemView) view).getDrawIconTop();
            }
            options = ActivityOptions.makeClipRevealAnimation(view, left, top, width, height);
        } else if (VERSION.SDK_INT < 21) {
            options = ActivityOptions.makeScaleUpAnimation(view, 0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
        }

        if (options != null) {
            bundle = options.toBundle();
        }

        return bundle;
    }

    public void onStartDesktopEdit() {
        Tool.visibleViews(100, getDesktopOptionView());
        updateDesktopIndicator(false);
        updateDock(false);
        updateSearchBar(false);
    }

    public void onFinishDesktopEdit() {
        Tool.invisibleViews(100, getDesktopOptionView());
        updateDesktopIndicator(true);
        updateDock(true);
        updateSearchBar(true);
    }

    public final void dimBackground() {
        Tool.visibleViews(200, getBackground());
    }

    public final void unDimBackground() {
        Tool.invisibleViews(200, getBackground());
    }

    public final void clearRoomForPopUp() {
        Tool.invisibleViews(200, getDesktop());
        updateDesktopIndicator(false);
        updateDock(false);
    }

    public final void unClearRoomForPopUp() {
        Tool.visibleViews(200, getDesktop());
        updateDesktopIndicator(true);
        updateDock(true);
    }

    public final void updateDock(boolean show) {
        AppSettings appSettings = Setup.appSettings();
        if (appSettings.getDockEnable() && show) {
            Tool.visibleViews(100, getDock());
        } else {
            if (appSettings.getDockEnable()) {
                Tool.invisibleViews(100, getDock());
            } else {
                Tool.goneViews(100, getDock());
            }
        }
    }

    public final void updateSearchBar(boolean show) {
        AppSettings appSettings = Setup.appSettings();
        if (appSettings.getSearchBarEnable() && show) {
            Tool.visibleViews(100, getSearchBar());
        } else {
            if (appSettings.getSearchBarEnable()) {
                Tool.invisibleViews(100, getSearchBar());
            } else {
                Tool.goneViews(100, getSearchBar());
            }
        }
    }

    public final void updateDesktopIndicator(boolean show) {
        AppSettings appSettings = Setup.appSettings();
        if (appSettings.getDesktopShowIndicator() && show) {
            Tool.visibleViews(100, getDesktopIndicator());
        } else {
            Tool.goneViews(100, getDesktopIndicator());
        }
    }

    public final void updateSearchClock() {
        TextView textView = getSearchBar()._searchClock;

        if (textView.getText() != null) {
            try {
                getSearchBar().updateClock();
            } catch (Exception e) {
                getSearchBar()._searchClock.setText(R.string.bad_format);
            }
        }
    }

    public final void updateHomeLayout() {
        updateSearchBar(true);
        updateDock(true);
        updateDesktopIndicator(true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_PICK_APPWIDGET) {
                _desktopOption.configureWidget(data);
            } else if (requestCode == REQUEST_CREATE_APPWIDGET) {
                _desktopOption.createWidget(data);
            }
        } else if (resultCode == RESULT_CANCELED && data != null) {
            int appWidgetId = data.getIntExtra("appWidgetId", -1);
            if (appWidgetId != -1) {
                _appWidgetHost.deleteAppWidgetId(appWidgetId);
            }
        }
    }

    @Override
    public void onBackPressed() {
        handleLauncherResume();
    }

    @Override
    protected void onStart() {
        _appWidgetHost.startListening();
        _launcher = this;

        super.onStart();
    }

    private void checkNotificationPermissions() {
        Set<String> appList = NotificationManagerCompat.getEnabledListenerPackages(this);
        for (String app : appList) {
            if (app.equals(getPackageName())) {
                // Already allowed, so request a full update when returning to the home screen from another app.
                Intent i = new Intent(NotificationListener.UPDATE_NOTIFICATIONS_ACTION);
                i.setPackage(getPackageName());
                i.putExtra(NotificationListener.UPDATE_NOTIFICATIONS_COMMAND, NotificationListener.UPDATE_NOTIFICATIONS_UPDATE);
                sendBroadcast(i);
                return;
            }
        }

        // Request the required permission otherwise.
        DialogHelper.alertDialog(this, getString(R.string.notification_title), getString(R.string.notification_summary), getString(R.string.enable), new MaterialDialog.SingleButtonCallback() {
            @Override
            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                Tool.toast(HomeActivity.this, getString(R.string.toast_notification_permission_required));
                startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        _appWidgetHost.startListening();
        _launcher = this;

        // handle restart if something needs to be reset
        AppSettings appSettings = Setup.appSettings();
        if (appSettings.getAppRestartRequired()) {
            appSettings.setAppRestartRequired(false);
            recreate();
            return;
        }

        if (appSettings.getNotificationStatus()) {
            // Ask user to allow the Notification permission if not already provided.
            checkNotificationPermissions();
        }

        // handle launcher rotation
        if (appSettings.getDesktopOrientationMode() == 2) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else if (appSettings.getDesktopOrientationMode() == 1) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        handleLauncherResume();

        new AsyncGetApps().execute();
    }

    @Override
    protected void onDestroy() {
        _appWidgetHost.stopListening();
        _launcher = null;

        unregisterReceiver(_appUpdateReceiver);
        unregisterReceiver(_shortcutReceiver);
        unregisterReceiver(_timeChangedReceiver);
        super.onDestroy();
    }

    private void handleLauncherResume() {
        if (ignoreResume) {
            // only triggers when a new activity is launched that should leave launcher state alone
            // uninstall package activity and pick widget activity
            ignoreResume = false;
        } else {
            getSearchBar().collapse();
            getGroupPopup().collapse();
            // close app option menu
            getItemOptionView().collapse();
            // close minibar
            getDrawerLayout().closeDrawers();
            if (getDesktop().getInEditMode()) {
                // exit desktop edit mode
                getDesktop().getCurrentPage().performClick();
            } else if (getAppDrawerController().getDrawer().getVisibility() == View.VISIBLE) {
                closeAppDrawer();
            } else if (getDesktop().getCurrentItem() != 0) {
                AppSettings appSettings = Setup.appSettings();
                getDesktop().setCurrentItem(appSettings.getDesktopPageCurrent());
            }
        }
    }

    public final void openAppDrawer() {
        openAppDrawer(null, 0, 0);
    }

    public final void openAppDrawer(View view, int x, int y) {
        if (!(x > 0 && y > 0) && view != null) {
            int[] pos = new int[2];
            view.getLocationInWindow(pos);
            cx = pos[0];
            cy = pos[1];

            cx += view.getWidth() / 2f;
            cy += view.getHeight() / 2f;
            if (view instanceof AppItemView) {
                AppItemView appItemView = (AppItemView) view;
                if (appItemView != null && appItemView.getShowLabel()) {
                    cy -= Tool.dp2px(14) / 2f;
                }
            }
            cy -= getAppDrawerController().getPaddingTop();
        } else {
            cx = x;
            cy = y;
        }
        getAppDrawerController().open(cx, cy);
    }

    public final void closeAppDrawer() {
        getAppDrawerController().close(cx, cy);
    }

    private void requestPermissions(List<App> _apps) {
        List<UsageStats> stats = mUsageStatsManager
                .queryUsageStats(UsageStatsManager.INTERVAL_DAILY, 0, System.currentTimeMillis());
        boolean isEmpty = stats.isEmpty();
        if (isEmpty) {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        } else {
            getData(_apps);
        }
    }

    public void getData(List<App> _apps) {
        ArrayMap<String, String> mAppLabelMap = new ArrayMap<>();
        ArrayList<UsageStats> mPackageStats = new ArrayList<>();
        Map<String, UsageStats> allAppsUsageStats = new ArrayMap<>();
        _listActivitiesHidden = new ArrayList<String>();
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -1);

        final List<UsageStats> stats =
                mUsageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY,
                        cal.getTimeInMillis(), System.currentTimeMillis());
        if (stats == null) {
            return;
        }

        ArrayMap<String, UsageStats> map = new ArrayMap<>();
        final int statCount = stats.size();
        for (int i = 0; i < statCount; i++) {
            final android.app.usage.UsageStats pkgStats = stats.get(i);

            // load application labels for each application
            try {
                ApplicationInfo appInfo = mPm.getApplicationInfo(pkgStats.getPackageName(), 0);
                String label = appInfo.loadLabel(mPm).toString();
                //mAppLabelMap.put(pkgStats.getPackageName(), label);

                UsageStats existingStats =
                        map.get(pkgStats.getPackageName());
                if (existingStats == null) {
                    map.put(pkgStats.getPackageName(), pkgStats);
                } else {
                    existingStats.add(pkgStats);
                }

            } catch (PackageManager.NameNotFoundException e) {
                // This package may be gone.
            }
        }
        mPackageStats.addAll(map.values());

        for (int g = 0; g < mPackageStats.size(); g++) {
            if (mPackageStats.get(g).getPackageName().equalsIgnoreCase("com.mcdonalds.app.qa")) {
                //Log.v("appsTime", String.valueOf(mPackageStats.get(g).getPackageName()) + " : " + String.valueOf(DateUtils.formatElapsedTime(mPackageStats.get(g).getTotalTimeInForeground() / 1000)));
                Log.v("appsTime", String.valueOf(mPackageStats.get(g).getPackageName()) + " : " + String.valueOf(mPackageStats.get(g).getTotalTimeInForeground() / 60000));
            }
            allAppsUsageStats.put(String.valueOf(mPackageStats.get(g).getPackageName()), mPackageStats.get(g));
        }

        AppSettings appSettings = new AppSettings(HomeActivity.this);
        List<String> hiddenList = appSettings.getHiddenAppsList();
        _listActivitiesHidden.addAll(hiddenList);

        /*TimerListModel timerListModel = AppSettings.get().getTimerListModel();
        if (timerListModel!=null) {
        ArrayList<Timer> timerArrayList = timerListModel.getTimerArrayList();
        if (timerArrayList!=null) {
            for (int p = 0; p < timerArrayList.size(); p++) {
                UsageStats usageStats = allAppsUsageStats.get(timerArrayList.get(p).getPackageName());
                if (Integer.parseInt(timerArrayList.get(p).getTime()) > (usageStats.getTotalTimeInForeground() / 60000)){
                    _listActivitiesHidden.add(timerArrayList.get(p).getPackageName());
                }
            }
            AppSettings.get().setHiddenAppsList(_listActivitiesHidden);
        }
        }*/

        //test
        /*for (int p = 0; p < mPackageStats.size(); p++) {
            if ((mPackageStats.get(p).getTotalTimeInForeground() / 60000) > 30){
                if (!_listActivitiesHidden.contains(mPackageStats.get(p).getPackageName())) {
                    _listActivitiesHidden.add(mPackageStats.get(p).getPackageName());
                    Log.v("allAppsUsageStats", mPackageStats.get(p).getPackageName());
                }
            }
        }*/


        Map<String, String> appsTimeMapArrayList = new ArrayMap<>();
        TimerListModel timerListModel = AppSettings.get().getTimerListModel();
        if (timerListModel != null) {
            for (int k = 0; k < timerListModel.getTimerArrayList().size(); k++) {
                appsTimeMapArrayList.put(timerListModel.getTimerArrayList().get(k).getPackageName(), timerListModel.getTimerArrayList().get(k).getTime());
            }
        }


        //test
        final List<App> apps = _apps;
        for (int g = 0; g < apps.size(); g++) {
            Log.v("values", String.valueOf(apps.size()));
            String packageName = apps.get(g).getPackageName();
            UsageStats usageStats = allAppsUsageStats.get(packageName);
            if (usageStats != null) {
                String timeString = appsTimeMapArrayList.get(packageName);
                if (timeString != null) {
                    if ((usageStats.getTotalTimeInForeground() / 60000) > Integer.parseInt(timeString)) {
                        if (!_listActivitiesHidden.contains(apps.get(g).getPackageName())) {
                            _listActivitiesHidden.add(apps.get(g).getPackageName());
                        }
                    }
                }
            }
        }

        AppSettings.get().setHiddenAppsList(_listActivitiesHidden);
        List<String> hiddenListDup = AppSettings.get().getHiddenAppsList();
        Log.v("allAppsUsageStats", String.valueOf(hiddenListDup.size()));


        AppManager.getInstance(this).init();
    }
}
