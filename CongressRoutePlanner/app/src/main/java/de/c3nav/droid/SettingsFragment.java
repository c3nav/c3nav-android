package de.c3nav.droid;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.preference.SwitchPreference;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.CheckBoxPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;

public class SettingsFragment extends PreferenceFragmentCompat {

    private SharedPreferences sharePrefs;

    public CheckBoxPreference useWifiLocating;
    private PreferenceCategory developerSettings;
    private SwitchPreference developerModeEnabled;
    private EditTextPreference developerInstanceUrl;
    private EditTextPreference developerHttpUser;
    private EditTextPreference developerHttpPassword;


    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);

        useWifiLocating = (CheckBoxPreference)this.findPreference(getString(R.string.use_wifi_locating_key));
        sharePrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        if (sharePrefs.getBoolean(getString(R.string.use_wifi_locating_key), true) && !checkLocationPermisson()) {
            useWifiLocating.setChecked(false);
        }
        useWifiLocating.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (!(boolean)newValue) {
                    // always let user disable wifi locating
                    return true;
                }

                boolean permissionAsked = false;
                while (permissionAsked == false) {
                    if (checkLocationPermisson()) {
                        return true;
                    }
                    permissionAsked = true;
                    ActivityCompat.requestPermissions(getActivity(),
                            new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                            MainActivity.PERM_REQUEST);
                }
                return false;
            }
        });

        developerSettings = (PreferenceCategory)findPreference(getString(R.string.developer_settings_key));
        developerModeEnabled = (SwitchPreference)findPreference(getString(R.string.developer_mode_enabled_key));
        developerInstanceUrl = (EditTextPreference)findPreference(getString(R.string.developer_instance_url_key));
        developerHttpUser = (EditTextPreference)findPreference(getString(R.string.developer_http_user_key));
        developerHttpPassword = (EditTextPreference)findPreference(getString(R.string.developer_http_password_key));

        developerSettings.setVisible(BuildConfig.DEBUG);
        developerModeEnabled.setVisible(BuildConfig.DEBUG);
        developerInstanceUrl.setVisible(BuildConfig.DEBUG);
        developerHttpUser.setVisible(BuildConfig.DEBUG);
        developerHttpPassword.setVisible(BuildConfig.DEBUG);
    }

    private boolean checkLocationPermisson() {
        int permissionCheck = ContextCompat.checkSelfPermission(getContext(),
                Manifest.permission.ACCESS_FINE_LOCATION);
        return permissionCheck == PackageManager.PERMISSION_GRANTED;
    }
}
