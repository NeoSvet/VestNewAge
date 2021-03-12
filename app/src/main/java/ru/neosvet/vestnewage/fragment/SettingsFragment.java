package ru.neosvet.vestnewage.fragment;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import ru.neosvet.ui.dialogs.SetNotifDialog;
import ru.neosvet.utils.BackFragment;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.MainActivity;
import ru.neosvet.vestnewage.helpers.NotificationHelper;
import ru.neosvet.vestnewage.helpers.ProgressHelper;
import ru.neosvet.vestnewage.helpers.PromHelper;
import ru.neosvet.vestnewage.model.BaseModel;
import ru.neosvet.vestnewage.workers.CheckWorker;

public class SettingsFragment extends BackFragment implements Observer<Data> {
    private final byte PANEL_BASE = 0, PANEL_SCREEN = 1, PANEL_CLEAR = 2, PANEL_CHECK = 3, PANEL_PROM = 4;
    private MainActivity act;
    private SetNotifDialog dialog = null;
    private TextView tvCheck, tvPromNotif;
    private View[] pSections;
    private View container, bClearDo, ivClear;
    private View tvCheckOn, tvCheckOff, bCheckSet;
    private View tvPromOn, tvPromOff, bPromSet;
    private ImageView[] imgSections;
    private boolean[] bPanels;
    private CheckBox cbCountFloat, cbNew;
    private RadioButton[] rbsScreen;
    private CheckBox[] cbsClear;
    private SeekBar sbCheckTime, sbPromTime;
    private BaseModel model;
    private Animation anRotate;
    private boolean stopRotate;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        this.container = inflater.inflate(R.layout.settings_fragment, container, false);
        act = (MainActivity) getActivity();
        act.setTitle(getResources().getString(R.string.settings));
        initSections();
        initViews();
        setViews();
        model = new ViewModelProvider(this).get(BaseModel.class);
        restoreState(savedInstanceState);
        return this.container;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (ProgressHelper.isBusy())
            ProgressHelper.removeObservers(act);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (ProgressHelper.isBusy())
            initProgress();
    }

    private void initProgress() {
        bClearDo.setEnabled(false);
        initRotate();
        ProgressHelper.addObserver(act, this);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putBooleanArray(Const.PANEL, bPanels);
        super.onSaveInstanceState(outState);
    }

    private void restoreState(Bundle state) {
        if (state == null) {
            bPanels = new boolean[]{true, false, false, false, false};
            return;
        }
        act.setCurFragment(this);
        bPanels = state.getBooleanArray(Const.PANEL);
        if (bPanels == null)
            bPanels = new boolean[]{true, false, false, false, false};
        for (int i = 0; i < bPanels.length; i++) {
            if (bPanels[i]) {
                pSections[i].setVisibility(View.VISIBLE);
                imgSections[i].setImageDrawable(getResources().getDrawable(R.drawable.minus));
            }
        }
    }

    @Override
    public void onChanged(@Nullable Data data) {
        if (!ProgressHelper.isBusy())
            return;
        if (data.getBoolean(Const.FINISH, false)) {
            ProgressHelper.removeObservers(act);
            stopRotate = true;
            ProgressHelper.setBusy(false);
            bClearDo.setEnabled(false);
            String error = data.getString(Const.ERROR);
            if (error != null) {
                Lib.showToast(act, getResources().getString(R.string.error)
                        + ": " + error);
                return;
            }
            float size = data.getLong(Const.PROG, 0) / 1024 / 1024f;
            Lib.showToast(act, getResources().getString(R.string.freed)
                    + String.format("%.2f", size) + getResources().getString(R.string.mb));
        }
    }

    private void initRotate() {
        stopRotate = false;
        if (anRotate == null) {
            ivClear = container.findViewById(R.id.ivClear);
            anRotate = AnimationUtils.loadAnimation(act, R.anim.rotate);
            anRotate.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {

                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    if (stopRotate)
                        ivClear.setVisibility(View.GONE);
                    else
                        ivClear.startAnimation(anRotate);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {

                }
            });
        }
        ivClear.setVisibility(View.VISIBLE);
        ivClear.startAnimation(anRotate);
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
        cbNew.setChecked(pref.getBoolean(Const.START_NEW, false));

        bClearDo = container.findViewById(R.id.bClearDo);
        rbsScreen = new RadioButton[]{container.findViewById(R.id.rbMenu),
                container.findViewById(R.id.rbCalendar),
                container.findViewById(R.id.rbSummary)};
        if (getResources().getInteger(R.integer.screen_mode) >= getResources().getInteger(R.integer.screen_tablet_port)) {
            rbsScreen[0].setVisibility(View.GONE);
        }
        int p = pref.getInt(Const.START_SCEEN, Const.SCREEN_CALENDAR);
        rbsScreen[p].setChecked(true);
        cbsClear = new CheckBox[]{container.findViewById(R.id.cbBookPrev),
                container.findViewById(R.id.cbBookCur),
                container.findViewById(R.id.cbMaterials),
                container.findViewById(R.id.cbMarkers),
                container.findViewById(R.id.cbCache)};
        tvCheck = container.findViewById(R.id.tvCheck);
        tvCheckOn = container.findViewById(R.id.tvCheckOn);
        tvCheckOff = container.findViewById(R.id.tvCheckOff);
        sbCheckTime = container.findViewById(R.id.sbCheckTime);
        bCheckSet = container.findViewById(R.id.bCheckSet);
        pref = act.getSharedPreferences(Const.SUMMARY, Context.MODE_PRIVATE);
        p = pref.getInt(Const.TIME, Const.TURN_OFF);
        if (p == Const.TURN_OFF)
            sbCheckTime.setProgress(sbCheckTime.getMax());
        else {
            p /= 15;
            if (p > 3)
                p = p / 4 + 2;
            else
                p--;
            sbCheckTime.setProgress(p);
        }
        setCheckTime();

        tvPromNotif = container.findViewById(R.id.tvPromNotif);
        tvPromOn = container.findViewById(R.id.tvPromOn);
        tvPromOff = container.findViewById(R.id.tvPromOff);
        sbPromTime = container.findViewById(R.id.sbPromTime);
        bPromSet = container.findViewById(R.id.bPromSet);
        pref = act.getSharedPreferences(Const.PROM, Context.MODE_PRIVATE);
        p = pref.getInt(Const.TIME, Const.TURN_OFF);
        if (p == Const.TURN_OFF)
            p = sbPromTime.getMax();
        sbPromTime.setProgress(p);
        setPromTime();
    }

    private void setViews() {
        cbCountFloat.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean check) {
                setMainCheckBox(Const.COUNT_IN_MENU, !check, -1);
                MainActivity.isCountInMenu = !check;
            }
        });
        cbNew.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean check) {
                setMainCheckBox(Const.START_NEW, check, -1);
            }
        });

        CheckBox.OnCheckedChangeListener ScreenChecked = new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                if (checked) {
                    byte sel;
                    switch (compoundButton.getId()) {
                        case R.id.rbMenu:
                            sel = Const.SCREEN_MENU;
                            break;
                        case R.id.rbCalendar:
                            sel = Const.SCREEN_CALENDAR;
                            break;
                        default:
                            sel = Const.SCREEN_SUMMARY;
                            break;
                    }
                    setMainCheckBox(Const.START_SCEEN, false, sel);
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
                initRotate();
                bClearDo.setEnabled(false);
                List<String> list = new ArrayList<String>();
                if (cbsClear[0].isChecked()) //book prev years
                    list.add(Const.START);
                if (cbsClear[1].isChecked()) //book cur year
                    list.add(Const.END);
                if (cbsClear[2].isChecked()) //materials
                    list.add(DataBase.ARTICLES);
                if (cbsClear[3].isChecked()) //markers
                    list.add(DataBase.MARKERS);
                if (cbsClear[4].isChecked()) //cache
                    list.add(Const.FILE);
                initProgress();
                model.startClear(list.toArray(new String[]{}));
                for (int i = 0; i < cbsClear.length; i++)
                    cbsClear[i].setChecked(false);
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
                dialog = new SetNotifDialog(act, Const.SUMMARY);
                dialog.show();
            }
        });
        bPromSet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog = new SetNotifDialog(act, Const.PROM);
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
        container.findViewById(R.id.pBattery).setVisibility(View.VISIBLE);
        container.findViewById(R.id.bSetBattery).setOnClickListener(new View.OnClickListener() {
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
        if (name.equals(Const.START_NEW))
            return;
        Intent main = new Intent(act, MainActivity.class);
        if (value == -1)
            main.putExtra(Const.CUR_ID, R.id.nav_settings);
        act.startActivity(main);
        act.finish();
    }

    private void saveSummary() {
        SharedPreferences pref = act.getSharedPreferences(Const.SUMMARY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        int p = sbCheckTime.getProgress();
        if (p < sbCheckTime.getMax()) {
            if (p > 2)
                p = (p - 2) * 4;
            else
                p++;
            p = p * 15;
        } else p = Const.TURN_OFF;
        editor.putInt(Const.TIME, p);
        editor.apply();
        WorkManager work = WorkManager.getInstance(act.getApplicationContext());
        work.cancelAllWorkByTag(CheckWorker.TAG_PERIODIC);
        if (p == Const.TURN_OFF)
            return;
        if (p == 15) p = 20;
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build();
        Data data = new Data.Builder()
                .putBoolean(Const.CHECK, true)
                .build();
        PeriodicWorkRequest task = new PeriodicWorkRequest
                .Builder(CheckWorker.class, p, TimeUnit.MINUTES, p - 5, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setInputData(data)
                .addTag(CheckWorker.TAG_PERIODIC)
                .build();
        work.enqueue(task);
    }

    private void saveProm() {
        SharedPreferences pref = act.getSharedPreferences(Const.PROM, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        int p = sbPromTime.getProgress();
        if (p == sbPromTime.getMax())
            p = Const.TURN_OFF;
        editor.putInt(Const.TIME, p);
        editor.apply();
        PromHelper prom = new PromHelper(act, null);
        prom.initNotif(p);
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
        if (p > 3) {
            bH = true;
            p -= 3;
        } else
            p *= 15;
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
        p++;
        if (p == 1) {
            t.append(getResources().getStringArray(R.array.time)[3]);
        } else if (p > 4 && p < 21) {
            t.append(p);
            t.append(" ");
            t.append(getResources().getStringArray(R.array.time)[4]);
        } else {
            t.append(p);
            t.append(" ");
            int n = p % 10;
            if (n == 1)
                t.append(getResources().getStringArray(R.array.time)[3]);
            else if (n > 1 && n < 5)
                t.append(getResources().getStringArray(R.array.time)[5]);
            else
                t.append(getResources().getStringArray(R.array.time)[4]);
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