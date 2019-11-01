package ru.neosvet.vestnewage.fragment;

import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.annotation.RequiresApi;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import ru.neosvet.ui.dialogs.SetNotifDialog;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.MainActivity;
import ru.neosvet.vestnewage.helpers.NotificationHelper;
import ru.neosvet.vestnewage.helpers.PromHelper;
import ru.neosvet.vestnewage.helpers.SummaryHelper;
import ru.neosvet.vestnewage.receiver.PromReceiver;

public class SettingsFragment extends Fragment {
    public static final String PANELS = "panels", TIME = "time",
            SUMMARY = "Summary", PROM = "Prom";
    public static final byte TURN_OFF = -1;
    private final byte PANEL_BASE = 0, PANEL_CHECK = 1, PANEL_PROM = 2;
    private MainActivity act;
    private SetNotifDialog dialog = null;
    private TextView tvCheck, tvPromNotif;
    private View container, bSyncTime, pBase;
    private View pCheck, tvCheckOn, tvCheckOff, bCheckSet;
    private View pProm, tvPromOn, tvPromOff, bPromSet;
    private ImageView imgBase, imgCheck, imgProm;
    private boolean[] bPanels;
    private CheckBox cbCountFloat, cbMenuMode;
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
        if (bPanels == null)
            bPanels = new boolean[]{true, false, false};
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
        bSyncTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                PromHelper prom = new PromHelper(act.getApplicationContext(), null);
                Handler action = new Handler(new Handler.Callback() {
                    @Override
                    public boolean handleMessage(Message message) {
                        if (message.what == PromHelper.ERROR) {
                            Lib.showToast(act, getResources().getString(R.string.sync_error));
                            bSyncTime.setEnabled(true);
                            return false;
                        }
                        float f = ((float) message.what) / 60f;
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            initButtonsSet();
        else
            initButtonsSetNew();
    }

    private void initButtonsSet() {
        bCheckSet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog = new SetNotifDialog(act, SUMMARY);
                dialog.show();
            }
        });
        bPromSet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog = new SetNotifDialog(act, PROM);
                dialog.show();
            }
        });
    }

    @RequiresApi(26)
    private void initButtonsSetNew() {
        bCheckSet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, act.getPackageName())
                        .putExtra(Settings.EXTRA_CHANNEL_ID, NotificationHelper.CHANNEL_SUMMARY);
                startActivity(intent);
            }
        });
        bPromSet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, act.getPackageName())
                        .putExtra(Settings.EXTRA_CHANNEL_ID, NotificationHelper.CHANNEL_PROM);
                startActivity(intent);
            }
        });
        container.findViewById(R.id.tvPromSetBattery).setVisibility(View.VISIBLE);
        View bPromSetBattery = container.findViewById(R.id.bPromSetBattery);
        bPromSetBattery.setVisibility(View.VISIBLE);
        bPromSetBattery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                startActivity(intent);
            }
        });
    }

    private void setBaseCheckBox(String name, boolean check) {
        SharedPreferences pref = act.getSharedPreferences(act.getLocalClassName(), Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.putBoolean(name, check);
        editor.apply();
        Intent main = new Intent(act, MainActivity.class);
        main.putExtra(MainActivity.CUR_ID, R.id.nav_settings);
        act.startActivity(main);
        act.finish();
    }

    private void saveSummary() {
        SharedPreferences pref = act.getSharedPreferences(SUMMARY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        int p = sbCheckTime.getProgress();
        if (p < sbCheckTime.getMax()) {
            if (p > 5)
                p += (p - 5) * 5;
        } else p = TURN_OFF;
        editor.putInt(TIME, p);
        editor.apply();
        SummaryHelper.setReceiver(act, p);
    }

    private void saveProm() {
        SharedPreferences pref = act.getSharedPreferences(PROM, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        int p = sbPromTime.getProgress();
        if (p == sbPromTime.getMax())
            p = TURN_OFF;
        editor.putInt(TIME, p);
        editor.apply();
        PromReceiver.setReceiver(act, p);
    }

    private void initViews() {
        cbCountFloat = container.findViewById(R.id.cbCountFloat);
        cbCountFloat.setChecked(!MainActivity.isCountInMenu);
        if (MainActivity.isMenuMode)
            cbCountFloat.setText(getResources().getString(R.string.count_everywhere));
        cbMenuMode = container.findViewById(R.id.cbMenuMode);
        if (getResources().getInteger(R.integer.screen_mode) < getResources().getInteger(R.integer.screen_tablet_port)) {
            cbMenuMode.setChecked(MainActivity.isMenuMode);
        } else { // else tablet
            cbMenuMode.setVisibility(View.GONE);
        }

        imgBase = container.findViewById(R.id.imgBase);
        imgCheck = container.findViewById(R.id.imgCheck);
        imgProm = container.findViewById(R.id.imgProm);
        pBase = container.findViewById(R.id.pBase);
        pCheck = container.findViewById(R.id.pCheck);
        pProm = container.findViewById(R.id.pProm);

        tvCheck = container.findViewById(R.id.tvCheck);
        tvCheckOn = container.findViewById(R.id.tvCheckOn);
        tvCheckOff = container.findViewById(R.id.tvCheckOff);
        sbCheckTime = container.findViewById(R.id.sbCheckTime);
        bCheckSet = container.findViewById(R.id.bCheckSet);
        SharedPreferences pref = act.getSharedPreferences(SUMMARY, Context.MODE_PRIVATE);
        int p = pref.getInt(TIME, TURN_OFF);
        if (p == TURN_OFF)
            p = sbCheckTime.getMax();
        sbCheckTime.setProgress(p);
        setCheckTime();

        tvPromNotif = container.findViewById(R.id.tvPromNotif);
        tvPromOn = container.findViewById(R.id.tvPromOn);
        tvPromOff = container.findViewById(R.id.tvPromOff);
        sbPromTime = container.findViewById(R.id.sbPromTime);
        bPromSet = container.findViewById(R.id.bPromSet);
        pref = act.getSharedPreferences(PROM, Context.MODE_PRIVATE);
        p = pref.getInt(TIME, TURN_OFF);
        if (p == TURN_OFF)
            p = sbPromTime.getMax();
        sbPromTime.setProgress(p);
        setPromTime();
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
            bCheckSet.setEnabled(false);
            return;
        }
        bCheckSet.setEnabled(true);
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
            bPromSet.setEnabled(false);
            return;
        }
        bPromSet.setEnabled(true);
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

    public void putRingtone(Intent data) {
        if (data == null) return;
        Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
        if (uri == null) return;
        Ringtone ringTone = RingtoneManager.getRingtone(act, uri);
        dialog.putRingtone(ringTone.getTitle(act), uri.toString());
    }

    public void putCustom(Intent data) {
        if (data == null) return;
        String name, uri;
        Cursor cursor = null;
        try {
            uri = data.getData().toString().replace("%3A", "/");
            String id = uri.substring(uri.lastIndexOf("/") + 1);
            cursor = act.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Audio.Media.DATA},
                    MediaStore.Audio.Media._ID + DataBase.Q,
                    new String[]{id}, null);
            cursor.moveToFirst();
            uri = cursor.getString(0);
            name = uri.substring(uri.lastIndexOf("/") + 1);
        } catch (Exception e) {
            return;
        } finally {
            if (cursor != null)
                cursor.close();
        }
        dialog.putRingtone(name, uri);
    }
}