package com.benny.openlauncher.fragment;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.preference.Preference;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.benny.openlauncher.R;
import com.benny.openlauncher.activity.HideAppsActivity;
import com.benny.openlauncher.activity.HomeActivity;
import com.benny.openlauncher.activity.MoreInfoActivity;
import com.benny.openlauncher.model.UserListModel;
import com.benny.openlauncher.model.UserModel;
import com.benny.openlauncher.util.AppSettings;

import net.gsantner.opoc.util.ContextUtils;

import java.util.ArrayList;
import java.util.Locale;

import static com.benny.openlauncher.widget.AppDrawerController.Mode.GRID;
import static com.benny.openlauncher.widget.AppDrawerController.Mode.PAGE;

public class SettingsMasterFragment extends SettingsBaseFragment {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        addPreferencesFromResource(R.xml.preferences_master);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        super.onPreferenceTreeClick(preference);
        HomeActivity homeActivity = HomeActivity._launcher;
        int key = new ContextUtils(homeActivity).getResId(ContextUtils.ResType.STRING, preference.getKey());
        switch (key) {
            case R.string.pref_key__cat_hide_apps:
                /*Intent intent = new Intent(getActivity(), HideAppsActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(intent);*/
                loginFragment();
                return true;
            case R.string.pref_key__cat_about:
                startActivity(new Intent(getActivity(), MoreInfoActivity.class));
                return true;
        }

