package ru.neosvet.vestnewage.fragment;

import android.app.Fragment;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import ru.neosvet.utils.Lib;
import ru.neosvet.utils.Prom;
import ru.neosvet.vestnewage.activity.MainActivity;
import ru.neosvet.vestnewage.receiver.PromReceiver;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.receiver.SummaryReceiver;

import static android.content.Context.MODE_PRIVATE;

public class SettingsFragment extends Fragment {
    public static final String PANELS = "panels", SUMMARY = "Summary", PROM = "Prom",
            TIME = "time", SOUND = "sound", VIBR = "vibr";
    private final byte PANEL_BASE = 0, PANEL_CHECK = 1, PANEL_PROM = 2;
    private MainActivity act;
    private TextView tvCheck, tvPromNotif;
    private View container, bSyncTime, pBase;
    private View pCheck, tvCheckOn, tvCheckOff;
    private View pProm, tvPromOn, tvPromOff;
    private ImageView imgBase, imgCheck, imgProm;
    private boolean[] bPanels;
    private CheckBox cbCountFloat, cbMenuMode, cbCheckSound, cbCheckVibr, cbPromSound, cbPromVibr;
    private SeekBar sbCheckTime, sbPromTime;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        this.container = inflater.inflate(R.layout.settings_fragment, container, false);
        act = (MainActivity) getActivity();
        act.setTitle(getResources().getString(R.string.settings));
        initViews();
        setViews();
        restoreActivityState(savedInstanceState);
        return this.container;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putBooleanArray(PANELS, bPanels);
        super.onSaveInstanceState(outState);
    }

    private void restoreActivityState(Bundle state) {
        if (state == null) {
            bPanels = new boolean[]{true, false, false};
            return;
        }
        bPanels = state.getBooleanArray(PANELS);
        if (bPanels[PANEL_BASE]) {
            pBase.setVisibility(View.VISIBLE);
            imgBase.setImageDrawable(getResources().getDrawable(R.drawable.minus));
        }
        if (bPanels[PANEL_CHECK]) {
            pCheck.setVisibility(View.VISIBLE);
            imgCheck.setImageDrawable(getResources().getDrawable(R.drawable.minus));
        }
        if (bPanels[PANEL_PROM]) {
            pProm.setVisibility(View.VISIBLE);
            imgProm.setImageDrawable(getResources().getDrawable(R.drawable.minus));
        }
    }

    private void setViews() {
        container.findViewById(R.id.bBase).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (bPanels[PANEL_BASE]) { //if open
                    pBase.setVisibility(View.GONE);
                    imgBase.setImageDrawable(getResources().getDrawable(R.drawable.plus));
                } else { //if close
                    pBase.setVisibility(View.VISIBLE);
                    imgBase.setImageDrawable(getResources().getDrawable(R.drawable.minus));
                }
                bPanels[PANEL_BASE] = !bPanels[PANEL_BASE];
            }
        });
        container.findViewById(R.id.bCheck).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (bPanels[PANEL_CHECK]) { //if open
                    pCheck.setVisibility(View.GONE);
                    imgCheck.setImageDrawable(getResources().getDrawable(R.drawable.plus));
                } else { //if close
                    pCheck.setVisibility(View.VISIBLE);
                    imgCheck.setImageDrawable(getResources().getDrawable(R.drawable.minus));
                }
                bPanels[PANEL_CHECK] = !bPanels[PANEL_CHECK];
            }
        });
        container.findViewById(R.id.bProm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (bPanels[PANEL_PROM]) { //if open
                    pProm.setVisibility(View.GONE);
                    imgProm.setImageDrawable(getResources().getDrawable(R.drawable.plus));
                } else { //if close
                    pProm.setVisibility(View.VISIBLE);
                    imgProm.setImageDrawable(getResources().getDrawable(R.drawable.minus));
                }
                bPanels[PANEL_PROM] = !bPanels[PANEL_PROM];
            }
        });

        cbCountFloat.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean check) {
                setBaseCheckBox(MainActivity.COUNT_IN_MENU, !check);
                MainActivity.isCountInMenu = !check;
            }
        });
        if (cbMenuMode.getVisibility() == View.VISIBLE)
            cbMenuMode.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean check) {
                    setBaseCheckBox(MainActivity.MENU_MODE, check);
                    MainActivity.isMenuMode = check;
                }
            });
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
        sbCheckTime.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
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
        sbPromTime.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
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
        bSyncTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Prom prom = new Prom(act.getApplicationContext(), null);
                Handler action = new Handler(new Handler.Callback() {
                    @Override
                    public boolean handleMessage(Message message) {
                        int timeleft = message.what;
                        if (timeleft == -1) {
                            Lib.showToast(act, getResources().getString(R.string.sync_error));
                            bSyncTime.setEnabled(true);
                            return false;
                        }
                        float f = ((float) timeleft) / 60000f;
                        if (f < 60f)
                            Lib.showToast(act, String.format(getResources().getString(R.string.prom_in_minute), f));
                        else {
                            f = f / 60f;
                            Lib.showToast(act, String.format(getResources().getString(R.string.prom_in_hour), f));
                        }
                        return false;
                    }
                });
                prom.synchronTime(action);
                bSyncTime.setEnabled(false);

            }
        });
    }

    private void setBaseCheckBox(String name, boolean check) {
        SharedPreferences pref = act.getSharedPreferences(act.getLocalClassName(), MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.putBoolean(name, check);
        editor.apply();
        Intent main = new Intent(act, MainActivity.class);
        main.putExtra(MainActivity.CUR_ID, R.id.nav_settings);
        act.startActivity(main);
        act.finish();
    }

    private void saveSummary() {
        SharedPreferences pref = act.getSharedPreferences(SUMMARY, MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        int p = sbCheckTime.getProgress();
        if (p < sbCheckTime.getMax()) {
            if (p > 5)
                p += (p - 5) * 5;
        } else p = -1;
        editor.putInt(TIME, p);
        editor.putBoolean(SOUND, cbCheckSound.isChecked());
        editor.putBoolean(VIBR, cbCheckVibr.isChecked());
        editor.apply();
        SummaryReceiver.setReceiver(act, p);
    }

    private void saveProm() {
        SharedPreferences pref = act.getSharedPreferences(PROM, MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        int p = sbPromTime.getProgress();
        if (p == sbPromTime.getMax())
            p = -1;
        editor.putInt(TIME, p);
        editor.putBoolean(SOUND, cbPromSound.isChecked());
        editor.putBoolean(VIBR, cbPromVibr.isChecked());
        editor.apply();
        PromReceiver.setReceiver(act, p);
    }

    private void initViews() {
        cbCountFloat = (CheckBox) container.findViewById(R.id.cbCountFloat);
        cbCountFloat.setChecked(!MainActivity.isCountInMenu);
        cbMenuMode = (CheckBox) container.findViewById(R.id.cbMenuMode);
        if (getResources().getInteger(R.integer.screen_mode) < getResources().getInteger(R.integer.screen_tablet_port)) {
            cbMenuMode.setChecked(MainActivity.isMenuMode);
        } else { // else tablet
            cbMenuMode.setVisibility(View.GONE);
        }

        imgBase = (ImageView) container.findViewById(R.id.imgBase);
        imgCheck = (ImageView) container.findViewById(R.id.imgCheck);
        imgProm = (ImageView) container.findViewById(R.id.imgProm);
        pBase = container.findViewById(R.id.pBase);
        pCheck = container.findViewById(R.id.pCheck);
        pProm = container.findViewById(R.id.pProm);

        tvCheck = (TextView) container.findViewById(R.id.tvCheck);
        tvCheckOn = container.findViewById(R.id.tvCheckOn);
        tvCheckOff = container.findViewById(R.id.tvCheckOff);
        cbCheckSound = (CheckBox) container.findViewById(R.id.cbCheckSound);
        cbCheckVibr = (CheckBox) container.findViewById(R.id.cbCheckVibr);
        sbCheckTime = (SeekBar) container.findViewById(R.id.sbCheckTime);
        SharedPreferences pref = act.getSharedPreferences(SUMMARY, MODE_PRIVATE);
        int p = pref.getInt(TIME, -1);
        if (p == -1)
            p = sbCheckTime.getMax();
        sbCheckTime.setProgress(p);
        setCheckTime();
        cbCheckSound.setChecked(pref.getBoolean(SOUND, false));
        cbCheckVibr.setChecked(pref.getBoolean(VIBR, true));

        tvPromNotif = (TextView) container.findViewById(R.id.tvPromNotif);
        tvPromOn = container.findViewById(R.id.tvPromOn);
        tvPromOff = container.findViewById(R.id.tvPromOff);
        cbPromSound = (CheckBox) container.findViewById(R.id.cbPromSound);
        cbPromVibr = (CheckBox) container.findViewById(R.id.cbPromVibr);
        sbPromTime = (SeekBar) container.findViewById(R.id.sbPromTime);
        pref = act.getSharedPreferences(PROM, MODE_PRIVATE);
        p = pref.getInt(TIME, -1);
        if (p == -1)
            p = sbPromTime.getMax();
        sbPromTime.setProgress(p);
        setPromTime();
        cbPromSound.setChecked(pref.getBoolean(SOUND, false));
        cbPromVibr.setChecked(pref.getBoolean(VIBR, true));
        bSyncTime = container.findViewById(R.id.bSyncTime);
    }

    private void setCheckTime() {
        StringBuilder t = new StringBuilder(getResources().getString(R.string.check_summary));
        t.append(" ");
        if (sbCheckTime.getProgress() == sbCheckTime.getMax()) {
            tvCheckOn.setVisibility(View.VISIBLE);
            tvCheckOff.setVisibility(View.GONE);
            t.append(getResources().getString(R.string.turn_off));
            tvCheck.setText(t);
            return;
        }
        tvCheckOn.setVisibility(View.GONE);
        tvCheckOff.setVisibility(View.VISIBLE);
        int p = sbCheckTime.getProgress() + 1;
        boolean bH = false;
        if (p > 5) {
            bH = true;
            p -= 5;
        } else
            p *= 10;
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
        tvCheck.setText(t);
    }

    private void setPromTime() {
        int p = sbPromTime.getProgress();
        if (p == sbPromTime.getMax()) {
            tvPromOn.setVisibility(View.VISIBLE);
            tvPromOff.setVisibility(View.GONE);
            tvPromNotif.setText(getResources().getString(R.string.prom_notif_off));
            return;
        }
        tvPromOn.setVisibility(View.GONE);
        tvPromOff.setVisibility(View.VISIBLE);
        StringBuilder t = new StringBuilder(getResources().getString(R.string.prom_notif));
        t.append(" ");
        t.append(getResources().getString(R.string.in));
        t.append(" ");
        if (p == 0) {
            t.append(getResources().getString(R.string.secs));
//            t.append(getResources().getString(R.string.at_moment_prom));
        } else {
//            t.append(getResources().getString(R.string.in));
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
        tvPromNotif.setText(t);
    }
}