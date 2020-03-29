package com.benny.openlauncher.fragment;

import android.app.Dialog;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.text.format.DateUtils;
import android.util.ArrayMap;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewSwitcher;

import com.benny.openlauncher.R;
import com.benny.openlauncher.activity.HomeActivity;
import com.benny.openlauncher.model.App;
import com.benny.openlauncher.model.Timer;
import com.benny.openlauncher.model.TimerListModel;
import com.benny.openlauncher.util.AppManager;
import com.benny.openlauncher.util.AppSettings;

import java.sql.Time;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class HideAppsFragment extends Fragment {
    private static final String TAG = "RequestActivity";
    private static final boolean DEBUG = true;

    private ArrayList<String> _listActivitiesHidden = new ArrayList();
    private ArrayList<App> _listActivitiesAll = new ArrayList();
    private AsyncWorkerList _taskList = new AsyncWorkerList();
    private HideAppsAdapter _appInfoAdapter;
    private ViewSwitcher _switcherLoad;
    private ListView _grid;
    private ArrayMap<String, String> mAppLabelMap;
    private UsageStatsManager mUsageStatsManager;
    private PackageManager mPm;
    private ArrayList<UsageStats> mPackageStats;
    private Map<String, UsageStats> allAppsUsageStats;
    private AppNameComparator mAppLabelComparator;
    private LastTimeUsedComparator mLastTimeUsedComparator = new LastTimeUsedComparator();
    private UsageTimeComparator mUsageTimeComparator = new UsageTimeComparator();
    private Map<String, String> appsTimeMapArrayList = new ArrayMap<>();

    public static class AppNameComparator implements Comparator<UsageStats> {
        private Map<String, String> mAppLabelList;

        AppNameComparator(Map<String, String> appList) {
            mAppLabelList = appList;
        }

        @Override
        public final int compare(UsageStats a, UsageStats b) {
            String alabel = mAppLabelList.get(a.getPackageName());
            String blabel = mAppLabelList.get(b.getPackageName());
            return alabel.compareTo(blabel);
        }
    }

    public static class LastTimeUsedComparator implements Comparator<UsageStats> {
        @Override
        public final int compare(UsageStats a, UsageStats b) {
            // return by descending order
            return (int) (b.getLastTimeUsed() - a.getLastTimeUsed());
        }
    }

    public static class UsageTimeComparator implements Comparator<UsageStats> {
        @Override
        public final int compare(UsageStats a, UsageStats b) {
            return (int) (b.getTotalTimeInForeground() - a.getTotalTimeInForeground());
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.view_hide_apps, container, false);
        _switcherLoad = rootView.findViewById(R.id.viewSwitcherLoadingMain);

        mUsageStatsManager = (UsageStatsManager) getActivity().getSystemService(Context.USAGE_STATS_SERVICE);
        mPm = getActivity().getPackageManager();
        FloatingActionButton fab = rootView.findViewById(R.id.fab_rq);

        getAppsTimeMapArrayList();
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                confirmSelection();
            }
        });

        if (_taskList.getStatus() == AsyncTask.Status.PENDING) {
            // task has not started yet
            _taskList.execute();
        }

        if (_taskList.getStatus() == AsyncTask.Status.FINISHED) {
            // task is done and onPostExecute has been called
            new AsyncWorkerList().execute();
        }

        requestPermissions();
        return rootView;
    }

    public class AsyncWorkerList extends AsyncTask<String, Integer, String> {

        private AsyncWorkerList() {
        }

        @Override
        protected void onPreExecute() {
            AppSettings appSettings = new AppSettings(getActivity());
            List<String> hiddenList = appSettings.getHiddenAppsList();
            _listActivitiesHidden.addAll(hiddenList);

            super.onPreExecute();
        }

        @Override
        protected String doInBackground(String... arg0) {
            try {
                // compare to installed apps
                prepareData();
                return null;
            } catch (Throwable e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            populateView();
            // switch from loading screen to the main view
            _switcherLoad.showNext();

            super.onPostExecute(result);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        if (DEBUG) Log.v(TAG, "onSaveInstanceState");
        super.onSaveInstanceState(savedInstanceState);
    }

    private void confirmSelection() {
        Thread actionSend_Thread = new Thread() {

            @Override
            public void run() {
                // update hidden apps
                AppSettings appSettings = new AppSettings(getActivity());
                appSettings.setHiddenAppsList(_listActivitiesHidden);
                getActivity().finish();
            }
        };

        if (!actionSend_Thread.isAlive()) {
            // prevents thread from being executed more than once
            actionSend_Thread.start();
        }
    }

    private void prepareData() {
        List<App> apps = AppManager.getInstance(getContext()).getNonFilteredApps();
        _listActivitiesAll.addAll(apps);
    }

    private void populateView() {
        _grid = getActivity().findViewById(R.id.app_grid);

        assert _grid != null;
        _grid.setFastScrollEnabled(true);
        _grid.setFastScrollAlwaysVisible(false);

        _appInfoAdapter = new HideAppsAdapter(getActivity(), _listActivitiesAll);

        _grid.setAdapter(_appInfoAdapter);
        _grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> AdapterView, View view, int position, long row) {
                App appInfo = (App) AdapterView.getItemAtPosition(position);
                CheckBox checker = view.findViewById(R.id.checkbox);
                ViewSwitcher icon = view.findViewById(R.id.viewSwitcherChecked);

                checker.toggle();
                if (checker.isChecked()) {
                    _listActivitiesHidden.add(appInfo.getPackageName());
                    if (DEBUG) Log.v(TAG, "Selected App: " + appInfo.getLabel());
                    if (icon.getDisplayedChild() == 0) {
                        icon.showNext();
                    }
                } else {
                    _listActivitiesHidden.remove(appInfo.getPackageName());
                    if (DEBUG) Log.v(TAG, "Deselected App: " + appInfo.getLabel());
                    if (icon.getDisplayedChild() == 1) {
                        icon.showPrevious();
                    }
                }
            }
        });
    }

    private class HideAppsAdapter extends ArrayAdapter<App> {
        private HideAppsAdapter(Context context, ArrayList<App> adapterArrayList) {
            super(context, R.layout.item_hide_apps, adapterArrayList);
        }

        @NonNull
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = ((LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.item_hide_apps, parent, false);
                holder = new ViewHolder();
                holder._apkIcon = convertView.findViewById(R.id.appIcon);
                holder._apkName = convertView.findViewById(R.id.appName);
                holder._apkPackage = convertView.findViewById(R.id.appPackage);
                holder._itemLayout = convertView.findViewById(R.id.itemLayout);
                holder._checker = convertView.findViewById(R.id.checkbox);
                holder._switcherChecked = convertView.findViewById(R.id.viewSwitcherChecked);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            App appInfo = getItem(position);

            //holder._apkPackage.setText(appInfo.getClassName());
            UsageStats usageStats = allAppsUsageStats.get(appInfo.getPackageName());
            int limit = 0;
            String limitString = appsTimeMapArrayList.get(appInfo.getPackageName());
            if (limitString != null) {
                limit = Integer.parseInt(limitString);
            }
            if (usageStats == null) {
                holder._apkPackage.setText("Usage: " + "0" + " Limit: " + String.valueOf(limit));
            } else {
                holder._apkPackage.setText("Usage: " + String.valueOf(usageStats.getTotalTimeInForeground() / 60000) + " Limit: " + String.valueOf(limit));
            }
            holder._apkName.setText(appInfo.getLabel());
            holder._apkIcon.setImageDrawable(appInfo.getIcon());

            holder._switcherChecked.setInAnimation(null);
            holder._switcherChecked.setOutAnimation(null);
            holder._checker.setChecked(_listActivitiesHidden.contains(appInfo.getPackageName()));
            holder._itemLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    TimerListModel timerListModel = AppSettings.get().getTimerListModel();
                    boolean isHidden = false;
                    int list_position = -1;
                    if (timerListModel != null) {
                        ArrayList<Timer> timerArrayList = timerListModel.getTimerArrayList();

                        if (timerArrayList != null) {
                            for (int p = 0; p < timerArrayList.size(); p++) {
                                if (timerArrayList.get(p).getPackageName().equalsIgnoreCase(appInfo.getPackageName())) {
                                    list_position = p;
                                    isHidden = true;
                                    break;
                                }
                            }
                        }
                    }
                    timeDialog(isHidden, appInfo.getPackageName(), appInfo.getLabel(), list_position);
                }
            });

            if (_listActivitiesHidden.contains(appInfo.getPackageName())) {
                if (holder._switcherChecked.getDisplayedChild() == 0) {
                    holder._switcherChecked.showNext();
                }
            } else {
                if (holder._switcherChecked.getDisplayedChild() == 1) {
                    holder._switcherChecked.showPrevious();
                }
            }
            return convertView;
        }
    }

    private class ViewHolder {
        TextView _apkName;
        TextView _apkPackage;
        ImageView _apkIcon;
        CheckBox _checker;
        ViewSwitcher _switcherChecked;
        LinearLayout _itemLayout;
    }

    public void getData() {
        mAppLabelMap = new ArrayMap<>();
        mPackageStats = new ArrayList<>();
        allAppsUsageStats = new ArrayMap<>();
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
                Log.v("appsTime", String.valueOf(mPackageStats.get(g).getPackageName()) + " : " + String.valueOf(DateUtils.formatElapsedTime(mPackageStats.get(g).getTotalTimeInForeground() / 1000)));
                allAppsUsageStats.put(String.valueOf(mPackageStats.get(g).getPackageName()), mPackageStats.get(g));
            }
        }

    }

    private void requestPermissions() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -1);
        List<UsageStats> stats = mUsageStatsManager
                .queryUsageStats(UsageStatsManager.INTERVAL_DAILY, cal.getTimeInMillis(), System.currentTimeMillis());
        boolean isEmpty = stats.isEmpty();
        if (isEmpty) {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        } else {
            getData();
        }
    }

    public void timeDialog(boolean isHidden, String packageName, String label, int position) {
        final Dialog confirmationDialog = new Dialog(getActivity());
        confirmationDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        confirmationDialog.setContentView(R.layout.app_time_dialog);
        confirmationDialog.setCanceledOnTouchOutside(false);
        confirmationDialog.setCancelable(true);

        EditText timeEditText = (EditText) confirmationDialog.findViewById(R.id.timeEditText);
        TextView unhideTextView = (TextView) confirmationDialog.findViewById(R.id.unhideTextView);
        TextView appNameTextView = (TextView) confirmationDialog.findViewById(R.id.appNameTextView);
        TextView saveTextView = (TextView) confirmationDialog.findViewById(R.id.saveTextView);

        if (label != null) {
            appNameTextView.setText(label);
        }

        if (isHidden) {
            unhideTextView.setEnabled(true);
            unhideTextView.setAlpha(1);
        } else {
            unhideTextView.setEnabled(false);
            unhideTextView.setAlpha((float) 0.5);
        }

        unhideTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                //removing fro  timer list
                TimerListModel timerListModel = AppSettings.get().getTimerListModel();
                if (timerListModel != null) {
                    ArrayList<Timer> timerArrayList = timerListModel.getTimerArrayList();
                    ArrayList<Timer> newTimerArrayList = new ArrayList<Timer>();
                    if (timerArrayList != null) {
                        for (int p = 0; p < timerArrayList.size(); p++) {
                            if (!timerArrayList.get(p).getPackageName().equalsIgnoreCase(packageName)) {
                                newTimerArrayList.add(timerArrayList.get(p));
                            }
                        }
                        timerListModel.setTimerArrayList(newTimerArrayList);
                        AppSettings.get().setTimerListModel(timerListModel);

                    }
                }

                //removing from hidden list
                /*ArrayList<String> hiddenList = AppSettings.get().getHiddenAppsList();
                ArrayList<String> newHiddenList = new ArrayList<String>();
                if (hiddenList!=null) {
                    for (int j = 0; j < hiddenList.size(); j++) {
                        if (!hiddenList.get(j).equalsIgnoreCase(packageName)) {
                            newHiddenList.add(hiddenList.get(j));
                        }
                    }
                }

                AppSettings.get().setHiddenAppsList(newHiddenList);
                ArrayList<String> newhiddenList = AppSettings.get().getHiddenAppsList();
                _listActivitiesHidden.addAll(newhiddenList);*/
                _listActivitiesHidden.remove(packageName);
                AppSettings appSettings = new AppSettings(getActivity());
                appSettings.setHiddenAppsList(_listActivitiesHidden);
                Toast.makeText(getActivity(), "Removed from hide list", Toast.LENGTH_SHORT).show();
                confirmationDialog.dismiss();
                _appInfoAdapter.notifyDataSetChanged();

            }
        });

        saveTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (timeEditText.getText().toString().length() > 0) {
                    TimerListModel timerListModel = AppSettings.get().getTimerListModel();
                    ArrayList<Timer> timerArrayList;
                    if (timerListModel != null) {
                        timerArrayList = timerListModel.getTimerArrayList();
                    } else {
                        timerListModel = new TimerListModel();
                        timerArrayList = new ArrayList<Timer>();
                    }
                    Timer timer = new Timer();
                    timer.setPackageName(packageName);
                    timer.setTime(timeEditText.getText().toString());
                    if (!isHidden) {
                        timerArrayList.add(timer);
                    } else {
                        timerArrayList.set(position, timer);
                    }
                    timerListModel.setTimerArrayList(timerArrayList);
                    AppSettings.get().setTimerListModel(timerListModel);
                    confirmationDialog.dismiss();

                    getAppsTimeMapArrayList();
                    _appInfoAdapter.notifyDataSetChanged();
                } else {
                    Toast.makeText(getActivity(), "Please enter value", Toast.LENGTH_SHORT).show();
                }
            }
        });

        confirmationDialog.show();
    }

    public void getAppsTimeMapArrayList() {
        appsTimeMapArrayList = new ArrayMap<>();
        TimerListModel timerListModel = AppSettings.get().getTimerListModel();
        if (timerListModel != null) {
            for (int k = 0; k < timerListModel.getTimerArrayList().size(); k++) {
                appsTimeMapArrayList.put(timerListModel.getTimerArrayList().get(k).getPackageName(), timerListModel.getTimerArrayList().get(k).getTime());
            }
        }
    }
}
