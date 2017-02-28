package ru.neosvet.vestnewage;

import android.app.Fragment;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.SeekBar;

import static android.content.Context.MODE_PRIVATE;

public class SettingsFragment extends Fragment {
    public static final String SUMMARY = "Summary", TIME = "time", SOUND = "sound", VIBR = "vibr";
    private MainActivity act;
    private View container, tvCheck;
    private CheckBox cbAutoCheck, cbCheckSound, cbCheckVibr;
    private SeekBar sbCheckTime;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        this.container = inflater.inflate(R.layout.fragment_settings, container, false);
        act = (MainActivity) getActivity();
        act.setTitle(getResources().getString(R.string.settings));
        initViews();
        setViews();
        return this.container;
    }

    private void setViews() {
        cbCheckSound.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean check) {
                saveSummary();
            }
        });
        cbCheckVibr.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean check) {
                saveSummary();
            }
        });
        cbAutoCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean check) {
                if (check) {
                    setTimeText();
                    tvCheck.setVisibility(View.VISIBLE);
                    sbCheckTime.setVisibility(View.VISIBLE);
                    cbCheckSound.setVisibility(View.VISIBLE);
                    cbCheckVibr.setVisibility(View.VISIBLE);
                } else {
                    cbAutoCheck.setText(getResources().getString(R.string.auto_check));
                    tvCheck.setVisibility(View.GONE);
                    sbCheckTime.setVisibility(View.GONE);
                    cbCheckSound.setVisibility(View.GONE);
                    cbCheckVibr.setVisibility(View.GONE);
                }
                saveSummary();
            }
        });
        sbCheckTime.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                setTimeText();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                saveSummary();
            }
        });
    }

    private void saveSummary() {
        SharedPreferences pref = act.getSharedPreferences(SUMMARY, MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        int p = -1;
        if (cbAutoCheck.isChecked())
            p = sbCheckTime.getProgress();
        editor.putInt(TIME, p);
        editor.putBoolean(SOUND, cbCheckSound.isChecked());
        editor.putBoolean(VIBR, cbCheckVibr.isChecked());
        editor.apply();
        SummaryReceiver.setReceiver(act, p);
    }

    private void initViews() {
        tvCheck = container.findViewById(R.id.tvCheck);
        cbAutoCheck = (CheckBox) container.findViewById(R.id.cbAutoCheck);
        cbCheckSound = (CheckBox) container.findViewById(R.id.cbCheckSound);
        cbCheckVibr = (CheckBox) container.findViewById(R.id.cbCheckVibr);
        sbCheckTime = (SeekBar) container.findViewById(R.id.sbCheckTime);
        SharedPreferences pref = act.getSharedPreferences(SUMMARY, MODE_PRIVATE);
        int p = pref.getInt(TIME, -1);
        if (p > -1) {
            cbAutoCheck.setChecked(true);
            tvCheck.setVisibility(View.VISIBLE);
            sbCheckTime.setVisibility(View.VISIBLE);
            cbCheckSound.setVisibility(View.VISIBLE);
            cbCheckVibr.setVisibility(View.VISIBLE);
            sbCheckTime.setProgress(p);
            setTimeText();
        }
        cbCheckSound.setChecked(pref.getBoolean(SOUND, false));
        cbCheckVibr.setChecked(pref.getBoolean(VIBR, true));
    }

    private void setTimeText() {
        int p = sbCheckTime.getProgress() + 1;
        boolean bH = false;
        if (p > 5) {
            if (p % 6 > 0) {
                p += 5 - p % 6;
                sbCheckTime.setProgress(p);
                return;
            }
            bH = true;
            p = p / 6;
        } else
            p *= 10;
        StringBuilder t = new StringBuilder(getResources().getString(R.string.auto_check));
        t.append(" ");
        if (p == 1)
            t.append(getResources().getString(R.string.each_one));
        else {
            t.append(getResources().getString(R.string.each_more));
            t.append(" ");
            t.append(p);
            t.append(" ");
            if (bH)
                t.append(getResources().getString(R.string.hours));
            else
                t.append(getResources().getString(R.string.minutes));
        }
        cbAutoCheck.setText(t);
    }
}