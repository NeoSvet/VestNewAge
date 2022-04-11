package ru.neosvet.vestnewage.fragment;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.work.Data;

import java.io.File;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import ru.neosvet.ui.NeoFragment;
import ru.neosvet.ui.Tip;
import ru.neosvet.ui.dialogs.CustomDialog;
import ru.neosvet.ui.dialogs.DateDialog;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.BrowserActivity;
import ru.neosvet.vestnewage.activity.MarkerActivity;
import ru.neosvet.vestnewage.helpers.BookHelper;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.helpers.LoaderHelper;
import ru.neosvet.vestnewage.helpers.ProgressHelper;
import ru.neosvet.vestnewage.list.ListAdapter;
import ru.neosvet.vestnewage.list.ListItem;
import ru.neosvet.vestnewage.model.BookModel;
import ru.neosvet.vestnewage.storage.JournalStorage;
import ru.neosvet.vestnewage.storage.PageStorage;

public class BookFragment extends NeoFragment implements DateDialog.Result, View.OnClickListener {
    private final String DIALOG_DATE = "date";
    private Animation anMin, anMax;
    private ListAdapter adBook;
    private View fabRefresh, fabRndMenu, ivPrev, ivNext;
    private TextView tvDate;
    private DateDialog dateDialog;
    private CustomDialog alertRnd;
    private TabHost tabHost;
    private ListView lvBook;
    private int x, y, year = 0, tab = 0;
    private Tip menuRnd;
    private BookModel model;
    private String dialog = "";
    private boolean notClick = false;
    private DateHelper dKatren, dPoslanie;
    private final BookHelper helper = new BookHelper();
    final Handler hTimer = new Handler(message -> {
        tvDate.setBackgroundResource(R.drawable.card_bg);
        return false;
    });

