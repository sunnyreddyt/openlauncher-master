package com.benny.openlauncher.util;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.UserHandle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.util.ArrayMap;
import android.util.Log;

import com.benny.openlauncher.activity.HomeActivity;
import com.benny.openlauncher.interfaces.AppDeleteListener;
import com.benny.openlauncher.interfaces.AppUpdateListener;
import com.benny.openlauncher.model.App;
import com.benny.openlauncher.model.Item;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class AppManager {
    private static Logger LOG = LoggerFactory.getLogger("AppManager");

    private static AppManager appManager;

    public static AppManager getInstance(Context context) {
        return appManager == null ? (appManager = new AppManager(context)) : appManager;
    }

    private PackageManager _packageManager;
    private List<App> _apps = new ArrayList<>();
    private List<App> _nonFilteredApps = new ArrayList<>();
    public final List<AppUpdateListener> _updateListeners = new ArrayList<>();
    public final List<AppDeleteListener> _deleteListeners = new ArrayList<>();
    public boolean _recreateAfterGettingApps;
    private AsyncTask _task;
    private Context _context;
    private UsageStatsManager mUsageStatsManager;
    private PackageManager mPm;
    public PackageManager getPackageManager() {
        return _packageManager;
    }

    public Context getContext() {
        return _context;
    }

    public AppManager(Context context) {
        _context = context;
        _packageManager = context.getPackageManager();
        mUsageStatsManager = (UsageStatsManager) getContext().getSystemService(Context.USAGE_STATS_SERVICE);
        mPm = getContext().getPackageManager();
    }

    public App findApp(Intent intent) {
        if (intent == null || intent.getComponent() == null) return null;

        String packageName = intent.getComponent().getPackageName();
        String className = intent.getComponent().getClassName();
        for (App app : _apps) {
            if (app._className.equals(className) && app._packageName.equals(packageName)) {
                return app;
            }
        }
        return null;
    }

    public List<App> getApps() {
        return _apps;
    }

    public List<App> getNonFilteredApps() {
        return _nonFilteredApps;
    }

    public void init() {
        getAllApps();
    }

    public void getAllApps() {
        if (_task == null || _task.getStatus() == AsyncTask.Status.FINISHED)
            _task = new AsyncGetApps().execute();
        else if (_task.getStatus() == AsyncTask.Status.RUNNING) {
            _task.cancel(false);
            _task = new AsyncGetApps().execute();
        }
    }

    public List<App> getAllApps(Context context, boolean includeHidden) {
        return includeHidden ? getNonFilteredApps() : getApps();
    }

    public App findItemApp(Item item) {
        return findApp(item.getIntent());
    }

    public App createApp(Intent intent) {
        try {
            ResolveInfo info = _packageManager.resolveActivity(intent, 0);
            return new App(_packageManager, info);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void onAppUpdated(Context context, Intent intent) {
        getAllApps();
    }

    public void addUpdateListener(AppUpdateListener updateListener) {
        _updateListeners.add(updateListener);
    }

    public void addDeleteListener(AppDeleteListener deleteListener) {
        _deleteListeners.add(deleteListener);
    }

    public void notifyUpdateListeners(@NonNull List<App> apps) {
        Iterator<AppUpdateListener> iter = _updateListeners.iterator();
        while (iter.hasNext()) {
            if (iter.next().onAppUpdated(apps)) {
                iter.remove();
            }
        }
    }

    public void notifyRemoveListeners(@NonNull List<App> apps) {
        Iterator<AppDeleteListener> iter = _deleteListeners.iterator();
        while (iter.hasNext()) {
            if (iter.next().onAppDeleted(apps)) {
                iter.remove();
            }
        }
    }

    private class AsyncGetApps extends AsyncTask {
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
            _apps.clear();
            _nonFilteredApps.clear();

            // work profile support
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                LauncherApps launcherApps = (LauncherApps) _context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
                List<UserHandle> profiles = launcherApps.getProfiles();
                for (UserHandle userHandle : profiles) {
                    List<LauncherActivityInfo> apps = launcherApps.getActivityList(null, userHandle);
                    for (LauncherActivityInfo info : apps) {
                        App app = new App(_packageManager, info);
                        app._userHandle = userHandle;

                        LOG.debug("adding work profile to non filtered list: {}, {}, {}", app._label, app._packageName, app._className);
                        _nonFilteredApps.add(app);
                    }
                }
            } else {
                Intent intent = new Intent(Intent.ACTION_MAIN, null);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> activitiesInfo = _packageManager.queryIntentActivities(intent, 0);
                for (ResolveInfo info : activitiesInfo) {
                    App app = new App(_packageManager, info);

                    LOG.debug("adding app to non filtered list: {}, {}, {}", app._label,  app._packageName, app._className);
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

            AppSettings appSettings = AppSettings.get();
            if (!appSettings.getIconPack().isEmpty() && Tool.isPackageInstalled(appSettings.getIconPack(), _packageManager)) {
                IconPackHelper.applyIconPack(AppManager.this, Tool.dp2px(appSettings.getIconSize()), appSettings.getIconPack(), _apps);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Object result) {
            notifyUpdateListeners(_apps);

            List<App> removed = getRemovedApps(tempApps, _apps);
            if (removed.size() > 0) {
                notifyRemoveListeners(removed);
            }

            if (_recreateAfterGettingApps) {
                _recreateAfterGettingApps = false;
                if (_context instanceof HomeActivity)
                    ((HomeActivity) _context).recreate();
            }

            super.onPostExecute(result);
        }
    }

    private void requestPermissions() {
        List<UsageStats> stats = mUsageStatsManager
                .queryUsageStats(UsageStatsManager.INTERVAL_DAILY, 0, System.currentTimeMillis());
        boolean isEmpty = stats.isEmpty();
        if (isEmpty) {
            getContext().startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        } else {
             getData();
        }
    }

    public void getData() {
        ArrayMap<String, String> mAppLabelMap = new ArrayMap<>();
        ArrayList<UsageStats> mPackageStats = new ArrayList<>();
        Map<String, UsageStats> allAppsUsageStats = new ArrayMap<>();
        ArrayList<String> _listActivitiesHidden = new ArrayList<String>();
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
        final List<App> apps = getApps();
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

    public static List<App> getRemovedApps(List<App> oldApps, List<App> newApps) {
        List<App> removed = new ArrayList<>();
        // if this is the first call then return an empty list
        if (oldApps.size() == 0) {
            return removed;
        }
        for (int i = 0; i < oldApps.size(); i++) {
            if (!newApps.contains(oldApps.get(i))) {
                removed.add(oldApps.get(i));
                break;
            }
        }
        return removed;
    }
}
