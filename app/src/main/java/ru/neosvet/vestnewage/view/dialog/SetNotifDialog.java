package ru.neosvet.vestnewage.view.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ru.neosvet.vestnewage.R;

public class SetNotifDialog extends Dialog {
    public static final byte RINGTONE = 1;
    public static final String SOUND = "sound", NAME = "name", URI = "uri", VIBR = "vibr";
    private Activity act;
    private final String source;
    private String name, uri;
    private SharedPreferences pref;
    private TextView tvSound;

    public SetNotifDialog(@NonNull Activity act, String source) {
        super(act);
        this.act = act;
        this.source = source;
    }

    @Override
    public void setOnDismissListener(@Nullable OnDismissListener listener) {
        act = null;
        super.setOnDismissListener(listener);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_setnotif);

        tvSound = findViewById(R.id.tvSound);
        View bRingtone = findViewById(R.id.bRingtone);
        CheckBox cbSound = findViewById(R.id.cbSound);
        CheckBox cbVibration = findViewById(R.id.cbVibration);

        pref = act.getSharedPreferences(source, Context.MODE_PRIVATE);
        name = pref.getString(NAME, "");
        uri = pref.getString(URI, "");
        setRingtone();
        cbSound.setChecked(pref.getBoolean(SOUND, false));
        if (!cbSound.isChecked()) {
            tvSound.setVisibility(View.GONE);
            bRingtone.setVisibility(View.GONE);
        }
        cbVibration.setChecked(pref.getBoolean(VIBR, true));

        cbSound.setOnCheckedChangeListener((compoundButton, user) -> {
            if (cbSound.isChecked()) {
                tvSound.setVisibility(View.VISIBLE);
                bRingtone.setVisibility(View.VISIBLE);
            } else {
                tvSound.setVisibility(View.GONE);
                bRingtone.setVisibility(View.GONE);
            }
        });
        bRingtone.setOnClickListener(view -> {
            Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
            act.startActivityForResult(intent, RINGTONE);
        });
        findViewById(R.id.bOk).setOnClickListener(view -> {
            SharedPreferences.Editor editor = pref.edit();
            editor.putBoolean(SOUND, cbSound.isChecked());
            editor.putString(NAME, name);
            editor.putString(URI, uri);
            editor.putBoolean(VIBR, cbVibration.isChecked());
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
        if (name.isEmpty()) tvSound.setText(act.getString(R.string.default_));
        else tvSound.setText(name);
    }
}
