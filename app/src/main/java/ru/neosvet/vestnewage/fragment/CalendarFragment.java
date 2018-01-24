package ru.neosvet.vestnewage.fragment;

import android.app.AlertDialog;
import android.app.Fragment;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import ru.neosvet.ui.CalendarAdapter;
import ru.neosvet.ui.CalendarItem;
import ru.neosvet.ui.DateDialog;
import ru.neosvet.ui.ListAdapter;
import ru.neosvet.ui.ListItem;
import ru.neosvet.ui.RecyclerItemClickListener;
import ru.neosvet.ui.ResizeAnim;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.BrowserActivity;
import ru.neosvet.vestnewage.activity.MainActivity;
import ru.neosvet.vestnewage.task.CalendarTask;

public class CalendarFragment extends Fragment implements DateDialog.Result {
    public static final String CURRENT_DATE = "current_date", ADS = "ads", FOLDER = "/calendar/";
    private int today_m, today_y, iNew = -1;
    private CalendarAdapter adCalendar;
    private RecyclerView rvCalendar;
    private Date dCurrent;
    private TextView tvDate, tvNew;
    private ListView lvNoread;
    private View container, tvEmpty, pCalendar, ivPrev, ivNext, fabRefresh, fabClose, fabClear;
    private CalendarTask task = null;
    private ListAdapter adNoread;
    private MainActivity act;
    private DateDialog dateDialog;
    private AlertDialog alert;
    private Animation anShow, anHide;
    private int dialog = -2;
    private boolean boolShow = false;
    final Handler hTimer = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message message) {
            if (message.what == 0)
                tvEmpty.startAnimation(anHide);
            else
                tvDate.setBackgroundDrawable(getResources().getDrawable(R.drawable.press));
            return false;
        }
    });

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        this.container = inflater.inflate(R.layout.calendar_fragment, container, false);
        act = (MainActivity) getActivity();
        act.setTitle(getResources().getString(R.string.calendar));
        initViews();
        setViews();
        initCalendar();
        restoreActivityState(savedInstanceState);
        return this.container;
    }

    public void setNew(int n) {
        iNew = n;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(Const.DIALOG, dialog);
        if (dialog == -1)
            dateDialog.dismiss();
        else if (dialog > -1)
            alert.dismiss();
        outState.putBoolean(Const.NOREAD, (lvNoread.getVisibility() == View.VISIBLE));
        outState.putLong(CURRENT_DATE, dCurrent.getTime());
        outState.putSerializable(Const.TASK, task);
        super.onSaveInstanceState(outState);
    }

    private void restoreActivityState(Bundle state) {
        if (state == null) {
            Date d = new Date();
            dCurrent = new Date(d.getYear(), d.getMonth(), d.getDate());
        } else {
            act.setFrCalendar(this);
            dCurrent = new Date(state.getLong(CURRENT_DATE));
            task = (CalendarTask) state.getSerializable(Const.TASK);
            if (task != null) {
                if (task.getStatus() == AsyncTask.Status.RUNNING) {
                    task.setFrm(this);
                    setStatus(true);
                } else task = null;
            }
            if (state.getBoolean(Const.NOREAD, false)) {
                pCalendar.setVisibility(View.GONE);
                tvNew.setVisibility(View.GONE);
                fabRefresh.setVisibility(View.GONE);
                fabClear.setVisibility(View.VISIBLE);
                fabClose.setVisibility(View.VISIBLE);
                lvNoread.getLayoutParams().height = (int) (getResources().getInteger(R.integer.height_list)
                        * getResources().getDisplayMetrics().density);
                lvNoread.requestLayout();
                lvNoread.setVisibility(View.VISIBLE);
            }
            dialog = state.getInt(Const.DIALOG);
            if (dialog == -1)
                showDatePicker();
            else if (dialog > -1) {
                createNoreadList(false);
                showAd(adNoread.getItem(dialog).getLink(),
                        adNoread.getItem(dialog).getHead(0));
            }
        }
        createCalendar(0);
    }

    private void setViews() {
        if (iNew > -1) {
            tvNew.setText(Integer.toString(iNew));
            iNew = -1;
        }
        tvNew.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (tvNew.getText().toString().equals("0")) {
                    if (boolShow)
                        return;
                    boolShow = true;
                    tvEmpty.setVisibility(View.VISIBLE);
                    tvEmpty.startAnimation(anShow);
                    new Timer().schedule(new TimerTask() {
                        @Override
                        public void run() {
                            hTimer.sendEmptyMessage(0);
                        }
                    }, 2500);
                    return;
                }
                if (tvNew.getText().toString().equals("...")) {
                    adNoread.clear();
                    adNoread.addItem(new ListItem(getResources().getString(R.string.no_list), ""));
                }
                pCalendar.setVisibility(View.GONE);
                tvNew.setVisibility(View.GONE);
                fabRefresh.setVisibility(View.GONE);
                fabClear.setVisibility(View.VISIBLE);
                fabClose.setVisibility(View.VISIBLE);
                lvNoread.setVisibility(View.VISIBLE);
                ResizeAnim anim = new ResizeAnim(lvNoread, false,
                        (int) (getResources().getInteger(R.integer.height_list)
                                * getResources().getDisplayMetrics().density));
                anim.setDuration(800);
                lvNoread.clearAnimation();
                lvNoread.startAnimation(anim);
            }
        });
        lvNoread.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int pos, long l) {
                if (adNoread.getItem(pos).getTitle().contains(getResources().getString(R.string.ad))) {
                    String link = adNoread.getItem(pos).getLink();
                    String des = adNoread.getItem(pos).getHead(0);
                    if (des.equals("")) // only link
                        act.lib.openInApps(link, null);
                    else {
                        dialog = pos;
                        showAd(link, des);
                    }
                } else if (!adNoread.getItem(pos).getLink().equals("")) {
                    openLink(adNoread.getItem(pos).getLink());
                }
            }
        });
        fabClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                closeNoread();
            }
        });
        fabClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                closeNoread();
                tvNew.setText("0");
                File file = new File(act.getFilesDir() + File.separator + Const.NOREAD);
                BufferedWriter bw;
                try {
                    if (file.exists()) {
                        bw = new BufferedWriter(new FileWriter(file));
                        bw.write(""); // затираем файл списка непрочитанного
                        bw.close();
                    }
                    file = new File(act.getFilesDir() + File.separator + ADS);
                    if (file.exists()) {
                        BufferedReader br = new BufferedReader(new FileReader(file));
                        String t = br.readLine(); // читаем время последней загрузки объявлений
                        br.close();
                        bw = new BufferedWriter(new FileWriter(file));
                        bw.write(t); // затираем файл объявлений, оставляем лишь время загрузки
                        bw.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        fabRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startLoad();
            }
        });
        act.status.setClick(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (act.status.onClick()) {
                    tvNew.setVisibility(View.VISIBLE);
                    fabRefresh.setVisibility(View.VISIBLE);
                } else if (act.status.isTime())
                    startLoad();
            }
        });
    }

    private void showAd(final String link, String des) {
        AlertDialog.Builder builder = new AlertDialog.Builder(act);
        LayoutInflater inflater = act.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_layout, null);
        builder.setView(dialogView);
        TextView tv = (TextView) dialogView.findViewById(R.id.title);
        tv.setText(getResources().getString(R.string.ad));
        tv = (TextView) dialogView.findViewById(R.id.message);
        tv.setText(des);
        Button button = (Button) dialogView.findViewById(R.id.rightButton);
        alert = builder.create();
        alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                dialog = -2;
            }
        });
        if (link.equals("")) { // only des
            button.setText(getResources().getString(android.R.string.ok));
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    alert.dismiss();
                }
            });
        } else {
            button.setText(getResources().getString(R.string.open_link));
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    act.lib.openInApps(link, null);
                    alert.dismiss();
                }
            });
        }
        alert.show();
    }

    private void closeNoread() {
        ResizeAnim anim = new ResizeAnim(lvNoread, false, 10);
        anim.setDuration(600);
        anim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                pCalendar.setVisibility(View.VISIBLE);
                tvNew.setVisibility(View.VISIBLE);
                fabRefresh.setVisibility(View.VISIBLE);
                fabClear.setVisibility(View.GONE);
                fabClose.setVisibility(View.GONE);
                lvNoread.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        lvNoread.clearAnimation();
        lvNoread.startAnimation(anim);
    }

    public void clearDays() {
        for (int i = 0; i < adCalendar.getItemCount(); i++) {
            adCalendar.getItem(i).clear();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adNoread.getCount() == 0) {
            createNoreadList(true);
            if (adNoread.getCount() == 0 && lvNoread.getVisibility() == View.VISIBLE) {
                closeNoread();
            }
        }
    }

    private void initViews() {
        Date d = new Date();
        today_m = d.getMonth();
        today_y = d.getYear();
        tvNew = (TextView) container.findViewById(R.id.tvNew);
        fabRefresh = container.findViewById(R.id.fabRefresh);
        fabClose = container.findViewById(R.id.fabClose);
        fabClear = container.findViewById(R.id.fabClear);
        tvEmpty = container.findViewById(R.id.tvEmptyList);
        lvNoread = (ListView) container.findViewById(R.id.lvNoread);
        adNoread = new ListAdapter(act);
        lvNoread.setAdapter(adNoread);
        anShow = AnimationUtils.loadAnimation(act, R.anim.show);
        anHide = AnimationUtils.loadAnimation(act, R.anim.hide);
        anHide.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                tvEmpty.setVisibility(View.GONE);
                boolShow = false;
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
    }


    public boolean onBackPressed() {
        if (lvNoread.getVisibility() == View.VISIBLE) {
            closeNoread();
            return false;
        }
        return true;
    }

    private void initCalendar() {
        pCalendar = container.findViewById(R.id.pCalendar);
        tvDate = (TextView) container.findViewById(R.id.tvDate);
        ivPrev = container.findViewById(R.id.ivPrev);
        ivNext = container.findViewById(R.id.ivNext);
        rvCalendar = (RecyclerView) container.findViewById(R.id.rvCalendar);
        GridLayoutManager layoutManager = new GridLayoutManager(act, 7);
        adCalendar = new CalendarAdapter();
        rvCalendar.setLayoutManager(layoutManager);
        rvCalendar.setAdapter(adCalendar);

        rvCalendar.addOnItemTouchListener(
                new RecyclerItemClickListener(act, new RecyclerItemClickListener.OnItemClickListener() {
                    @Override
                    public void onItemClick(View view, final int pos) {
                        int k = adCalendar.getItem(pos).getCount();
                        if (k == 1) {
                            openLink(adCalendar.getItem(pos).getLink(0));
                        } else if (k > 1) {
                            PopupMenu popupMenu = new PopupMenu(act, rvCalendar.getChildAt(pos));
                            popupMenu.inflate(R.menu.menu_links);

                            String s;
                            for (int i = 0; i < 5; i++) {
                                if (i < k) {
                                    s = adCalendar.getItem(pos).getLink(i).substring(Const.LINK.length());
                                    if (!s.contains("/"))
                                        popupMenu.getMenu().getItem(i).setTitle(
                                                getResources().getString(R.string.prom_for_soul_unite));
                                    else {
                                        s = DataBase.getContentPage(act, s, true);
                                        if (s != null)
                                            popupMenu.getMenu().getItem(i).setTitle(
                                                    s.substring(s.indexOf(" ") + 1));
                                    }
                                } else {
                                    popupMenu.getMenu().getItem(i).setVisible(false);
                                }
                            }
                            popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                                @Override
                                public boolean onMenuItemClick(MenuItem item) {
                                    int index;
                                    switch (item.getItemId()) {
                                        case R.id.link1:
                                            index = 0;
                                            break;
                                        case R.id.link2:
                                            index = 1;
                                            break;
                                        case R.id.link3:
                                            index = 2;
                                            break;
                                        case R.id.link4:
                                            index = 3;
                                            break;
                                        default:
                                            index = 4;
                                            break;
                                    }
                                    openLink(adCalendar.getItem(pos).getLink(index));
                                    return true;
                                }
                            });
                            popupMenu.show();
                        }
                    }
                })
        );

        ivPrev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openMonth(-1);
            }
        });
        ivNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openMonth(1);
            }
        });
        tvDate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showDatePicker();
            }
        });
    }

    private void openLink(String link) {
        BrowserActivity.openReader(act, link, null);
        adNoread.clear();
    }

    private void openMonth(int v) {
        if (task == null) {
            tvDate.setBackgroundDrawable(getResources().getDrawable(R.drawable.selected));
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    hTimer.sendEmptyMessage(1);
                }
            }, 300);
            createCalendar(v);
        }
    }

    private void createCalendar(int v) {
        Date d = (Date) dCurrent.clone();
        if (v != 0)
            d.setMonth(d.getMonth() + v);
        tvDate.setText(getResources().getStringArray(R.array.months)[d.getMonth()]
                + "\n" + (d.getYear() + 1900));
        adCalendar.clear();
        for (int i = -1; i > -7; i--) //monday-saturday
            adCalendar.addItem(new CalendarItem(act, i, R.color.light_gray));
        adCalendar.addItem(new CalendarItem(act, 0, R.color.light_gray)); //sunday
        int n;
        final int m = d.getMonth();
        d.setDate(1);
        dCurrent = (Date) d.clone();
        if (d.getDay() != 1) {
            if (d.getDay() == 0) //sunday
                d.setDate(-5);
            else
                d.setDate(2 - d.getDay());
            n = d.getDate();
            while (d.getDate() > 1) {
                adCalendar.addItem(new CalendarItem(act, d.getDate(), android.R.color.darker_gray));
                n++;
                d.setDate(n);
            }
        }
        n = 1;
        while (d.getMonth() == m) {
            adCalendar.addItem(new CalendarItem(act, d.getDate(), android.R.color.white));
            if (d.getDay() == 3) // wednesday
                adCalendar.getItem(adCalendar.getItemCount() - 1).setProm();
            n++;
            d.setDate(n);
        }
        n = 1;
        while (d.getDay() != 1) {
            adCalendar.addItem(new CalendarItem(act, d.getDate(), android.R.color.darker_gray));
            n++;
            d.setDate(n);
        }
        openCalendar(true);
        adCalendar.notifyDataSetChanged();
        if (dCurrent.getYear() == 116)
            ivPrev.setEnabled(dCurrent.getMonth() != 0);
        if (dCurrent.getYear() == today_y)
            ivNext.setEnabled(dCurrent.getMonth() != today_m);
        else
            ivNext.setEnabled(true);
    }

    public boolean isCurMonth() {
        return dCurrent.getMonth() == today_m && dCurrent.getYear() == today_y;
    }

    private void openCalendar(boolean boolLoad) {
        try {
            if (task != null)
                return;
            File file = new File(act.getFilesDir() + FOLDER +
                    dCurrent.getMonth() + "." + dCurrent.getYear());
            if (!file.exists()) {
                if (boolLoad)
                    startLoad();
            } else {
                if (boolLoad) {
                    if (isCurMonth()) {
                        act.status.checkTime(file.lastModified());
                    }
                    if ((dCurrent.getMonth() == today_m - 1 && dCurrent.getYear() == today_y) ||
                            (dCurrent.getMonth() == 11 && dCurrent.getYear() == today_y - 1)) {
                        Date d = new Date(file.lastModified());
                        if (d.getMonth() != today_m)
                            act.status.checkTime(file.lastModified());
                    }
                }
                int i;
                String s;
                BufferedReader br = new BufferedReader(new FileReader(file));
                while ((s = br.readLine()) != null) {
                    i = adCalendar.indexOf(Integer.parseInt(s));
                    s = br.readLine();
                    while (s != null && !s.equals("")) {
                        if (i > -1)
                            adCalendar.getItem(i).addLink(s);
                        s = br.readLine();
                    }
                }
                br.close();
            }
            adCalendar.notifyDataSetChanged();
        } catch (Exception e) {
            e.printStackTrace();
            if (boolLoad)
                startLoad();
        }
    }

    private void startLoad() {
        setStatus(true);
        task = new CalendarTask(this);
        if (isCurMonth()) adNoread.clear();
        int n = (isCurMonth() ? 1 : 0);
        task.execute(dCurrent.getYear(), dCurrent.getMonth(), n);
    }

    public void finishLoad(boolean suc) {
        task = null;
        if (suc)
            setStatus(false);
        else
            act.status.setCrash(true);
        if (adNoread.getCount() == 0)
            createNoreadList(false);
        clearDays();
        openCalendar(false);
    }

    private void createNoreadList(boolean boolLoad) {
        try {
            String t, s;
            int n;
            File file = new File(act.getFilesDir() + File.separator + Const.NOREAD);
            boolean bNewNoread = false;
            if (file.exists()) {
                bNewNoread = (System.currentTimeMillis() - file.lastModified() < 10000);
                BufferedReader br = new BufferedReader(new FileReader(file));
                while ((s = br.readLine()) != null) {
                    t = s.substring(s.lastIndexOf(File.separator) + 1);
                    if (t.contains("_")) {
                        n = t.indexOf("_");
                        t = t.substring(0, n) + " (" + t.substring(n + 1) + ")";
                    }
                    if (s.contains(Const.POEMS))
                        t = getResources().getString(R.string.katren) + " " +
                                getResources().getString(R.string.from) + " " + t;
                    adNoread.insertItem(0, new ListItem(t, s + Const.HTML));
                }
                br.close();
            }
            boolean bNewAds = false;
            file = new File(act.getFilesDir() + File.separator + ADS);
            if (file.exists()) {
                bNewAds = (System.currentTimeMillis() - file.lastModified() < 10000);
                BufferedReader br = new BufferedReader(new FileReader(file));
                br.readLine(); //time
                final String end = "<e>";
                while ((s = br.readLine()) != null) {
                    if (s.contains("<t>")) {
                        adNoread.insertItem(0, new ListItem(
                                getResources().getString(R.string.ad)
                                        + ": " + s.substring(3)));
                    } else if (s.contains("<u>")) {
                        n = Integer.parseInt(s.substring(3));
                        if (n > act.getPackageManager().getPackageInfo(act.getPackageName(), 0).versionCode) {
                            adNoread.insertItem(0, new ListItem(
                                    getResources().getString(R.string.ad) + ": " +
                                            getResources().getString(R.string.access_new_version)));
                            adNoread.getItem(0).addLink(
                                    getResources().getString(R.string.url_on_app));
                        } else {
                            while (!s.equals(end))
                                s = br.readLine();
                        }
                    } else if (s.contains("<d>")) {
                        t = s.substring(3);
                        s = br.readLine();
                        while (!s.equals(end)) {
                            t += Const.N + s;
                            s = br.readLine();
                        }
                        adNoread.getItem(0).addHead(t);
                    } else if (s.contains("<l>")) {
                        adNoread.getItem(0).addLink(s.substring(3));
                    }
                }
                br.close();
            }
            adNoread.notifyDataSetChanged();
            if (tvNew.getText().toString().contains("."))
                n = adNoread.getCount();
            else
                n = Integer.parseInt(tvNew.getText().toString());
            tvNew.setText(Integer.toString(adNoread.getCount()));
            if (adNoread.getCount() > n || bNewAds || bNewNoread) {
                tvNew.clearAnimation();
                tvNew.startAnimation(AnimationUtils.loadAnimation(act, R.anim.blink));
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (boolLoad && isCurMonth())
                startLoad();
        }
    }

    private void setStatus(boolean boolStart) {
        act.status.setCrash(false);
        act.status.setLoad(boolStart);
        if (boolStart) {
            tvNew.setVisibility(View.GONE);
            fabRefresh.setVisibility(View.GONE);
        } else {
            tvNew.setVisibility(View.VISIBLE);
            fabRefresh.setVisibility(View.VISIBLE);
        }
    }

    private void showDatePicker() {
        dialog = -1;
        dateDialog = new DateDialog(act, dCurrent);
        dateDialog.setResult(CalendarFragment.this);
        dateDialog.show();
    }

    @Override
    public void putDate(Date date) {
        dialog = -2;
        if (date == null) // cancel
            return;
        dCurrent = date;
        createCalendar(0);
    }
}
