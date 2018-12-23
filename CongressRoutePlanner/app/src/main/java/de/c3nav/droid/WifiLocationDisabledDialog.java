package de.c3nav.droid;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatDialogFragment;

public class WifiLocationDisabledDialog extends AppCompatDialogFragment {

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle args = getArguments();
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getActivity());
        alertDialogBuilder.setMessage(getString(R.string.wifi_locating_disabled_message));
        alertDialogBuilder.setCancelable(false);
        alertDialogBuilder.setIcon(android.R.drawable.ic_dialog_alert);
        alertDialogBuilder.setPositiveButton(R.string.wifi_locating_disabled_ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
                Intent settingsIntent = new Intent(getActivity(), SettingsActivity.class);
                getActivity().startActivityForResult(settingsIntent, MainActivity.SETTINGS_REQUEST);
            }
        });
        alertDialogBuilder.setNegativeButton(R.string.wifi_locating_disabled_cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.cancel();
            }
        });
        return alertDialogBuilder.create();
    }
}