    public static BookFragment newInstance(int tab) {
        BookFragment fragment = new BookFragment();
        Bundle args = new Bundle();
        args.putInt(Const.TAB, tab);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.book_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getArguments() != null)
            tab = getArguments().getInt(Const.TAB);
        initViews(view);
        setViews();
        initTabs();
        model = new ViewModelProvider(this).get(BookModel.class);
        restoreState(savedInstanceState);
        if (year > 0) {
            DateHelper d = DateHelper.initToday();
            d.setYear(year);
            year = 0;
            dialog = DIALOG_DATE + d;
            showDatePicker(d);
        }
        if (ProgressHelper.isBusy())
            setStatus(true);
    }

    @Override
    public void onPause() {
        super.onPause();
        helper.saveDates(dKatren.getTimeInDays(), dPoslanie.getTimeInDays());
    }

    @Override
    public void onChanged(Data data) {
        if (data.getBoolean(Const.OTKR, false)) {
            if (tab == 1 && dPoslanie.getYear() == 2016 && dPoslanie.getMonth() == 1)
                ivPrev.setEnabled(true);
            return;
        }
        if (data.getBoolean(Const.START, false)) {
            act.status.loadText();
            return;
        }
        if (data.getBoolean(Const.DIALOG, false)) {
            act.status.setProgress(data.getInt(Const.PROG, 0));
            return;
        }
        if (!data.getBoolean(Const.FINISH, false))
            return;
        ProgressHelper.setBusy(false);
        setStatus(false);
        String error = data.getString(Const.ERROR);
        if (error != null) {
            act.status.setError(error);
            return;
        }
        String name = data.getString(Const.TITLE);
        finishLoad(name);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString(Const.DIALOG, dialog);
        if (dialog.contains(DIALOG_DATE))
            dateDialog.dismiss();
        else if (dialog.length() > 1)
            alertRnd.dismiss();
        outState.putInt(Const.TAB, tab);
        super.onSaveInstanceState(outState);
    }

    private void restoreState(Bundle state) {
        helper.loadDates();
        dKatren = DateHelper.putDays(helper.getKatrenDays());
        dPoslanie = DateHelper.putDays(helper.getPoslaniyaDays());
        if (!helper.isLoadedOtkr() && dPoslanie.getYear() < 2016)
            dPoslanie = DateHelper.putYearMonth(2016, 1);
        if (state != null) {
            tab = state.getInt(Const.TAB);
            if (!ProgressHelper.isBusy()) {
                dialog = state.getString(Const.DIALOG);
                if (dialog.contains(DIALOG_DATE)) {
                    if (!dialog.equals(DIALOG_DATE)) {
                        DateHelper d = DateHelper.parse(dialog.substring(DIALOG_DATE.length()));
                        showDatePicker(d);
                    } else
                        showDatePicker(null);
                } else if (dialog.length() > 1) {
                    String[] m = dialog.split(Const.AND);
                    showRndAlert(m[0], m[1], m[2], m[3], Integer.parseInt(m[4]));
                }
            }
        }
        tabHost.setCurrentTab(tab);
    }

    private void initTabs() {
        tabHost.setup();
        TabHost.TabSpec tabSpec;

        tabSpec = tabHost.newTabSpec(Const.KATRENY);
        tabSpec.setIndicator(getString(R.string.katreny),
                ContextCompat.getDrawable(act, R.drawable.none));
        tabSpec.setContent(R.id.pBook);
        tabHost.addTab(tabSpec);

        tabSpec = tabHost.newTabSpec(Const.POSLANIYA);
        tabSpec.setIndicator(getString(R.string.poslaniya),
                ContextCompat.getDrawable(act, R.drawable.none));
        tabSpec.setContent(R.id.pBook);
        tabHost.addTab(tabSpec);

        TabWidget widget = tabHost.getTabWidget();
        for (int i = 0; i < widget.getChildCount(); i++) {
            View v = widget.getChildAt(i);
            TextView tv = v.findViewById(android.R.id.title);
            if (tv != null) {
                tv.setMaxLines(1);
                v.setBackgroundResource(R.drawable.table_selector);
            }
        }
        tabHost.setCurrentTab(1);
        tabHost.setOnTabChangedListener(name -> {
            if (ProgressHelper.isBusy()) return;
            if (name.equals(Const.KATRENY))
                act.setTitle(getString(R.string.katreny));
            else
                act.setTitle(getString(R.string.poslaniya));
            tab = tabHost.getCurrentTab();
            openList(true);
        });
    }

    @SuppressLint("Range")
    private void openList(boolean loadIfNeed) {
        try {
            DateHelper d;
            boolean katren;
            if (tab == 0) {
                d = dKatren;
                katren = true;
            } else {
                d = dPoslanie;
                katren = false;
            }
            if (!existsList(d, katren)) {
                if (loadIfNeed)
                    startLoad();
                return;
            }
            adBook.clear();
            tvDate.setText(d.getCalendarString());
            if (d.getMonth() == 1 && d.getYear() == 2016 && !helper.isLoadedOtkr()) {
                // доступна для того, чтобы предложить скачать Послания за 2004-2015
                ivPrev.setEnabled(!LoaderHelper.start);
                d.changeMonth(1);
            } else {
                d.changeMonth(-1);
                ivPrev.setEnabled(existsList(d, katren));
                d.changeMonth(2);
            }
            ivNext.setEnabled(existsList(d, katren));
            d.changeMonth(-1);
            PageStorage storage = new PageStorage();
            String t, s;
            Cursor cursor;

            if (d.getMonth() == 1 && d.getYear() == 2016 && !helper.isLoadedOtkr()) {
                //добавить в список "Предисловие к Толкованиям" /2004/predislovie.html
                storage.open("12.04");
                cursor = storage.getListAll();
                if (cursor.moveToFirst() && cursor.moveToNext()) {
                    t = cursor.getString(cursor.getColumnIndex(Const.TITLE));
                    s = cursor.getString(cursor.getColumnIndex(Const.LINK));
                    adBook.addItem(new ListItem(t, s));
                }
                cursor.close();
                storage.close();
            }

            storage.open(d.getMY());
            cursor = storage.getListAll();

            DateHelper dModList;
            if (cursor.moveToFirst()) {
                dModList = DateHelper.putMills(cursor.getLong(cursor.getColumnIndex(Const.TIME)));
                if (d.getYear() > 2015) { //списки скаченные с сайта Откровений не надо открывать с фильтром - там и так всё по порядку
                    cursor.close();
                    cursor = storage.getList(katren);
                    cursor.moveToFirst();
                } else // в случае списков с сайта Откровений надо просто перейти к следующей записи
                    cursor.moveToNext();
                int iTitle = cursor.getColumnIndex(Const.TITLE);
                int iLink = cursor.getColumnIndex(Const.LINK);
                do {
                    s = cursor.getString(iLink);
                    if (s.contains("2004") || s.contains("pred"))
                        t = cursor.getString(iTitle);
                    else {
                        t = s.substring(s.lastIndexOf("/") + 1, s.lastIndexOf("."));
                        if (t.contains("_")) t = t.substring(0, t.indexOf("_"));
                        if (t.contains("#")) t = t.substring(0, t.indexOf("#"));
                        t = cursor.getString(iTitle) +
                                " (" + getString(R.string.from)
                                + " " + t + ")";
                    }
                    adBook.addItem(new ListItem(t, s));
                } while (cursor.moveToNext());
            } else
                dModList = d;
            cursor.close();
            storage.close();
            DateHelper today = DateHelper.initToday();
            if (d.getMonth() == today.getMonth() && d.getYear() == today.getYear()) {
                //если выбранный месяц - текущий
                katren = act.status.checkTime(dModList.getTimeInSeconds());
            } else {
                //если выбранный месяц - предыдущий, то проверяем когда список был обновлен
                if ((d.getMonth() == today.getMonth() - 1 && d.getYear() == today.getYear()) ||
                        (d.getMonth() == 11 && d.getYear() == today.getYear() - 1)) {
                    if (dModList.getMonth() != today.getMonth())
                        act.status.checkTime(dModList.getTimeInSeconds());
                    else
                        katren = act.status.checkTime(DateHelper.initNow().getTimeInSeconds()); //hide "ref?"
                } else
                    katren = act.status.checkTime(DateHelper.initNow().getTimeInSeconds()); //hide "ref?"
            }
            if (katren)
                fabRefresh.setVisibility(View.GONE);
            else
                fabRefresh.setVisibility(View.VISIBLE);
            adBook.notifyDataSetChanged();
            lvBook.smoothScrollToPosition(0);
        } catch (Exception e) {
            e.printStackTrace();
            if (loadIfNeed)
                startLoad();
        }
    }

    private boolean existsList(DateHelper d, boolean katren) {
        PageStorage storage = new PageStorage();
        storage.open(d.getMY());
        Cursor cursor = storage.getLinks();
        String s;
        if (cursor.moveToFirst()) {
            // первую запись пропускаем, т.к. там дата изменения списка
            while (cursor.moveToNext()) {
                s = cursor.getString(0);
                if ((s.contains(Const.POEMS) && katren) ||
                        (!s.contains(Const.POEMS) && !katren)) {
                    cursor.close();
                    storage.close();
                    return true;
                }
            }
        }
        cursor.close();
        storage.close();
        return false;
    }

    private void initViews(View container) {
        menuRnd = new Tip(act, container.findViewById(R.id.pRnd));
        lvBook = container.findViewById(R.id.lvBook);
        fabRefresh = container.findViewById(R.id.fabRefresh);
        fabRndMenu = container.findViewById(R.id.fabRndMenu);
        tvDate = container.findViewById(R.id.tvDate);
        ivPrev = container.findViewById(R.id.ivPrev);
        ivNext = container.findViewById(R.id.ivNext);
        tabHost = container.findViewById(R.id.thBook);
        container.findViewById(R.id.bRndStih).setOnClickListener(this);
        container.findViewById(R.id.bRndPos).setOnClickListener(this);
        container.findViewById(R.id.bRndKat).setOnClickListener(this);
        anMin = AnimationUtils.loadAnimation(act, R.anim.minimize);
        anMin.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                fabRefresh.setVisibility(View.GONE);
                fabRndMenu.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        anMax = AnimationUtils.loadAnimation(act, R.anim.maximize);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setViews() {
        fabRefresh.setOnClickListener(view -> startLoad());
        adBook = new ListAdapter(requireContext());
        lvBook.setAdapter(adBook);
        lvBook.setOnItemClickListener((adapterView, view, pos, l) -> {
            if (notClick) return;
            if (act.checkBusy()) return;
            BrowserActivity.openReader(adBook.getItem(pos).getLink(), null);
        });
        lvBook.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (!act.status.startMin()) {
                        fabRefresh.startAnimation(anMin);
                        fabRndMenu.startAnimation(anMin);
                        menuRnd.hide();
                    }
                    x = (int) event.getX(0);
                    y = (int) event.getY(0);
                    break;
                case MotionEvent.ACTION_UP:
                    final int x2 = (int) event.getX(0), r = Math.abs(x - x2);
                    notClick = false;
                    if (r > (int) (30 * getResources().getDisplayMetrics().density))
                        if (r > Math.abs(y - (int) event.getY(0))) {
                            if (x > x2) { // next
                                if (ivNext.isEnabled())
                                    openMonth(true);
                                notClick = true;
                            } else if (x < x2) { // prev
                                if (ivPrev.isEnabled())
                                    openMonth(false);
                                notClick = true;
                            }
                        }
                case MotionEvent.ACTION_CANCEL:
                    if (!act.status.startMax()) {
                        fabRefresh.setVisibility(View.VISIBLE);
                        fabRefresh.startAnimation(anMax);
                        fabRndMenu.setVisibility(View.VISIBLE);
                        fabRndMenu.startAnimation(anMax);
                    }
                    break;
            }
            return false;
        });
        ivPrev.setOnClickListener(view -> openMonth(false));
        ivNext.setOnClickListener(view -> openMonth(true));
        act.status.setClick(view -> onStatusClick(false));
        tvDate.setOnClickListener(view -> {
            if (act.checkBusy()) return;
            dialog = DIALOG_DATE;
            showDatePicker(null);
        });
        fabRndMenu.setOnClickListener(view -> {
            if (menuRnd.isShow())
                menuRnd.hide();
            else
                menuRnd.show();
        });
    }

    @Override
    public void onStatusClick(boolean reset) {
        ProgressHelper.cancelled();
        fabRefresh.setVisibility(View.VISIBLE);
        fabRndMenu.setVisibility(View.VISIBLE);
        ProgressHelper.setBusy(false);
        openList(false);
        setStatus(false);
        if (!act.status.isStop()) {
            act.status.setLoad(false);
            return;
        }
        if (reset) {
            act.status.setError(null);
            return;
        }
        if (!act.status.onClick() && act.status.isTime())
            startLoad();
    }

    private void openMonth(boolean plus) {
        if (act.checkBusy()) return;
        if (!plus && tab == 1) {
            if (dPoslanie.getMonth() == 1 && dPoslanie.getYear() == 2016 && !helper.isLoadedOtkr()) {
                AlertDialog.Builder builder = new AlertDialog.Builder(act, R.style.NeoDialog);
                builder.setMessage(getString(R.string.alert_download_otkr));
                builder.setNegativeButton(getString(R.string.no),
                        (dialog, id) -> dialog.dismiss());
                builder.setPositiveButton(getString(R.string.yes),
                        (dialog, id) -> {
                            ivPrev.setEnabled(false);
                            LoaderHelper.postCommand(LoaderHelper.DOWNLOAD_OTKR, "");
                        });
                builder.create().show();
                return;
            }
        }
        DateHelper d;
        if (tab == 0)
            d = dKatren;
        else
            d = dPoslanie;
        if (plus)
            d.changeMonth(1);
        else
            d.changeMonth(-1);
        tvDate.setBackgroundResource(R.drawable.selected);
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                hTimer.sendEmptyMessage(1);
            }
        }, 300);
        openList(true);
    }

    @Override
    public void startLoad() {
        if (ProgressHelper.isBusy())
            return;
        setStatus(true);
        act.status.setLoad(true);
        act.status.startText();
        model.startLoad(helper.isLoadedOtkr(), tab == 0);
    }

    @Override
    public void setStatus(boolean load) {
        if (load) {
            fabRefresh.setVisibility(View.GONE);
            fabRndMenu.setVisibility(View.GONE);
        } else {
            fabRefresh.setVisibility(View.VISIBLE);
            fabRndMenu.setVisibility(View.VISIBLE);
        }
    }

    private void finishLoad(String result) {
        act.status.setLoad(false);
        if (tabHost.getCurrentTab() != tab)
            tabHost.setCurrentTab(tab);
        DateHelper d;
        if (tab == 0)
            d = dKatren;
        else
            d = dPoslanie;
        if (result == null)
            return;

        if (!existsList(d, tab == 0)) {
            d = DateHelper.putYearMonth(2000 + Integer.parseInt(result.substring(3, 5)),
                    Integer.parseInt(result.substring(0, 2)));
            if (tab == 0)
                dKatren = d;
            else
                dPoslanie = d;
        }
        if (existsList(d, tab == 0)) {
            openList(false);
        } else {
            DateHelper t = DateHelper.initToday();
            if (t.getMonth() == d.getMonth() && t.getYear() == d.getYear())
                Lib.showToast(getString(R.string.month_is_empty));
            else
                startLoad();
        }
    }

    public void setYear(int year) {
        this.year = year;
    }

    private void showDatePicker(DateHelper d) {
        if (d == null)
            if (tab == 0)
                d = dKatren;
            else
                d = dPoslanie;
        dateDialog = new DateDialog(act, d);
        dateDialog.setResult(BookFragment.this);
        if (tab == 0) { //katreny
            dateDialog.setMinMonth(2); //feb
        } else { //poslyania
            if (helper.isLoadedOtkr()) {
                dateDialog.setMinMonth(8); //aug
                dateDialog.setMinYear(2004);
            }
            dateDialog.setMaxMonth(9); //sep
            dateDialog.setMaxYear(2016);
        }
        dateDialog.show();
    }

    @Override
    public void putDate(@Nullable DateHelper date) {
        dialog = "";
        if (date == null) //cancel
            return;
        if (tab == 0)
            dKatren = date;
        else
            dPoslanie = date;
        openList(true);
    }

    @SuppressLint("Range")
    @Override
    public void onClick(View view) {
        menuRnd.hide();
        //Определяем диапозон дат:
        DateHelper d = DateHelper.initToday();
        int m, y, max_m = d.getMonth(), max_y = d.getYear() - 2000;
        if (view.getId() == R.id.bRndKat) {
            m = 2;
            y = 16;
        } else {
            if (helper.isLoadedOtkr()) {
                m = 8;
                y = 4;
            } else {
                m = 1;
                y = 16;
            }
            if (view.getId() == R.id.bRndPos) {
                max_m = 9;
                max_y = 16;
            }
        }
        int n = (max_y - y) * 12 + max_m - m;
        if (max_y > 16) { //проверка на существование текущего месяца
            File f = Lib.getFileDB(d.getMY());
            if (!f.exists()) n--;
        }
        //определяем случайный месяц:
        Random g = new Random();
        n = g.nextInt(n);
        while (n > 0) { //определяем случайную дату
            if (m == 12) {
                m = 1;
                y++;
            } else
                m++;
            n--;
        }
        //открываем базу по случайной дате:
        String name = (m < 10 ? "0" : "") + m + "." + (y < 10 ? "0" : "") + y;
        PageStorage storage = new PageStorage();
        storage.open(name);
        Cursor curTitle;
        String title = null;
        //определяем условие отбора в соотвтствии с выбранным пунктом:
        if (view.getId() == R.id.bRndKat) { //случайный катрен
            title = getString(R.string.rnd_kat);
            curTitle = storage.getList(true);
        } else if (view.getId() == R.id.bRndPos) { //случайное послание
            title = getString(R.string.rnd_pos);
            curTitle = storage.getList(false);
        } else { //случайных стих
            curTitle = storage.getListAll();
        }
        //определяем случайных текст:
        if (curTitle.getCount() < 2)
            n = 0;
        else {
            g = new Random();
            n = g.nextInt(curTitle.getCount() - 2) + 2; //0 - отсуствует, 1 - дата изменения списка
        }
        if (!curTitle.moveToPosition(n)) {
            curTitle.close();
            storage.close();
            Lib.showToast(getString(R.string.alert_rnd));
            return;
        }
        //если случайный текст найден
        String s = "";
        if (view.getId() == R.id.bRndStih) { //случайных стих
            Cursor curPar = storage.getParagraphs(curTitle);
            if (curPar.getCount() > 1) { //если текст скачен
                g = new Random();
                n = curPar.getCount(); //номер случайного стиха
                if (y > 13 || (y == 13 && m > 7))
                    n--; //исключаем подпись
                n = g.nextInt(n);
                if (curPar.moveToPosition(n)) { //если случайный стих найден
                    s = Lib.withOutTags(curPar.getString(0));
                } else {
                    s = "";
                    n = -1;
                }
            } else
                s = "";
            curPar.close();
            if (s.equals("")) {//случайный стих не найден
                Lib.showToast(getString(R.string.alert_rnd));
                title = getString(R.string.rnd_stih);
            }
        } else // случайный катрен или послание
            n = -1;
        //выводим на экран:
        String link = curTitle.getString(curTitle.getColumnIndex(Const.LINK));
        String msg;
        if (link == null)
            msg = getString(R.string.try_again);
        else
            msg = storage.getPageTitle(curTitle.getString(curTitle.getColumnIndex(Const.TITLE)), link);
        curTitle.close();
        if (title == null) {
            title = msg;
            msg = s;
        }
        dialog = title + Const.AND + link + Const.AND + msg + Const.AND + s + Const.AND + n;
        showRndAlert(title, link, msg, s, n);
        if (link == null) {
            storage.close();
            return;
        }
        //добавляем в журнал:
        ContentValues row = new ContentValues();
        row.put(Const.TIME, System.currentTimeMillis());
        JournalStorage dbJournal = new JournalStorage();
        row.put(DataBase.ID, PageStorage.getDatePage(link) + Const.AND + storage.getPageId(link) + Const.AND + n);
        storage.close();
        dbJournal.insert(row);
        dbJournal.close();
    }

    private void showRndAlert(String title, final String link, String msg, final String place, final int par) {
        alertRnd = new CustomDialog(act);
        alertRnd.setTitle(title);
        alertRnd.setMessage(msg);
        alertRnd.setLeftButton(getString(R.string.in_markers), view -> {
            Intent marker = new Intent(requireContext(), MarkerActivity.class);
            marker.putExtra(Const.LINK, link);
            marker.putExtra(DataBase.PARAGRAPH, par);
            startActivity(marker);
            alertRnd.dismiss();
        });
        alertRnd.setRightButton(getString(R.string.open), view -> {
            BrowserActivity.openReader(link, place);
            alertRnd.dismiss();
        });
        alertRnd.show(dialogInterface -> dialog = "");
    }
}
