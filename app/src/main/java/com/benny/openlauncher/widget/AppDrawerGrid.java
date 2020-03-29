package com.benny.openlauncher.widget;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.provider.Settings;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateUtils;
import android.util.ArrayMap;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import com.benny.openlauncher.R;
import com.benny.openlauncher.interfaces.AppUpdateListener;
import com.benny.openlauncher.manager.Setup;
import com.benny.openlauncher.model.App;
import com.benny.openlauncher.model.Item;
import com.benny.openlauncher.model.Timer;
import com.benny.openlauncher.model.TimerListModel;
import com.benny.openlauncher.util.AppManager;
import com.benny.openlauncher.util.AppSettings;
import com.benny.openlauncher.util.DragAction;
import com.benny.openlauncher.util.DragHandler;
import com.benny.openlauncher.util.Tool;
import com.benny.openlauncher.viewutil.IconLabelItem;
import com.mikepenz.fastadapter.commons.adapters.FastItemAdapter;
import com.turingtechnologies.materialscrollbar.AlphabetIndicator;
import com.turingtechnologies.materialscrollbar.DragScrollBar;
import com.turingtechnologies.materialscrollbar.INameableAdapter;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

public class AppDrawerGrid extends FrameLayout {

    public static int _itemWidth;
    public static int _itemHeightPadding;

    private ArrayList<String> _listActivitiesHidden;
    public RecyclerView _recyclerView;
    public AppDrawerGridAdapter _gridDrawerAdapter;
    public DragScrollBar _scrollBar;

    private UsageStatsManager mUsageStatsManager;
    private ArrayMap<String, String> mAppLabelMap;
    private ArrayList<UsageStats> mPackageStats;
    public static Map<String, UsageStats> allAppsUsageStats;
    private PackageManager mPm;
    private static List<App> _apps;
    private GridLayoutManager _layoutManager;

    public AppDrawerGrid(Context context) {
        super(context);
        LayoutInflater layoutInflater = LayoutInflater.from(getContext());
        View view = layoutInflater.inflate(R.layout.view_app_drawer_grid, AppDrawerGrid.this, false);
        addView(view);

        _recyclerView = findViewById(R.id.recycler_view);
        _scrollBar = findViewById(R.id.scroll_bar);
        _layoutManager = new GridLayoutManager(getContext(), Setup.appSettings().getDrawerColumnCount());
        mUsageStatsManager = (UsageStatsManager) getContext().getSystemService(Context.USAGE_STATS_SERVICE);
        mPm = getContext().getPackageManager();
        //requestPermissions();
        init();
    }

    private void init() {

        if (!Setup.appSettings().getDrawerShowIndicator()) _scrollBar.setVisibility(View.GONE);
        _scrollBar.setIndicator(new AlphabetIndicator(getContext()), true);
        _scrollBar.setClipToPadding(true);
        _scrollBar.setDraggableFromAnywhere(true);
        _scrollBar.setHandleColor(Setup.appSettings().getDrawerFastScrollColor());

        _gridDrawerAdapter = new AppDrawerGridAdapter();

        if (getContext().getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            setPortraitValue();
        } else {
            setLandscapeValue();
        }
        _recyclerView.setAdapter(_gridDrawerAdapter);
        _recyclerView.setLayoutManager(_layoutManager);
        _recyclerView.setDrawingCacheEnabled(true);

        getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                getViewTreeObserver().removeOnGlobalLayoutListener(this);
                _itemWidth = getWidth() / _layoutManager.getSpanCount();
                _itemHeightPadding = Tool.dp2px(20);
                updateAdapter(Setup.appLoader().getAllApps(getContext(), false));
                Setup.appLoader().addUpdateListener(new AppUpdateListener() {
                    @Override
                    public boolean onAppUpdated(List<App> apps) {
                        updateAdapter(apps);
                        return false;
                    }
                });
            }
        });
    }

    private void requestPermissions() {
        List<UsageStats> stats = mUsageStatsManager
                .queryUsageStats(UsageStatsManager.INTERVAL_DAILY, 0, System.currentTimeMillis());
        boolean isEmpty = stats.isEmpty();
        if (isEmpty) {
            getContext().startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        } else {
           // getData();
        }
    }

    public void getData() {
        mAppLabelMap = new ArrayMap<>();
        mPackageStats = new ArrayList<>();
        allAppsUsageStats = new ArrayMap<>();
        _listActivitiesHidden = new ArrayList<String>();
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -5);

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
                mAppLabelMap.put(pkgStats.getPackageName(), label);

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
            allAppsUsageStats.put(String.valueOf(mPackageStats.get(g).getPackageName()),mPackageStats.get(g));
        }
        List<String> hiddenList = AppSettings.get().getHiddenAppsList();
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


        //test
        final List<App> apps = AppManager.getInstance(getContext()).getApps();
        for (int g=0;g< apps.size();g++){
            Log.v("values",String.valueOf(apps.size()));
            String packageName = apps.get(g).getPackageName();
            UsageStats usageStats = allAppsUsageStats.get(packageName);
            if ((usageStats.getTotalTimeInForeground() / 60000) > 30){
                if (!_listActivitiesHidden.contains(apps.get(g).getPackageName())){
                    _listActivitiesHidden.add(apps.get(g).getPackageName());
                }
            }
        }


        AppSettings.get().setHiddenAppsList(_listActivitiesHidden);
        List<String> hiddenListDup = AppSettings.get().getHiddenAppsList();
        Log.v("allAppsUsageStats", String.valueOf(hiddenListDup.size()));

    }

    public void updateAdapter(List<App> apps) {
        _apps = apps;
        ArrayList<IconLabelItem> items = new ArrayList<>();
        for (int i = 0; i < apps.size(); i++) {
            App app = apps.get(i);
            items.add(new IconLabelItem(app.getIcon(), app.getLabel())
                    .withIconSize(Setup.appSettings().getIconSize())
                    .withTextColor(Color.WHITE)
                    .withTextVisibility(Setup.appSettings().getDrawerShowLabel())
                    .withIconPadding(8)
                    .withTextGravity(Gravity.CENTER)
                    .withIconGravity(Gravity.TOP)
                    .withOnClickAnimate(false)
                    .withIsAppLauncher(true)
                    .withOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Tool.startApp(v.getContext(), app, null);
                        }
                    })
                    .withOnLongClickListener(DragHandler.getLongClick(Item.newAppItem(app), DragAction.Action.DRAWER, null)));
        }
        _gridDrawerAdapter.set(items);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        if (_apps == null || _layoutManager == null) {
            super.onConfigurationChanged(newConfig);
            return;
        }

        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            setLandscapeValue();
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            setPortraitValue();
        }
        super.onConfigurationChanged(newConfig);
    }

    private void setPortraitValue() {
        _layoutManager.setSpanCount(Setup.appSettings().getDrawerColumnCount());
        _gridDrawerAdapter.notifyAdapterDataSetChanged();
    }

    private void setLandscapeValue() {
        _layoutManager.setSpanCount(Setup.appSettings().getDrawerRowCount());
        _gridDrawerAdapter.notifyAdapterDataSetChanged();
    }

    public static class AppDrawerGridAdapter extends FastItemAdapter<IconLabelItem> implements INameableAdapter {
        public AppDrawerGridAdapter() {
        }

        @Override
        public Character getCharacterForElement(int element) {
            if (_apps != null && element < _apps.size() && _apps.get(element) != null && _apps.get(element).getLabel().length() > 0)
                return _apps.get(element).getLabel().charAt(0);
            else return '#';
        }
    }
}
