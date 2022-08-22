package com.example.gfnqueuetracker;


import androidx.appcompat.app.AppCompatActivity;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.JsonReader;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class RequestActivity extends AppCompatActivity {
    private List<Request> requests;
    private Request cur_request;
    private EditText edit_text;

    // define list here as dependency injection
    // list assumed to be > 0 length
    private List<Request> makeList() {
        List<Request> ls = new ArrayList<>();
        ls.add(new Request(
                getString(R.string.name_gfnopener), getString(R.string.key_gfnopener),
                "", this));
        ls.add(new Request(
                getString(R.string.name_tvopener), getString(R.string.key_tvopener),
                "", this));
        return ls;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_request);

        requests = makeList();
        edit_text = (EditText) findViewById(R.id.editText_request_url);

        // setup spinner
        Spinner spinner = (Spinner) findViewById(R.id.spinner_request);
        List<CharSequence> cs_ls = requests.stream()
                .map(r -> r.getName())
                .collect(Collectors.toList());
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, cs_ls);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (cur_request != null) {
                    cur_request.saveUrl(edit_text.getText().toString());
                }
                cur_request = requests.get(pos);
                edit_text.setText(cur_request.getUrl());
            }
            public void onNothingSelected(AdapterView<?> parent) {
                // Another interface callback
            }
        });
    }

    public void onClickSubmit(View view) {
        String url_str = edit_text.getText().toString();
        cur_request.saveUrl(url_str);

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                cur_request.execute(url_str);
                return;
            }

        });
        thread.start();
    }
}

class Request {
    protected String name, key, default_str;
    protected AppCompatActivity activity;
    public Request(String name, String key, String default_str, AppCompatActivity activity) {
        this.name = name;
        this.key = key;
        this.default_str = default_str;
        this.activity = activity;
    }
    public String getName() { return name; }
    public String getKey() { return key; }

    public void saveUrl(String url) {
        SharedPreferences.Editor editor = activity.getPreferences(Context.MODE_PRIVATE).edit();
        editor.putString(key, url);
        editor.apply();
    }
    public String getUrl() {
        return activity.getPreferences(Context.MODE_PRIVATE).getString(key, default_str);
    }
    public void _runToast(String content) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(activity, content, Toast.LENGTH_SHORT).show();
            }
        });
    }
    public void execute(String content) {
        _runToast(String.format(Locale.getDefault(), "Executing: %s, %s", name, content));
        URL url = null;
        int code = -1;
        String msg = "Internal error";

        // get URL
        try {
            url = new URL(content);
        } catch (Exception e) {
            _runToast("Error: " + e.toString());
            return;
        }

        // parse
        try (InputStream is = url.openStream()) {
            JsonReader jr = new JsonReader(new InputStreamReader(is));
            jr.beginObject();
            while (jr.hasNext()) {
                String key = jr.nextName();
                if (key.equals("code"))
                    code = jr.nextInt();
                else if (key.equals("message")) {
                    msg = jr.nextString();
                } else {
                    jr.skipValue();
                }
            }
            jr.endObject();
        } catch (Exception e) {
            _runToast("Error: " + e.toString());
            return;
        }
        if (code != 0) {
            _runToast(String.format(Locale.getDefault(), "Code %d: %s", code, msg));
        } else {
            _runToast("Success!");
        }
    }
}

class WarningRequest extends Request {
    public WarningRequest(String name, String key, String default_str, AppCompatActivity activity) {
        super(name, key, default_str, activity);
    }
    @Override
    public void execute(String content) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this.activity);
        builder.setMessage(R.string.dialog_request_warning)
                .setPositiveButton(R.string.dialog_request_yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        WarningRequest.super.execute(content);
                    }
                })
                .setNegativeButton(R.string.dialog_request_no, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        Toast.makeText(activity, "Cancelled", Toast.LENGTH_SHORT).show();
                    }
                });
        builder.create();
    }
}