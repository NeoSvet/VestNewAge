package ru.neosvet.vestnewage.fragment;

import android.app.Fragment;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.SeekBar;

import ru.neosvet.vestnewage.activity.MainActivity;
import ru.neosvet.vestnewage.receiver.PromReceiver;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.receiver.SummaryReceiver;

import static android.content.Context.MODE_PRIVATE;

public class SettingsFragment extends Fragment {
    public static final String SUMMARY = "Summary", PROM = "Prom",
            TIME = "time", SOUND = "sound", VIBR = "vibr";
    private MainActivity act;
    private View container, tvCheck;
    private CheckBox cbCheckAuto, cbCheckSound, cbCheckVibr, cbPromNotif, cbPromSound, cbPromVibr;
    private SeekBar sbCheckTime, sbPromTime;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        this.container = inflater.inflate(R.layout.settings_fragment, container, false);
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
        cbCheckAuto.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean check) {
                if (check) {
                    setCheckTime();
                    tvCheck.setVisibility(View.VISIBLE);
                    sbCheckTime.setVisibility(View.VISIBLE);
                    cbCheckSound.setVisibility(View.VISIBLE);
                    cbCheckVibr.setVisibility(View.VISIBLE);
                } else {
                    cbCheckAuto.setText(getResources().getString(R.string.auto_check));
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
                setCheckTime();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                saveSummary();
            }
        });

        cbPromSound.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean check) {
                saveProm();
            }
        });
        cbPromVibr.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean check) {
                saveProm();
            }
        });
        cbPromNotif.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean check) {
                if (check) {
                    setPromTime();
                    sbPromTime.setVisibility(View.VISIBLE);
                    cbPromSound.setVisibility(View.VISIBLE);
                    cbPromVibr.setVisibility(View.VISIBLE);
                } else {
                    cbPromNotif.setText(getResources().getString(R.string.prom_notif));
                    sbPromTime.setVisibility(View.GONE);
                    cbPromSound.setVisibility(View.GONE);
                    cbPromVibr.setVisibility(View.GONE);
                }
                saveProm();
            }
        });
        sbPromTime.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                setPromTime();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                saveProm();
            }
        });
    }

    private void saveSummary() {
        SharedPreferences pref = act.getSharedPreferences(SUMMARY, MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        int p = -1;
        if (cbCheckAuto.isChecked())
            p = sbCheckTime.getProgress();
        editor.putInt(TIME, p);
        editor.putBoolean(SOUND, cbCheckSound.isChecked());
        editor.putBoolean(VIBR, cbCheckVibr.isChecked());
        editor.apply();
        SummaryReceiver.setReceiver(act, p);
    }

    private void saveProm() {
        SharedPreferences pref = act.getSharedPreferences(PROM, MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        int p = -1;
        if (cbPromNotif.isChecked())
            p = sbPromTime.getProgress();
        editor.putInt(TIME, p);
        editor.putBoolean(SOUND, cbPromSound.isChecked());
        editor.putBoolean(VIBR, cbPromVibr.isChecked());
        editor.apply();
        PromReceiver.setReceiver(act, p, false);
    }

    private void initViews() {
        tvCheck = container.findViewById(R.id.tvCheck);
        cbCheckAuto = (CheckBox) container.findViewById(R.id.cbCheckAuto);
        cbCheckSound = (CheckBox) container.findViewById(R.id.cbCheckSound);
        cbCheckVibr = (CheckBox) container.findViewById(R.id.cbCheckVibr);
        sbCheckTime = (SeekBar) container.findViewById(R.id.sbCheckTime);
        SharedPreferences pref = act.getSharedPreferences(SUMMARY, MODE_PRIVATE);
        int p = pref.getInt(TIME, -1);
        if (p > -1) {
            cbCheckAuto.setChecked(true);
            tvCheck.setVisibility(View.VISIBLE);
            sbCheckTime.setVisibility(View.VISIBLE);
            cbCheckSound.setVisibility(View.VISIBLE);
            cbCheckVibr.setVisibility(View.VISIBLE);
            sbCheckTime.setProgress(p);
            setCheckTime();
        }
        cbCheckSound.setChecked(pref.getBoolean(SOUND, false));
        cbCheckVibr.setChecked(pref.getBoolean(VIBR, true));

        cbPromNotif = (CheckBox) container.findViewById(R.id.cbPromNotif);
        cbPromSound = (CheckBox) container.findViewById(R.id.cbPromSound);
        cbPromVibr = (CheckBox) container.findViewById(R.id.cbPromVibr);
        sbPromTime = (SeekBar) container.findViewById(R.id.sbPromTime);
        pref = act.getSharedPreferences(PROM, MODE_PRIVATE);
        p = pref.getInt(TIME, -1);
        if (p > -1) {
            cbPromNotif.setChecked(true);
            sbPromTime.setVisibility(View.VISIBLE);
            cbPromSound.setVisibility(View.VISIBLE);
            cbPromVibr.setVisibility(View.VISIBLE);
            sbPromTime.setProgress(p);
            setPromTime();
        }
        cbPromSound.setChecked(pref.getBoolean(SOUND, false));
        cbPromVibr.setChecked(pref.getBoolean(VIBR, true));
    }

    private void setCheckTime() {
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
        cbCheckAuto.setText(t);
    }

    private void setPromTime() {
        int p = sbPromTime.getProgress();
        StringBuilder t = new StringBuilder(getResources().getString(R.string.prom_notif));
        t.append(" ");
        t.append(getResources().getString(R.string.on));
        t.append(" ");
        if (p == 0) {
            t.append(getResources().getString(R.string.secs));
//            t.append(getResources().getString(R.string.at_moment_prom));
        } else {
//            t.append(getResources().getString(R.string.on));
//            t.append(" ");
            if (p > 4 && p < 21)
                t.append(p + " " + getResources().getStringArray(R.array.time)[4]);
            else {
                if (p == 1)
                    t.append(getResources().getStringArray(R.array.time)[3]);
                else {
                    int n = (int) p % 10;
                    if (n == 1)
                        t.append(p + " " + getResources().getStringArray(R.array.time)[3]);
                    else if (n > 1 && n < 5)
                        t.append(p + " " + getResources().getStringArray(R.array.time)[5]);
                    else
                        t.append(p + " " + getResources().getStringArray(R.array.time)[4]);
                }
            }
        }
        cbPromNotif.setText(t);
    }
}