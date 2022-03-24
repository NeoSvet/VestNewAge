package ru.neosvet.vestnewage.fragment;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.Data;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import ru.neosvet.ui.NeoFragment;
import ru.neosvet.ui.ResizeAnim;
import ru.neosvet.ui.SoftKeyboard;
import ru.neosvet.ui.dialogs.DateDialog;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.BrowserActivity;
import ru.neosvet.vestnewage.activity.MarkerActivity;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.helpers.ProgressHelper;
import ru.neosvet.vestnewage.list.ListAdapter;
import ru.neosvet.vestnewage.list.ListItem;
import ru.neosvet.vestnewage.list.PageAdapter;
import ru.neosvet.vestnewage.model.SearchModel;
import ru.neosvet.vestnewage.storage.SearchStorage;

public class SearchFragment extends NeoFragment implements DateDialog.Result, View.OnTouchListener, Observer<Data> {
    private final String SETTINGS = "s", ADDITION = "a", LABEL = "l", LAST_RESULTS = "r", CLEAR_RESULTS = "c";
    private View fabSettings, fabOk, pSettings, pPages, pStatus, bShow, pAdditionSet;
    private CheckBox cbSearchInResults;
    private PageAdapter adPages;
    private RecyclerView rvPages;
    private LinearLayout mainLayout;
    private ListView lvResult;
    private TextView tvStatus, tvLabel;
    private Button bStart, bEnd;
    private Spinner sMode;
    private AutoCompleteTextView etSearch;
    private ArrayAdapter<String> adSearch;
    private SearchModel model;
    private DateHelper dStart, dEnd;
    private ListAdapter adResults;
    private ResizeAnim anim;
    private int min_m = 1, min_y = 2016, dialog = -1, mode = -1, page = -1;
    private DateDialog dateDialog;
    private SoftKeyboard softKeyboard;
    private String string;
    private SharedPreferences pref;
    private SharedPreferences.Editor editor;
    private boolean scrollToFirst = false;

    public void setString(String s) {
        string = s;
    }

    public void setMode(int mode) {
        this.mode = mode;
    }

    public void setPage(int page) {
        this.page = page - 1;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.search_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        act.setTitle(getString(R.string.search));
        initViews(view);
        setViews(view);
        initModel();
        restoreState(savedInstanceState);
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
            ProgressHelper.addObserver(act, this);
    }

    private void initModel() {
        model = new ViewModelProvider(this).get(SearchModel.class);
        if (ProgressHelper.isBusy()) {
            pStatus.setVisibility(View.VISIBLE);
            fabSettings.setVisibility(View.GONE);
            etSearch.setEnabled(false);
        }
    }

