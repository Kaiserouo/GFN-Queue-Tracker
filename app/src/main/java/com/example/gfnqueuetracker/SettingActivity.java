package com.example.gfnqueuetracker;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Pair;
import android.view.View;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class SettingActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);

        loadInputs();
    }

    public void onClickSubmit(View view) {
        // whatever they do, just save it
        saveInputs();

        Map<String, String> inputs = readInputs();
        Pair<Boolean, String> inputs_valid_ret = inputsValid(inputs);
        Boolean is_input_valid = inputs_valid_ret.first;
        String invalid_reason = inputs_valid_ret.second;
        if (!is_input_valid) {
            // warn and do nothing
            Toast.makeText(
                    this,
                    String.format(getString(R.string.str_input_not_valid_format), invalid_reason),
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        // stop & start service
        Intent intent = new Intent(this, GFNTrackerService.class);
        if (isMyServiceRunning(GFNTrackerService.class)) {
            intent.setAction(GFNTrackerService.ACTION_STOP);
            startService(intent);
        }
        putInputsToIntent(intent);
        Toast.makeText(this, "Starting service...", Toast.LENGTH_SHORT).show();
        intent.setAction(GFNTrackerService.ACTION_START);
        startService(intent);

        // go to MonitorActivity
        startActivity(new Intent(this, MonitorActivity.class));
    }

    @Override
    public void onBackPressed() {
        // do absolutely nothing
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void onClickGotoVPNSetting(View view) {
        Intent intent = new Intent(android.provider.Settings.ACTION_VPN_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    public void onClickGotoRequestList(View view) {
        Intent intent = new Intent(this, RequestActivity.class);
        startActivity(intent);
    }

    // ---
    // This is a mess. I don't want to overdesign but it turns out there is almost no design here
    // Maybe design a field option...? But how to integrate with service?

    private void loadInputs() {
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor shared_editor = sharedPref.edit();

        EditText edittext_url = (EditText) findViewById(R.id.editText_URL);
        EditText edittext_alertcount = (EditText) findViewById(R.id.editText_alertCount);
        Switch switch_vibrate = (Switch) findViewById(R.id.switch_vibrate);

        edittext_url.setText(
                sharedPref.getString(
                        getString(R.string.key_setting_url),
                        getString(R.string.edit_view_url)
                )
        );
        edittext_alertcount.setText(
                sharedPref.getString(
                        getString(R.string.key_setting_alertcount),
                        getString(R.string.edit_view_alert_count)
                )
        );
        switch_vibrate.setChecked(
                sharedPref.getBoolean(
                        getString(R.string.key_setting_vibrate),
                        false
                )
        );

        return;
    }
    private void saveInputs() {
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor shared_editor = sharedPref.edit();

        EditText edittext_url = (EditText) findViewById(R.id.editText_URL);
        EditText edittext_alertcount = (EditText) findViewById(R.id.editText_alertCount);
        Switch switch_vibrate = (Switch) findViewById(R.id.switch_vibrate);


        shared_editor.putString(
                getString(R.string.key_setting_url),
                edittext_url.getText().toString()
        );
        shared_editor.putString(
                getString(R.string.key_setting_alertcount),
                edittext_alertcount.getText().toString()
        );
        shared_editor.putBoolean(
                getString(R.string.key_setting_vibrate),
                switch_vibrate.isChecked()
        );
        shared_editor.apply();
    }
    private Map<String, String> readInputs() {
        Map<String, String> m = new HashMap<>();
        EditText edittext_url = (EditText) findViewById(R.id.editText_URL);
        EditText edittext_alertcount = (EditText) findViewById(R.id.editText_alertCount);
        Switch switch_vibrate = (Switch) findViewById(R.id.switch_vibrate);

        m.put(
                getString(R.string.key_setting_url),
                edittext_url.getText().toString()
        );
        m.put(
                getString(R.string.key_setting_alertcount),
                edittext_alertcount.getText().toString()
        );
        m.put(
                getString(R.string.key_setting_vibrate),
                String.valueOf(switch_vibrate.isChecked())
        );

        return m;
    }
    private Pair<Boolean, String> inputsValid(Map<String, String> inputs) {
        // <input_is_valid, invalid_reason (if valid then null)>
        // hardcode currently
        String url = inputs.get(getString(R.string.key_setting_url));
        String alertcount = inputs.get(getString(R.string.key_setting_alertcount));

        try {
            URL u = new URL(url);
        } catch (MalformedURLException e) {
            return new Pair<>(false, e.toString());
        }

        try {
            Integer a = Integer.parseInt(alertcount);
            if (a < 0) {
                return new Pair<>(false, getString(R.string.error_msg_alertcount_negative));
            }
        } catch (Exception e) {
            return new Pair<>(false, e.toString());
        }
        return new Pair<>(true, "");
    }
    private void putInputsToIntent(Intent intent) {
        Map<String, String> m = readInputs();
        for (Map.Entry<String, String> entry: m.entrySet()) {
            intent.putExtra(entry.getKey(), entry.getValue());
        }
    }

    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}