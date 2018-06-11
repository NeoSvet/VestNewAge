package ru.neosvet.vestnewage.fragment;

import android.app.Fragment;
import android.content.DialogInterface;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
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
import android.widget.ListView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import ru.neosvet.ui.CalendarAdapter;
import ru.neosvet.ui.CalendarItem;
import ru.neosvet.ui.CustomDialog;
import ru.neosvet.ui.DateDialog;
import ru.neosvet.ui.ListAdapter;
import ru.neosvet.ui.ListItem;
import ru.neosvet.ui.RecyclerItemClickListener;
import ru.neosvet.ui.ResizeAnim;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.MultiWindowSupport;
import ru.neosvet.utils.Unread;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.BrowserActivity;
import ru.neosvet.vestnewage.activity.MainActivity;
import ru.neosvet.vestnewage.task.CalendarTask;

public class CalendarFragment extends Fragment implements DateDialog.Result {
    public static final String CURRENT_DATE = "current_date", ADS = "ads";
    private int today_m, today_y, iNew = -1;
    private CalendarAdapter adCalendar;
    private RecyclerView rvCalendar;
    private Date dCurrent;
    private TextView tvDate, tvNew;
    private ListView lvUnread;
    private View container, tvEmpty, pCalendar, ivPrev, ivNext, fabRefresh, fabClose, fabClear;
    private CalendarTask task = null;
    private ListAdapter adUnread;
    private MainActivity act;
    private DateDialog dateDialog;
    private CustomDialog alert;
    private Animation anShow, anHide;
    private int dialog = -2;
    private boolean show = false;
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (act.isInMultiWindowMode())
                MultiWindowSupport.resizeFloatTextView(tvNew, true);
        }
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
        outState.putBoolean(Unread.NAME, (lvUnread.getVisibility() == View.VISIBLE));
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
            if (state.getBoolean(Unread.NAME, false)) {
                pCalendar.setVisibility(View.GONE);
                tvNew.setVisibility(View.GONE);
                fabRefresh.setVisibility(View.GONE);
                fabClear.setVisibility(View.VISIBLE);
                fabClose.setVisibility(View.VISIBLE);
                lvUnread.getLayoutParams().height = (int) (getResources().getInteger(R.integer.height_list)
                        * getResources().getDisplayMetrics().density);
                lvUnread.requestLayout();
                lvUnread.setVisibility(View.VISIBLE);
            }
            dialog = state.getInt(Const.DIALOG);
            if (dialog == -1)
                showDatePicker();
            else if (dialog > -1) {
                openUnreadList(false);
                showAd(adUnread.getItem(dialog).getLink(),
                        adUnread.getItem(dialog).getHead(0));
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
                    if (show)
                        return;
                    show = true;
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
                    adUnread.clear();
                    adUnread.addItem(new ListItem(getResources().getString(R.string.no_list), ""));
                }
                pCalendar.setVisibility(View.GONE);
                tvNew.setVisibility(View.GONE);
                fabRefresh.setVisibility(View.GONE);
                fabClear.setVisibility(View.VISIBLE);
                fabClose.setVisibility(View.VISIBLE);
                lvUnread.setVisibility(View.VISIBLE);
                ResizeAnim anim = new ResizeAnim(lvUnread, false,
                        (int) (getResources().getInteger(R.integer.height_list)
                                * getResources().getDisplayMetrics().density));
                anim.setDuration(800);
                lvUnread.clearAnimation();
                lvUnread.startAnimation(anim);
            }
        });
        lvUnread.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int pos, long l) {
                if (adUnread.getItem(pos).getTitle().contains(getResources().getString(R.string.ad))) {
                    String link = adUnread.getItem(pos).getLink();
                    String des = adUnread.getItem(pos).getHead(0);
                    if (des.equals("")) // only link
                        act.lib.openInApps(link, null);
                    else {
                        dialog = pos;
                        showAd(link, des);
                    }
                } else if (!adUnread.getItem(pos).getLink().equals("")) {
                    openLink(adUnread.getItem(pos).getLink());
                }
            }
        });
        fabClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                closeUnread();
            }
        });
        fabClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                closeUnread();
                tvNew.setText("0");
                try {
                    Unread unread = new Unread(act);
                    unread.clearList();
                    unread.setBadge();
                    unread.close();
                    File file = new File(act.getFilesDir() + File.separator + ADS);
                    if (file.exists()) {
                        BufferedReader br = new BufferedReader(new FileReader(file));
                        String t = br.readLine(); // читаем время последней загрузки объявлений
                        br.close();
                        BufferedWriter bw = new BufferedWriter(new FileWriter(file));
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
                if (!act.status.isStop()) {
                    if (task != null)
                        task.cancel(false);
                    else
                        act.status.setLoad(false);
                } else if (act.status.onClick()) {
                    tvNew.setVisibility(View.VISIBLE);
                    fabRefresh.setVisibility(View.VISIBLE);
                } else if (act.status.isTime())
                    startLoad();
            }
        });
    }

    private void showAd(final String link, String des) {
        alert = new CustomDialog(act);
        alert.setTitle(getResources().getString(R.string.ad));
        alert.setMessage(des);

        if (link.equals("")) { // only des
            alert.setRightButton(getResources().getString(android.R.string.ok), new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    alert.dismiss();
                }
            });
        } else {
            alert.setRightButton(getResources().getString(R.string.open_link), new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    act.lib.openInApps(link, null);
                    alert.dismiss();
                }
            });
        }

        alert.show(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                dialog = -2;
            }
        });
    }

    private void closeUnread() {
        ResizeAnim anim = new ResizeAnim(lvUnread, false, 10);
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
                lvUnread.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        lvUnread.clearAnimation();
        lvUnread.startAnimation(anim);
    }

    public void clearDays() {
        for (int i = 0; i < adCalendar.getItemCount(); i++) {
            adCalendar.getItem(i).clear(false);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adUnread.getCount() == 0) {
            openUnreadList(true);
            if (adUnread.getCount() == 0 && lvUnread.getVisibility() == View.VISIBLE) {
                closeUnread();
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
        lvUnread = (ListView) container.findViewById(R.id.lvUnread);
        adUnread = new ListAdapter(act);
        lvUnread.setAdapter(adUnread);
        anShow = AnimationUtils.loadAnimation(act, R.anim.show);
        anHide = AnimationUtils.loadAnimation(act, R.anim.hide);
        anHide.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                tvEmpty.setVisibility(View.GONE);
                show = false;
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
    }


    public boolean onBackPressed() {
        if (task != null) {
            task.cancel(false);
            return false;
        }
        if (lvUnread.getVisibility() == View.VISIBLE) {
            closeUnread();
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
                            PopupMenu pMenu = new PopupMenu(act, rvCalendar.getChildAt(pos));
                            for (int i = 0; i < adCalendar.getItem(pos).getCount(); i++) {
                                pMenu.getMenu().add(adCalendar.getItem(pos).getTitle(i));
                            }
                            pMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                                @Override
                                public boolean onMenuItemClick(MenuItem item) {
                                    String title = item.getTitle().toString();
                                    for (int i = 0; i < adCalendar.getItem(pos).getCount(); i++) {
                                        if (adCalendar.getItem(pos).getTitle(i).equals(title)) {
                                            openLink(adCalendar.getItem(pos).getLink(i));
                                            break;
                                        }
                                    }
                                    return true;
                                }
                            });
                            pMenu.show();
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
        if (task != null)
            task.cancel(false);
        BrowserActivity.openReader(act, link, null);
        adUnread.clear();
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

    private void createCalendar(int offsetMonth) {
        Date d = (Date) dCurrent.clone();
        if (offsetMonth != 0)
            d.setMonth(d.getMonth() + offsetMonth);
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
        Date today = new Date();
        int n_today = 0;
        if (today.getMonth() == m)
            n_today = today.getDate();
        while (d.getMonth() == m) {
            adCalendar.addItem(new CalendarItem(act, d.getDate(), android.R.color.white));
            if (d.getDate() == n_today)
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

    private void openCalendar(boolean loadIfNeed) {
        try {
            if (task != null)
                if (task.isLoadList())
                    return;

            DateFormat df = new SimpleDateFormat("MM.yy");
            DataBase dataBase = new DataBase(act, df.format(dCurrent));
            SQLiteDatabase db = dataBase.getWritableDatabase();
            Cursor cursor = db.query(DataBase.TITLE, null, null, null, null, null, null);
            boolean empty = true;
            if (cursor.moveToFirst()) {
                if (loadIfNeed) {
                    checkTime(cursor.getLong(cursor.getColumnIndex(DataBase.TIME)));
                }

                int iTitle = cursor.getColumnIndex(DataBase.TITLE);
                int iLink = cursor.getColumnIndex(DataBase.LINK);
                int i;
                String title, link;
                while (cursor.moveToNext()) {
                    title = cursor.getString(iTitle);
                    link = cursor.getString(iLink);
                    i = link.lastIndexOf("/") + 1;
                    i = Integer.parseInt(link.substring(i, i + 2));
                    i = adCalendar.indexOf(i);
                    adCalendar.getItem(i).addLink(link);
                    if (title.contains("/"))
                        adCalendar.getItem(i).addTitle(title);
                    else {
                        title = dataBase.getPageTitle(title, link);
                        adCalendar.getItem(i).addTitle(title.substring(title.indexOf(" ") + 1));
                    }
                    empty = false;
                }
            }
            cursor.close();
            dataBase.close();
            adCalendar.notifyDataSetChanged();

            if (empty && loadIfNeed)
                startLoad();
        } catch (Exception e) {
            e.printStackTrace();
            if (loadIfNeed)
                startLoad();
        }
    }

    private void checkTime(long time) {
        if (isCurMonth()) {
            act.status.checkTime(time);
            return;
        }
        if ((dCurrent.getMonth() == today_m - 1 && dCurrent.getYear() == today_y) ||
                (dCurrent.getMonth() == 11 && dCurrent.getYear() == today_y - 1)) {
            Date d = new Date(time);
            if (d.getMonth() != today_m)
                act.status.checkTime(time);
        }
    }

    private void startLoad() {
        setStatus(true);
        task = new CalendarTask(this);
        if (isCurMonth()) adUnread.clear();
        int n = (isCurMonth() ? 1 : 0);
        task.execute(dCurrent.getYear(), dCurrent.getMonth(), n);
    }

    public void updateCalendar() {
        clearDays();
        openCalendar(false);
    }

    public void finishLoad(boolean suc) {
        task = null;
        if (suc)
            setStatus(false);
        else
            act.status.setCrash(true);
        if (adUnread.getCount() == 0)
            openUnreadList(false);
    }

    private void openUnreadList(boolean loadIfNeed) {
        try {
            String t, s;
            int n;
            boolean isNew = false;
            Unread unread = new Unread(act);
            unread.setBadge();
            long time = unread.lastModified();
            if (time > 0) {
                isNew = (System.currentTimeMillis() - time < 10000);
                List<String> links = unread.getList();
                unread.close();
                for (int i = 0; i < links.size(); i++) {
                    s = links.get(i);
                    t = s.substring(s.lastIndexOf(File.separator) + 1);
                    if (t.contains("_")) {
                        n = t.indexOf("_");
                        t = t.substring(0, n) + " (" + t.substring(n + 1) + ")";
                    }
                    if (s.contains(Const.POEMS))
                        t = getResources().getString(R.string.katren) + " " +
                                getResources().getString(R.string.from) + " " + t;
                    adUnread.addItem(new ListItem(t, s + Const.HTML));
                }
                links.clear();
            }
            File file = new File(act.getFilesDir() + File.separator + ADS);
            if (file.exists()) {
                isNew = isNew || (System.currentTimeMillis() - file.lastModified() < 10000);
                BufferedReader br = new BufferedReader(new FileReader(file));
                br.readLine(); //time
                final String end = "<e>";
                while ((s = br.readLine()) != null) {
                    if (s.contains("<t>")) {
                        adUnread.insertItem(0, new ListItem(
                                getResources().getString(R.string.ad)
                                        + ": " + s.substring(3)));
                    } else if (s.contains("<u>")) {
                        n = Integer.parseInt(s.substring(3));
                        if (n > act.getPackageManager().getPackageInfo(act.getPackageName(), 0).versionCode) {
                            adUnread.insertItem(0, new ListItem(
                                    getResources().getString(R.string.ad) + ": " +
                                            getResources().getString(R.string.access_new_version)));
                            adUnread.getItem(0).addLink(
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
                        adUnread.getItem(0).addHead(t);
                    } else if (s.contains("<l>")) {
                        adUnread.getItem(0).addLink(s.substring(3));
                    }
                }
                br.close();
            }
            adUnread.notifyDataSetChanged();

            if (adUnread.getCount() == 0)
                isNew = false;
            else {
                if (!tvNew.getText().toString().contains("."))
                    isNew = adUnread.getCount() > Integer.parseInt(tvNew.getText().toString());
                if (!isNew) {
                    file = new File(act.getFilesDir() + SummaryFragment.RSS);
                    if (Math.abs(time - file.lastModified()) < 2000) {
                        isNew = true;
                        file.setLastModified(time - 2500);
                    }
                }
            }
            tvNew.setText(Integer.toString(adUnread.getCount()));
            if (isNew) {
                tvNew.clearAnimation();
                tvNew.startAnimation(AnimationUtils.loadAnimation(act, R.anim.blink));
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (loadIfNeed && isCurMonth())
                startLoad();
        }
    }

    private void setStatus(boolean load) {
        act.status.setCrash(false);
        act.status.setLoad(load);
        if (load) {
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
    public void putDate(@Nullable Date date) {
        dialog = -2;
        if (date == null) // cancel
            return;
        dCurrent = date;
        createCalendar(0);
    }

    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode) {
        super.onMultiWindowModeChanged(isInMultiWindowMode);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            MultiWindowSupport.resizeFloatTextView(tvNew, isInMultiWindowMode);
    }

    public void blinkDay(int d) {
        boolean begin = false;
        for (int i = 6; i < adCalendar.getItemCount(); i++) {
            if (adCalendar.getItem(i).getNum() == 1)
                begin = true;
            if (begin) {
                if (adCalendar.getItem(i).getNum() == d) {
                    updateTitles(i);
                    View v = rvCalendar.getLayoutManager().findViewByPosition(i);
                    if (v != null) {
                        v.clearAnimation();
                        v.startAnimation(AnimationUtils.loadAnimation(act, R.anim.blink));
                    }
                    break;
                }
            }
        }
    }

    private void updateTitles(int item) {
        adCalendar.getItem(item).clear(true);
        int i = 0;
        if (adCalendar.getItem(item).isBold())
            i = 1;
        DataBase dataBase = new DataBase(act, adCalendar.getItem(item).getLink(i));
        SQLiteDatabase db = dataBase.getWritableDatabase();
        Cursor cursor;
        String title, link;
        for (; i < adCalendar.getItem(item).getCount(); i++) {
            link = adCalendar.getItem(item).getLink(i);
            cursor = db.query(DataBase.TITLE, new String[]{DataBase.TITLE},
                    DataBase.LINK + DataBase.Q, new String[]{link},
                    null, null, null);
            if (cursor.moveToFirst()) {
                title = cursor.getString(0);
                if (title.contains("/"))
                    adCalendar.getItem(item).addTitle(title);
                else {
                    title = dataBase.getPageTitle(title, link);
                    adCalendar.getItem(item).addTitle(title.substring(title.indexOf(" ") + 1));
                }
            }
            cursor.close();
        }
        dataBase.close();
    }

    public int getCurrentYear() {
        return dCurrent.getYear();
    }
}
