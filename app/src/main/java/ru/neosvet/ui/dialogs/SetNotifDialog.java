package ru.neosvet.ui.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;

import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.R;

public class SetNotifDialog extends Dialog {
    public static final byte RINGTONE = 1, CUSTOM = 2;
    public static final String SOUND = "sound", NAME = "name", URI = "uri", VIBR = "vibr";
    private Activity act;
    private String source, name, uri;
    private SharedPreferences pref;
    private TextView tvSound;
    private View tvLabel, pButtons;
    private CheckBox cbSound, cbVibr;


    public SetNotifDialog(@NonNull Activity act, String source) {
        super(act);
        this.act = act;
        this.source = source;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_setnotif);

        tvSound = findViewById(R.id.tvSound);
        tvLabel = findViewById(R.id.tvLabel);
        pButtons = findViewById(R.id.pButtons);
        cbSound = findViewById(R.id.cbSound);
        cbVibr = findViewById(R.id.cbVibr);

        pref = act.getSharedPreferences(source, Context.MODE_PRIVATE);
        name = pref.getString(NAME, null);
        uri = pref.getString(URI, null);
        setRingtone();
        cbSound.setChecked(pref.getBoolean(SOUND, false));
        if (!cbSound.isChecked()) {
            tvSound.setVisibility(View.GONE);
            tvLabel.setVisibility(View.GONE);
            pButtons.setVisibility(View.GONE);
        }
        cbVibr.setChecked(pref.getBoolean(VIBR, true));

        cbSound.setOnCheckedChangeListener((compoundButton, user) -> {
            if (cbSound.isChecked()) {
                tvSound.setVisibility(View.VISIBLE);
                tvLabel.setVisibility(View.VISIBLE);
                pButtons.setVisibility(View.VISIBLE);
            } else {
                tvSound.setVisibility(View.GONE);
                tvLabel.setVisibility(View.GONE);
                pButtons.setVisibility(View.GONE);
            }
        });
        findViewById(R.id.bStandard).setOnClickListener(view -> {
            Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
            act.startActivityForResult(intent, RINGTONE);
        });
        findViewById(R.id.bCustom).setOnClickListener(view -> {
            Lib lib = new Lib(act);
            if (lib.verifyStoragePermissions(RINGTONE)) return;
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            String data = uri;
            if (data == null)
                data = Environment.getExternalStorageDirectory().getAbsolutePath();
            intent.setDataAndType(android.net.Uri.parse(data), "audio/*");
            act.startActivityForResult(intent, CUSTOM);
        });
        findViewById(R.id.bOk).setOnClickListener(view -> {
            SharedPreferences.Editor editor = pref.edit();
            editor.putBoolean(SOUND, cbSound.isChecked());
            editor.putString(NAME, name);
            editor.putString(URI, uri);
            editor.putBoolean(VIBR, cbVibr.isChecked());
            editor.apply();
            SetNotifDialog.this.dismiss();
        });
    }

    public void putRingtone(String name, String uri) {
        this.name = name;
        this.uri = uri;
        setRingtone();
    }

    private void setRingtone() {
        if (name == null)
            tvSound.setText(act.getResources().getString(R.string.default_));
        else
            tvSound.setText(name);
    }
}
