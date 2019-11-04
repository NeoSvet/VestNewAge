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
import android.widget.RadioButton;
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
    private final byte PANEL_BASE = 0, PANEL_SCREEN = 1, PANEL_CLEAR = 2, PANEL_CHECK = 3, PANEL_PROM = 4;
    private MainActivity act;
    private SetNotifDialog dialog = null;
    private TextView tvCheck, tvPromNotif;
    private View[] pSections;
    private View container, bSyncTime, bClearDo;
    private View tvCheckOn, tvCheckOff, bCheckSet;
    private View tvPromOn, tvPromOff, bPromSet;
    private ImageView[] imgSections;
    private boolean[] bPanels;
    private CheckBox cbCountFloat, cbNew;
    private RadioButton[] rbsScreen;
    private CheckBox[] cbsClear;
    private SeekBar sbCheckTime, sbPromTime;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        this.container = inflater.inflate(R.layout.settings_fragment, container, false);
        act = (MainActivity) getActivity();
        act.setTitle(getResources().getString(R.string.settings));
        initSections();
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
            bPanels = new boolean[]{true, false, false, false, false};
            return;
        }
        bPanels = state.getBooleanArray(PANELS);
        if (bPanels == null)
            bPanels = new boolean[]{true, false, false, false, false};
        for (int i = 0; i < bPanels.length; i++) {
            if (bPanels[i]) {
                pSections[i].setVisibility(View.VISIBLE);
                imgSections[i].setImageDrawable(getResources().getDrawable(R.drawable.minus));
            }
        }
    }

    private void initSections() {
        imgSections = new ImageView[]{container.findViewById(R.id.imgBase),
                container.findViewById(R.id.imgScreen), container.findViewById(R.id.imgClear),
                container.findViewById(R.id.imgCheck), container.findViewById(R.id.imgProm)};
        pSections = new View[]{container.findViewById(R.id.pBase),
                container.findViewById(R.id.pScreen), container.findViewById(R.id.pClear),
                container.findViewById(R.id.pCheck), container.findViewById(R.id.pProm)};
        View[] bSections = new View[]{container.findViewById(R.id.bBase),
                container.findViewById(R.id.bScreen), container.findViewById(R.id.bClear),
                container.findViewById(R.id.bCheck), container.findViewById(R.id.bProm)};

        View.OnClickListener SectionsClick = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int section;
                switch (view.getId()) {
                    case R.id.bBase:
                        section = PANEL_BASE;
                        break;
                    case R.id.bScreen:
                        section = PANEL_SCREEN;
                        break;
                    case R.id.bClear:
                        section = PANEL_CLEAR;
                        break;
                    case R.id.bCheck:
                        section = PANEL_CHECK;
                        break;
                    default:
                        section = PANEL_PROM;
                        break;
                }
                if (bPanels[section]) { //if open
                    pSections[section].setVisibility(View.GONE);
                    imgSections[section].setImageDrawable(getResources().getDrawable(R.drawable.plus));
                } else { //if close
                    pSections[section].setVisibility(View.VISIBLE);
                    imgSections[section].setImageDrawable(getResources().getDrawable(R.drawable.minus));
                }
                bPanels[section] = !bPanels[section];
            }
        };
        for (int i = 0; i < bSections.length; i++)
            bSections[i].setOnClickListener(SectionsClick);
    }

    private void initViews() {
        cbCountFloat = container.findViewById(R.id.cbCountFloat);
        cbCountFloat.setChecked(!MainActivity.isCountInMenu);
        if (act.isMenuMode)
            cbCountFloat.setText(getResources().getString(R.string.count_everywhere));
        cbNew = container.findViewById(R.id.cbNew);
        SharedPreferences pref = act.getSharedPreferences(MainActivity.class.getSimpleName(), Context.MODE_PRIVATE);
        cbNew.setChecked(pref.getBoolean(MainActivity.START_NEW, false));

        bClearDo = container.findViewById(R.id.bClearDo);
        rbsScreen = new RadioButton[]{container.findViewById(R.id.rbMenu),
                container.findViewById(R.id.rbCalendar),
                container.findViewById(R.id.rbSummary)};
        if (getResources().getInteger(R.integer.screen_mode) >= getResources().getInteger(R.integer.screen_tablet_port)) {
            rbsScreen[0].setVisibility(View.GONE);
        }
        int p = pref.getInt(MainActivity.START_SCEEN, 1);
        rbsScreen[p].setChecked(true);
        cbsClear = new CheckBox[]{container.findViewById(R.id.cbKatreny),
                container.findViewById(R.id.cbPoslaniya),
                container.findViewById(R.id.cbMaterials),
                container.findViewById(R.id.cbMarkers)};

        tvCheck = container.findViewById(R.id.tvCheck);
        tvCheckOn = container.findViewById(R.id.tvCheckOn);
        tvCheckOff = container.findViewById(R.id.tvCheckOff);
        sbCheckTime = container.findViewById(R.id.sbCheckTime);
        bCheckSet = container.findViewById(R.id.bCheckSet);
        pref = act.getSharedPreferences(SUMMARY, Context.MODE_PRIVATE);
        p = pref.getInt(TIME, TURN_OFF);
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

    private void setViews() {
        cbCountFloat.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean check) {
                setMainCheckBox(MainActivity.COUNT_IN_MENU, !check, -1);
                MainActivity.isCountInMenu = !check;
            }
        });
        cbNew.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean check) {
                setMainCheckBox(MainActivity.START_NEW, check, -1);
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

        CheckBox.OnCheckedChangeListener ScreenChecked = new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                if (checked) {
                    int sel;
                    switch (compoundButton.getId()) {
                        case R.id.rbMenu:
                            sel = 0;
                            break;
                        case R.id.rbCalendar:
                            sel = 1;
                            break;
                        default:
                            sel = 2;
                            break;
                    }
                    setMainCheckBox(MainActivity.START_SCEEN, false, sel);
                }
            }
        };
        for (int i = 0; i < rbsScreen.length; i++)
            rbsScreen[i].setOnCheckedChangeListener(ScreenChecked);

        CheckBox.OnCheckedChangeListener ClearChecked = new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                int k = 0;
                for (int i = 0; i < cbsClear.length; i++) {
                    if (cbsClear[i].isChecked())
                        k++;
                }
                bClearDo.setEnabled(k > 0);
            }
        };
        for (int i = 0; i < cbsClear.length; i++)
            cbsClear[i].setOnCheckedChangeListener(ClearChecked);
        bClearDo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //TODO tut
                for (int i = 0; i < cbsClear.length; i++)
                    cbsClear[i].setChecked(false);
                bClearDo.setEnabled(false);
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

    private void setMainCheckBox(String name, boolean check, int value) {
        SharedPreferences pref = act.getSharedPreferences(MainActivity.class.getSimpleName(), Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        if (value == -1)
            editor.putBoolean(name, check);
        else
            editor.putInt(name, value);
        editor.apply();
        if (name.equals(MainActivity.START_NEW))
            return;
        Intent main = new Intent(act, MainActivity.class);
        if (value == -1)
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