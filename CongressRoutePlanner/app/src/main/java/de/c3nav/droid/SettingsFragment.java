package de.c3nav.droid;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.preference.CheckBoxPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;

public class SettingsFragment extends PreferenceFragmentCompat {

    private SharedPreferences sharePrefs;

    public CheckBoxPreference useWifiLocating;

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

    private boolean checkLocationPermisson() {
        int permissionCheck = ContextCompat.checkSelfPermission(getContext(),
                Manifest.permission.ACCESS_FINE_LOCATION);
        return permissionCheck == PackageManager.PERMISSION_GRANTED;
    }
}