        return false;
    }

    @Override
    public void updateSummaries() {
        Preference categoryDesktop = findPreference(getString(R.string.pref_key__cat_desktop));
        Preference categoryDock = findPreference(getString(R.string.pref_key__cat_dock));
        Preference categoryAppDrawer = findPreference(getString(R.string.pref_key__cat_app_drawer));
        Preference categoryAppearance = findPreference(getString(R.string.pref_key__cat_appearance));

        categoryDesktop.setSummary(String.format(Locale.ENGLISH, "%s: %d x %d", getString(R.string.pref_title__size), AppSettings.get().getDesktopColumnCount(), AppSettings.get().getDesktopRowCount()));
        categoryDock.setSummary(String.format(Locale.ENGLISH, "%s: %d x %d", getString(R.string.pref_title__size), AppSettings.get().getDockColumnCount(), AppSettings.get().getDockRowCount()));
        categoryAppearance.setSummary(String.format(Locale.ENGLISH, "%s: %ddp", getString(R.string.pref_title__icons), AppSettings.get().getIconSize()));

        switch (AppSettings.get().getDrawerStyle()) {
            case GRID:
                categoryAppDrawer.setSummary(String.format("%s: %s", getString(R.string.pref_title__style), getString(R.string.vertical_scroll_drawer)));
                break;
            case PAGE:
            default:
                categoryAppDrawer.setSummary(String.format("%s: %s", getString(R.string.pref_title__style), getString(R.string.horizontal_paged_drawer)));
                break;
        }
    }

    public void loginFragment() {
        final Dialog loginDialog = new Dialog(getActivity());
        loginDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        loginDialog.setContentView(R.layout.login_dialog);
        loginDialog.setCanceledOnTouchOutside(false);
        loginDialog.setCancelable(true);

        TextView headingTextView = loginDialog.findViewById(R.id.headingTextView);
        TextView registerTextView = loginDialog.findViewById(R.id.registerTextView);
        EditText usernameEditText = loginDialog.findViewById(R.id.usernameEditText);
        EditText passwordEditText = loginDialog.findViewById(R.id.passwordEditText);
        TextView saveTextView = loginDialog.findViewById(R.id.saveTextView);
        TextView forgotPasswordTextView = loginDialog.findViewById(R.id.forgotPasswordTextView);
        headingTextView.setText("User Login");

        forgotPasswordTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loginDialog.dismiss();
                forgotPasswordFragment();
            }
        });

        saveTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String username = usernameEditText.getText().toString();
                String password = passwordEditText.getText().toString();
                if (username != null && password != null && isValidEmail(username) && isValidPassword(password)) {
                    UserListModel userListModel = AppSettings.get().getUserListModel();
                    if (userListModel != null) {
                        ArrayList<UserModel> userModelArrayList = userListModel.getUserModelArrayList();
                        if (userModelArrayList != null) {
                            boolean userExists = false;
                            for (int p = 0; p < userModelArrayList.size(); p++) {
                                if (userModelArrayList.get(p).getUsername().equalsIgnoreCase(username) && userModelArrayList.get(p).getPassword().equals(password)) {
                                    userExists = true;
                                }
                            }
                            if (userExists) {
                                loginDialog.dismiss();
                                Toast.makeText(getActivity(), "Login Successful", Toast.LENGTH_SHORT).show();
                                Intent intent = new Intent(getActivity(), HideAppsActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                                startActivity(intent);
                            } else {
                                Toast.makeText(getActivity(), "User not exists", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(getActivity(), "User not exists", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(getActivity(), "User not exists", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(getActivity(), "Email or Password is not valid", Toast.LENGTH_SHORT).show();
                }
            }
        });

        registerTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loginDialog.dismiss();
                registerFragment();
            }
        });


        loginDialog.show();
    }


    public void forgotPasswordFragment() {
        final Dialog forgotPasswordDialog = new Dialog(getActivity());
        forgotPasswordDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        forgotPasswordDialog.setContentView(R.layout.login_dialog);
        forgotPasswordDialog.setCanceledOnTouchOutside(false);
        forgotPasswordDialog.setCancelable(true);

        TextView headingTextView = forgotPasswordDialog.findViewById(R.id.headingTextView);
        TextView registerTextView = forgotPasswordDialog.findViewById(R.id.registerTextView);
        EditText usernameEditText = forgotPasswordDialog.findViewById(R.id.usernameEditText);
        EditText passwordEditText = forgotPasswordDialog.findViewById(R.id.passwordEditText);
        TextView saveTextView = forgotPasswordDialog.findViewById(R.id.saveTextView);
        TextView forgotPasswordTextView = forgotPasswordDialog.findViewById(R.id.forgotPasswordTextView);
        forgotPasswordTextView.setVisibility(View.GONE);
        registerTextView.setVisibility(View.GONE);
        passwordEditText.setVisibility(View.GONE);
        saveTextView.setText("SUBMIT");
        headingTextView.setText("Forgot Password");

        saveTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String username = usernameEditText.getText().toString();
                UserModel userModel = new UserModel();
                userModel.setUsername(username);

                if (saveTextView.getText().equals("SUBMIT")) {
                    if (username != null && isValidEmail(username)) {
                        UserListModel userListModel = AppSettings.get().getUserListModel();
                        if (userListModel != null) {
                            ArrayList<UserModel> userModelArrayList = userListModel.getUserModelArrayList();
                            if (userModelArrayList != null) {
                                boolean userExists = false;
                                for (int p = 0; p < userModelArrayList.size(); p++) {
                                    if (userModelArrayList.get(p).getUsername().equalsIgnoreCase(username)) {
                                        userExists = true;
                                    }
                                }
                                if (userExists) {
                                    Toast.makeText(getActivity(), "Please enter new password", Toast.LENGTH_SHORT).show();
                                    passwordEditText.setVisibility(View.VISIBLE);
                                    passwordEditText.setHint("New Password");
                                    saveTextView.setText("SAVE Password");
                                } else {
                                    //forgotPasswordDialog.dismiss();
                                    Toast.makeText(getActivity(), "User not exists with this email", Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                forgotPasswordDialog.dismiss();
                                Toast.makeText(getActivity(), "User not exists with this email", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            forgotPasswordDialog.dismiss();
                            Toast.makeText(getActivity(), "User not exists with this email", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(getActivity(), "Please fill all details", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    String forgotUsername = usernameEditText.getText().toString();
                    UserModel forgotUserModel = new UserModel();
                    forgotUserModel.setUsername(username);
                    String forgotPassword = passwordEditText.getText().toString();
                    forgotUserModel.setPassword(forgotPassword);
                    if (forgotUsername != null && forgotPassword != null && isValidEmail(forgotUsername) && isValidPassword(forgotPassword)) {
                        UserListModel userListModel = AppSettings.get().getUserListModel();
                        if (userListModel != null) {
                            ArrayList<UserModel> userModelArrayList = userListModel.getUserModelArrayList();
                            if (userModelArrayList != null) {
                                for (int p = 0; p < userModelArrayList.size(); p++) {
                                    if (userModelArrayList.get(p).getUsername().equalsIgnoreCase(username)) {
                                        userModelArrayList.get(p).setPassword(forgotPassword);
                                    }
                                }
                                UserListModel usm = new UserListModel();
                                usm.setUserModelArrayList(userModelArrayList);
                                AppSettings.get().setUserListModel(usm);
                                forgotPasswordDialog.dismiss();
                                Toast.makeText(getActivity(), "Password Update Successful", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                }
            }
        });

        forgotPasswordDialog.show();
    }

    public void registerFragment() {
        final Dialog registerDialog = new Dialog(getActivity());
        registerDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        registerDialog.setContentView(R.layout.login_dialog);
        registerDialog.setCanceledOnTouchOutside(false);
        registerDialog.setCancelable(true);

        TextView headingTextView = registerDialog.findViewById(R.id.headingTextView);
        TextView registerTextView = registerDialog.findViewById(R.id.registerTextView);
        EditText usernameEditText = registerDialog.findViewById(R.id.usernameEditText);
        EditText passwordEditText = registerDialog.findViewById(R.id.passwordEditText);
        TextView saveTextView = registerDialog.findViewById(R.id.saveTextView);
        TextView forgotPasswordTextView = registerDialog.findViewById(R.id.forgotPasswordTextView);
        forgotPasswordTextView.setVisibility(View.GONE);
        registerTextView.setVisibility(View.GONE);
        saveTextView.setText("REGISTER");
        headingTextView.setText("User Registration");

        saveTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String username = usernameEditText.getText().toString();
                String password = passwordEditText.getText().toString();
                UserModel userModel = new UserModel();
                userModel.setUsername(username);
                userModel.setPassword(password);
                if (username != null && password != null && isValidEmail(username) && isValidPassword(password)) {
                    UserListModel userListModel = AppSettings.get().getUserListModel();
                    if (userListModel != null) {
                        ArrayList<UserModel> userModelArrayList = userListModel.getUserModelArrayList();
                        if (userModelArrayList != null) {
                            boolean userExists = false;
                            for (int p = 0; p < userModelArrayList.size(); p++) {
                                if (userModelArrayList.get(p).getUsername().equalsIgnoreCase(username)) {
                                    userExists = true;
                                }
                            }
                            if (userExists) {
                                Toast.makeText(getActivity(), "User already exists with this username", Toast.LENGTH_SHORT).show();
                            } else {
                                registerDialog.dismiss();
                                Toast.makeText(getActivity(), "Registration Successful", Toast.LENGTH_SHORT).show();
                                userModelArrayList.add(userModel);
                                UserListModel usm = new UserListModel();
                                usm.setUserModelArrayList(userModelArrayList);
                                AppSettings.get().setUserListModel(usm);
                                /*Intent intent = new Intent(getActivity(), HideAppsActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                                startActivity(intent);*/
                                loginFragment();
                            }
                        } else {
                            registerDialog.dismiss();
                            userModelArrayList.add(userModel);
                            UserListModel usm = new UserListModel();
                            usm.setUserModelArrayList(userModelArrayList);
                            AppSettings.get().setUserListModel(usm);
                            /*Intent intent = new Intent(getActivity(), HideAppsActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                            startActivity(intent);*/
                            loginFragment();
                        }
                    } else {
                        registerDialog.dismiss();
                        ArrayList<UserModel> userModelArrayList = new ArrayList<UserModel>();
                        userModelArrayList.add(userModel);
                        UserListModel usm = new UserListModel();
                        usm.setUserModelArrayList(userModelArrayList);
                        AppSettings.get().setUserListModel(usm);
                        /*Intent intent = new Intent(getActivity(), HideAppsActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                        startActivity(intent);*/
                        loginFragment();
                    }
                } else {
                    Toast.makeText(getActivity(), "Please fill all details", Toast.LENGTH_SHORT).show();
                }
            }
        });

        registerDialog.show();
    }


    public boolean isValidEmail(String email) {
        boolean result = false;
        String emailPattern = "[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+";
        if (email.matches(emailPattern)) {
            result = true;
        }
        return result;
    }

    public boolean isValidPassword(String password) {
        boolean result = false;
        if (password.length() == 8) {
            result = true;
        }
        return result;
    }
}