    @Override
    public void onChanged(@Nullable Data data) {
        if (!ProgressHelper.isBusy() || data == null)
            return;
        if (data.getBoolean(Const.FINISH, false)) {
            ProgressHelper.setBusy(false);
            ProgressHelper.removeObservers(act);
            page = 0;
            etSearch.setEnabled(true);
            pStatus.setVisibility(View.GONE);
            String error = data.getString(Const.ERROR);
            if (error != null) {
                act.status.setError(error);
                return;
            }
            putResult(data.getInt(Const.MODE, 0),
                    data.getString(Const.STRING),
                    data.getInt(Const.START, 0),
                    data.getInt(Const.END, 0));
            return;
        }
        if (Objects.equals(data.getString(Const.MODE), Const.TIME)) {
            DateHelper d = DateHelper.putDays(act, data.getInt(Const.TIME, 0));
            tvStatus.setText(String.format(getString(R.string.format_search_date),
                    d.getMonthString(), d.getYear() + 2000));
        } else { //Const.PROG
            tvStatus.setText(String.format(getString(R.string.format_search_proc),
                    data.getInt(Const.PROG, 0)));
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(Const.DIALOG, dialog);
        if (dialog > -1)
            dateDialog.dismiss();
        outState.putInt(Const.SEARCH, page);
        if (page > -1) {
            outState.putBoolean(ADDITION, pAdditionSet.getVisibility() == View.VISIBLE);
            outState.putString(LABEL, tvLabel.getText().toString());
        }
        outState.putBoolean(SETTINGS, pSettings.getVisibility() == View.VISIBLE);
        super.onSaveInstanceState(outState);
    }

    private void restoreState(Bundle state) {
        File f = new File(act.lib.getDBFolder() + "/12.15");
        if (f.exists()) {
            // если последний загружаемый месяц с сайта Откровений загружен, значит расширяем диапозон поиска
            min_m = 8; //aug
            min_y = 2004; //2004
        }

        initMode();
        initDates();

        if (state == null) {
            if (string != null) {
                etSearch.setText(string);
                startSearch();
            }
        } else {
            act.setCurFragment(this);
            page = state.getInt(Const.SEARCH, -1);
            if (page > -1) {
                fabSettings.setVisibility(View.GONE);
                showResult();
                tvLabel.setText(state.getString(LABEL));
                if (state.getBoolean(ADDITION))
                    pAdditionSet.setVisibility(View.VISIBLE);
                else
                    bShow.setVisibility(View.VISIBLE);
            }
            if (state.getBoolean(SETTINGS)) {
                visSettings();
                dialog = state.getInt(Const.DIALOG);
                if (dialog > -1)
                    showDatePicker(dialog);
            }
            softKeyboard.closeSoftKeyboard();
        }

        if (adResults.getCount() == 0 && !ProgressHelper.isBusy()) {
            f = new File(act.lib.getDBFolder() + File.separator + Const.SEARCH);
            if (f.exists()) {
                adResults.addItem(new ListItem(
                        getString(R.string.results_last_search),
                        LAST_RESULTS));
                adResults.addItem(new ListItem(
                        getString(R.string.clear_results_search),
                        CLEAR_RESULTS));
                adResults.notifyDataSetChanged();
            }
        }
    }

    private void initMode() {
        if (mode == -1)
            mode = pref.getInt(Const.MODE, 5);
        sMode.setSelection(mode);
    }

    private void initDates() {
        int d = pref.getInt(Const.START, 0);
        if (d == 0) {
            dEnd = DateHelper.initToday(act);
            //if (mode < 5)// открываем ссылку с сайта Благая Весть
            //    dStart = DateHelper.putYearMonth(act, 2016, 1);
            dStart = DateHelper.putYearMonth(act, min_y, min_m);
        } else {
            dStart = DateHelper.putDays(act, d);
            dEnd = DateHelper.putDays(act, pref.getInt(Const.END, 0));
        }

        bStart.setText(formatDate(dStart));
        bEnd.setText(formatDate(dEnd));
    }

    private void visSettings() {
        fabSettings.setVisibility(View.GONE);
        fabOk.setVisibility(View.VISIBLE);
        mainLayout.setVisibility(View.GONE);
        pSettings.setVisibility(View.VISIBLE);
        softKeyboard.closeSoftKeyboard();
        pPages.setVisibility(View.GONE);
        initResizeAnim();
    }

    private void initResizeAnim() {
        if (anim == null) {
            anim = new ResizeAnim(pSettings, false, (int) (270 * getResources().getDisplayMetrics().density));
            anim.setStart(10);
            anim.setDuration(800);
            anim.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {

                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    pSettings.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    pSettings.requestLayout();
                }

                @Override
                public void onAnimationRepeat(Animation animation) {

                }
            });
        }
        pSettings.clearAnimation();
        pSettings.startAnimation(anim);
    }

    private String formatDate(DateHelper d) {
        return getResources().getStringArray(R.array.months_short)[d.getMonth() - 1] + " " + d.getYear();
    }

    private void initViews(View container) {
        pref = act.getSharedPreferences(this.getClass().getSimpleName(), Context.MODE_PRIVATE);
        editor = pref.edit();
        mainLayout = container.findViewById(R.id.content_search);
        pSettings = container.findViewById(R.id.pSettings);
        pAdditionSet = container.findViewById(R.id.pAdditionSet);
        fabSettings = container.findViewById(R.id.fabSettings);
        fabOk = container.findViewById(R.id.fabOk);
        rvPages = container.findViewById(R.id.rvPages);
        bStart = container.findViewById(R.id.bStartRange);
        bEnd = container.findViewById(R.id.bEndRange);
        pStatus = container.findViewById(R.id.pStatus);
        pPages = container.findViewById(R.id.pPages);
        bShow = container.findViewById(R.id.bShow);
        tvStatus = container.findViewById(R.id.tvStatus);
        cbSearchInResults = container.findViewById(R.id.cbSearchInResults);
        tvLabel = container.findViewById(R.id.tvLabel);
        etSearch = container.findViewById(R.id.etSearch);
        sMode = container.findViewById(R.id.sMode);
        ArrayAdapter<String> adBook = new ArrayAdapter<>(act, R.layout.spinner_button,
                getResources().getStringArray(R.array.search_mode));
        adBook.setDropDownViewResource(R.layout.spinner_item);
        sMode.setAdapter(adBook);
        lvResult = container.findViewById(R.id.lvResult);
        adResults = new ListAdapter(act);
        lvResult.setAdapter(adResults);
        InputMethodManager im = (InputMethodManager) act.getSystemService(Service.INPUT_METHOD_SERVICE);
        softKeyboard = new SoftKeyboard(mainLayout, im);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setViews(View container) {
        File f = new File(act.getFilesDir() + File.separator + Const.SEARCH);
        List<String> liSearch = new ArrayList<>();
        if (f.exists()) {
            try {
                BufferedReader br = new BufferedReader(new FileReader(f));
                String s;
                while ((s = br.readLine()) != null) {
                    liSearch.add(s);
                }
                br.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        adSearch = new ArrayAdapter<>(act, R.layout.spinner_item, liSearch);
        etSearch.setThreshold(1);
        etSearch.setAdapter(adSearch);
        etSearch.setOnKeyListener((view, keyCode, keyEvent) -> {
            if ((keyEvent.getAction() == KeyEvent.ACTION_DOWN && keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER)
                    || keyCode == EditorInfo.IME_ACTION_SEARCH) {
                enterSearch();
                return true;
            }
            return false;
        });
        etSearch.setOnTouchListener((view, motionEvent) -> {
            if (motionEvent.getAction() == MotionEvent.ACTION_DOWN)
                etSearch.showDropDown();
            return false;
        });
        container.findViewById(R.id.bSearch).setOnClickListener(view -> enterSearch());
        lvResult.setOnItemClickListener((adapterView, view, pos, l) -> {
            if (act.checkBusy()) return;
            if (adResults.getItem(pos).getLink().equals(LAST_RESULTS)) {
                fabSettings.setVisibility(View.GONE);
                bShow.setVisibility(View.VISIBLE);
                page = 0;
                showResult();
                String s = pref.getString(LABEL, "");
                tvLabel.setText(s);
                if (s.contains("“")) {
                    string = s.substring(s.indexOf("“") + 1, s.indexOf(Const.N) - 2);
                    etSearch.setText(string);
                }
            } else if (adResults.getItem(pos).getLink().equals(CLEAR_RESULTS)) {
                deleteBase();
                adResults.clear();
                adResults.notifyDataSetChanged();
            } else {
                Lib.showToast(act, getString(R.string.long_press_for_mark));
                BrowserActivity.openReader(act, adResults.getItem(pos).getLink(), string);
            }
        });
        lvResult.setOnItemLongClickListener((adapterView, view, pos, l) -> {
            String des = tvLabel.getText().toString();
            des = getString(R.string.search_for) +
                    des.substring(des.indexOf("“") - 1, des.indexOf(Const.N) - 1);
            MarkerActivity.addMarker(act, adResults.getItem(pos).getLink(), adResults.getItem(pos).getDes(), des);
            return true;
        });
        View.OnClickListener click = view -> {
            softKeyboard.closeSoftKeyboard();
            visSettings();
        };
        fabSettings.setOnClickListener(click);
        container.findViewById(R.id.bSettings).setOnClickListener(click);
        container.findViewById(R.id.bStop).setOnClickListener(view -> SearchModel.cancel = true);

        fabOk.setOnClickListener(view -> {
            if (page == -1)
                fabSettings.setVisibility(View.VISIBLE);
            else
                pPages.setVisibility(View.VISIBLE);
            fabOk.setVisibility(View.GONE);
            mainLayout.setVisibility(View.VISIBLE);
            pSettings.setVisibility(View.GONE);
            editor.putInt(Const.MODE, sMode.getSelectedItemPosition());
            editor.putInt(Const.START, dStart.getTimeInDays());
            editor.putInt(Const.END, dEnd.getTimeInDays());
            editor.apply();
        });
        container.findViewById(R.id.bClearSearch).setOnClickListener(view -> {
            adSearch.clear();
            adSearch.notifyDataSetChanged();
            File f1 = new File(act.getFilesDir() + File.separator + Const.SEARCH);
            if (f1.exists()) f1.delete();
        });
        bStart.setOnClickListener(view -> showDatePicker(0));
        bEnd.setOnClickListener(view -> showDatePicker(1));
        container.findViewById(R.id.bChangeRange).setOnClickListener(view -> {
            DateHelper d = dEnd;
            dEnd = dStart;
            dStart = d;
            bStart.setText(formatDate(dStart));
            bEnd.setText(formatDate(dEnd));
        });
        lvResult.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView absListView, int scrollState) {
                if (scrollState == SCROLL_STATE_IDLE && scrollToFirst) {
                    if (lvResult.getFirstVisiblePosition() > 0)
                        lvResult.smoothScrollToPosition(0);
                    else
                        scrollToFirst = false;
                }
            }

            @Override
            public void onScroll(AbsListView absListView, int firstVisibleItem,
                                 int visibleItemCount, int totalItemCount) {
            }
        });
        bShow.setOnClickListener(view -> {
            bShow.setVisibility(View.GONE);
            pAdditionSet.setVisibility(View.VISIBLE);
        });
        container.findViewById(R.id.bHide).setOnClickListener(view -> {
            bShow.setVisibility(View.VISIBLE);
            pAdditionSet.setVisibility(View.GONE);
        });
    }

    private void deleteBase() {
        File f = new File(act.lib.getDBFolder() + File.separator + Const.SEARCH);
        if (f.exists()) f.delete();
    }

    private void enterSearch() {
        etSearch.dismissDropDown();
        if (etSearch.length() < 3)
            Lib.showToast(act, getString(R.string.low_sym_for_search));
        else {
            pPages.setVisibility(View.GONE);
            page = 0;
            startSearch();
        }
    }

    private void showDatePicker(int id) {
        DateHelper d;
        if (id == 0)
            d = dStart;
        else
            d = dEnd;
        dialog = id;
        dateDialog = new DateDialog(act, d);
        dateDialog.setMinMonth(min_m);
        dateDialog.setMinYear(min_y);
        dateDialog.setResult(this);
        dateDialog.show();
    }

    @Override
    public void putDate(@Nullable DateHelper date) {
        if (date == null) { // cancel
            dialog = -1;
            return;
        }
        if (dialog == 0) {
            dStart = date;
            bStart.setText(formatDate(dStart));
        } else {
            dEnd = date;
            bEnd.setText(formatDate(dEnd));
        }
        dialog = -1;
    }

    private void startSearch() {
        etSearch.setEnabled(false);
        page = -1;
        pStatus.setVisibility(View.VISIBLE);
        fabSettings.setVisibility(View.GONE);
        string = etSearch.getText().toString();
        int mode;
        if (cbSearchInResults.isChecked()) {
            mode = 6;
            tvStatus.setText(getString(R.string.search));
        } else
            mode = sMode.getSelectedItemPosition();
        ProgressHelper.addObserver(act, this);
        model.search(string, mode, dStart.getMY(), dEnd.getMY());
        boolean needAdd = true;
        for (int i = 0; i < adSearch.getCount(); i++) {
            if (adSearch.getItem(i).equals(string)) {
                needAdd = false;
                break;
            }
        }
        if (needAdd) {
            adSearch.add(string);
            adSearch.notifyDataSetChanged();
            File f = new File(act.getFilesDir() + File.separator + Const.SEARCH);
            try {
                BufferedWriter bw = new BufferedWriter(new FileWriter(f, true));
                bw.write(string + Const.N);
                bw.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void putResult(int mode, String str, int count1, int count2) {
        if (count1 > 0) {
            pAdditionSet.setVisibility(View.VISIBLE);
            cbSearchInResults.setChecked(true);
            String s;
            if (mode == 6)
                s = getString(R.string.search_in_results);
            else
                s = getResources().getStringArray(R.array.search_mode)[mode];
            tvLabel.setText(
                    String.format(getString(R.string.format_found),
                            s.substring(s.indexOf(" ") + 1), str, count1, count2));
            editor.putString(LABEL, tvLabel.getText().toString());
            editor.apply();
        }
        showResult();
    }

    private void showResult() {
        adResults.clear();
        adResults.notifyDataSetChanged();
        SearchStorage storage = new SearchStorage(requireContext());
        Cursor cursor = storage.getResults(dStart.getTimeInMills() > dEnd.getTimeInMills());
        if (cursor.getCount() == 0) {
            bShow.setVisibility(View.GONE);
            pAdditionSet.setVisibility(View.GONE);
            fabSettings.setVisibility(View.VISIBLE);
            AlertDialog.Builder builder = new AlertDialog.Builder(act, R.style.NeoDialog);
            builder.setMessage(getString(R.string.alert_search));
            builder.setPositiveButton(getString(android.R.string.ok),
                    (dialog, id) -> dialog.dismiss());
            builder.create().show();
            deleteBase();
        } else {
            pPages.setVisibility(View.VISIBLE);
            int max = cursor.getCount() / Const.MAX_ON_PAGE;
            if (cursor.getCount() % Const.MAX_ON_PAGE > 0) max++;
            if (adPages != null && adPages.getItemCount() == max)
                adPages.setSelect(page);
            else {
                adPages = new PageAdapter(max, page, this);
                LinearLayoutManager layoutManager = new LinearLayoutManager(act, LinearLayoutManager.HORIZONTAL, false);
                rvPages.setLayoutManager(layoutManager);
                rvPages.setAdapter(adPages);
            }
            if (cursor.moveToPosition(page * Const.MAX_ON_PAGE)) {
                int iTitle = cursor.getColumnIndex(Const.TITLE);
                int iLink = cursor.getColumnIndex(Const.LINK);
                int iDes = cursor.getColumnIndex(Const.DESCTRIPTION);
                ListItem item;
                String s;
                do {
                    item = new ListItem(cursor.getString(iTitle), cursor.getString(iLink));
                    s = cursor.getString(iDes);
                    if (s != null)
                        item.setDes(s);
                    adResults.addItem(item);
                } while (cursor.moveToNext() && adResults.getCount() < Const.MAX_ON_PAGE);
                adResults.notifyDataSetChanged();
                if (lvResult.getFirstVisiblePosition() > 0) {
                    scrollToFirst = true;
                    lvResult.smoothScrollToPosition(0);
                }
            }
        }
        storage.close();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View v, MotionEvent event) {//click page item
        if (event.getAction() != MotionEvent.ACTION_UP)
            return false;
        int pos = (int) v.getTag();
        if (page != pos) {
            page = pos;
            showResult();
        }
        return false;
    }
}
