package ru.neosvet.vestnewage.fragment;

import android.app.Service;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.work.Data;
import androidx.work.WorkInfo;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

import ru.neosvet.ui.RecyclerItemClickListener;
import ru.neosvet.ui.ResizeAnim;
import ru.neosvet.ui.SoftKeyboard;
import ru.neosvet.ui.dialogs.DateDialog;
import ru.neosvet.utils.BackFragment;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.BrowserActivity;
import ru.neosvet.vestnewage.activity.MainActivity;
import ru.neosvet.vestnewage.activity.MarkerActivity;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.list.ListAdapter;
import ru.neosvet.vestnewage.list.ListItem;
import ru.neosvet.vestnewage.list.PageAdapter;
import ru.neosvet.vestnewage.model.SearchModel;


public class SearchFragment extends BackFragment implements DateDialog.Result, View.OnClickListener {
    private final String SETTINGS = "s", ADDITION = "a", LABEL = "l", LAST_RESULTS = "r";
    private MainActivity act;
    private float density;
    private View container, fabSettings, fabOk, pSettings, pPages, pStatus, bShow, pAdditionSet;
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
    private int min_m = 1, min_y = 2016, dialog = -1, mode = 5, page = -1;
    private DateDialog dateDialog;
    private SoftKeyboard softKeyboard;
    private String string;
    SharedPreferences pref;
    SharedPreferences.Editor editor;
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
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        this.container = inflater.inflate(R.layout.search_fragment, container, false);
        act = (MainActivity) getActivity();
        act.setTitle(getResources().getString(R.string.search));
        density = getResources().getDisplayMetrics().density;
        initViews();
        setViews();
        initModel();
        restoreActivityState(savedInstanceState);

        return this.container;
    }

    @Override
    public void onPause() {
        super.onPause();
        model.removeObserves(act);
    }

    @Override
    public boolean onBackPressed() {
        if (!act.isMenuMode)
            onDestroy(); //сохранение "истории поиска"
        return true;
    }

    private void initModel() {
        model = ViewModelProviders.of(act).get(SearchModel.class);
        model.getProgress().observe(act, new Observer<Data>() {
            @Override
            public void onChanged(@Nullable Data data) {
                DateHelper d = DateHelper.putDays(act, data.getInt(Const.TIME, 0));
                tvStatus.setText(getResources().getString(R.string.search) + ": " + d.getMonthString() + " " + (d.getYear() + 2000));
            }
        });
        model.getState().observe(act, new Observer<List<WorkInfo>>() {
            @Override
            public void onChanged(@Nullable List<WorkInfo> workInfos) {
                for (int i = 0; i < workInfos.size(); i++) {
                    if (workInfos.get(i).getState().isFinished()) {
                        Data result = workInfos.get(i).getOutputData();
                        putResult(result.getInt(Const.MODE, 0),
                                result.getString(Const.STRING),
                                result.getInt(Const.START, 0),
                                result.getInt(Const.END, 0));
                    }
                    if (workInfos.get(i).getState().equals(WorkInfo.State.FAILED))
                        Lib.showToast(act, workInfos.get(i).getOutputData().getString(Const.ERROR));
                }
            }
        });
        if (model.inProgress) {
            pStatus.setVisibility(View.VISIBLE);
            fabSettings.setVisibility(View.GONE);
            etSearch.setEnabled(false);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(Const.DIALOG, dialog);
        if (dialog > -1)
            dateDialog.dismiss();
        outState.putInt(Const.START, dStart.getTimeInDays());
        outState.putInt(Const.END, dEnd.getTimeInDays());
        outState.putInt(DataBase.SEARCH, page);
        if (page > -1) {
            outState.putBoolean(ADDITION, pAdditionSet.getVisibility() == View.VISIBLE);
            outState.putString(LABEL, tvLabel.getText().toString());
        }
        outState.putBoolean(SETTINGS, pSettings.getVisibility() == View.VISIBLE);
        super.onSaveInstanceState(outState);
    }

    private void restoreActivityState(Bundle state) {
        File f = new File(act.lib.getDBFolder() + "/12.15");
        if (f.exists()) {
            // если последний загружаемый месяц с сайта Откровений загружен, значит расширяем диапозон поиска
            min_m = 8; //aug
            min_y = 2004; //2004
        }
        if (state == null) {
            dEnd = DateHelper.initToday(act);
            if (mode < 5)// открываем ссылку с сайта Благая Весть
                dStart = DateHelper.putYearMonth(act, 2016, 1);
            else
                dStart = DateHelper.putYearMonth(act, min_y, min_m);
            sMode.setSelection(mode);
            if (string != null) {
                etSearch.setText(string);
                startSearch();
            }
        } else {
            act.setCurFragment(this);
            dStart = DateHelper.putDays(act, state.getInt(Const.START));
            dEnd = DateHelper.putDays(act, state.getInt(Const.END));
            page = state.getInt(DataBase.SEARCH, -1);
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
        bStart.setText(formatDate(dStart));
        bEnd.setText(formatDate(dEnd));
        if (adResults.getCount() == 0 && !model.inProgress) {
            f = new File(act.lib.getDBFolder() + "/" + DataBase.SEARCH);
            if (f.exists()) {
                adResults.addItem(new ListItem(
                        getResources().getString(R.string.results_last_search),
                        LAST_RESULTS));
                adResults.notifyDataSetChanged();
            }
        }
    }

    private void visSettings() {
        fabSettings.setVisibility(View.GONE);
        fabOk.setVisibility(View.VISIBLE);
        mainLayout.setVisibility(View.GONE);
        pSettings.setVisibility(View.VISIBLE);
        softKeyboard.closeSoftKeyboard();
        pPages.setVisibility(View.GONE);
        ResizeAnim anim = new ResizeAnim(pSettings, false, (int) (270 * density));
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
        pSettings.clearAnimation();
        pSettings.startAnimation(anim);
    }

    private String formatDate(DateHelper d) {
        return getResources().getStringArray(R.array.months_short)[d.getMonth() - 1] + " " + d.getYear();
    }

    private void initViews() {
        pref = act.getSharedPreferences(this.getClass().getSimpleName(), Context.MODE_PRIVATE);
        editor = pref.edit();
        mainLayout = (LinearLayout) container.findViewById(R.id.content_search);
        pSettings = container.findViewById(R.id.pSettings);
        pAdditionSet = container.findViewById(R.id.pAdditionSet);
        fabSettings = container.findViewById(R.id.fabSettings);
        fabOk = container.findViewById(R.id.fabOk);
        rvPages = (RecyclerView) container.findViewById(R.id.rvPages);
        bStart = (Button) container.findViewById(R.id.bStartRange);
        bEnd = (Button) container.findViewById(R.id.bEndRange);
        pStatus = container.findViewById(R.id.pStatus);
        pPages = container.findViewById(R.id.pPages);
        bShow = container.findViewById(R.id.bShow);
        tvStatus = (TextView) container.findViewById(R.id.tvStatus);
        cbSearchInResults = (CheckBox) container.findViewById(R.id.cbSearchInResults);
        tvLabel = (TextView) container.findViewById(R.id.tvLabel);
        etSearch = (AutoCompleteTextView) container.findViewById(R.id.etSearch);
        sMode = (Spinner) container.findViewById(R.id.sMode);
        ArrayAdapter<String> adBook = new ArrayAdapter<String>(act, R.layout.spinner_button,
                getResources().getStringArray(R.array.search_mode));
        adBook.setDropDownViewResource(R.layout.spinner_item);
        sMode.setAdapter(adBook);
        lvResult = (ListView) container.findViewById(R.id.lvResult);
        adResults = new ListAdapter(act);
        lvResult.setAdapter(adResults);
        InputMethodManager im = (InputMethodManager) act.getSystemService(Service.INPUT_METHOD_SERVICE);
        softKeyboard = new SoftKeyboard(mainLayout, im);
    }

    private void setViews() {
        final File f = new File(act.getFilesDir() + File.separator + DataBase.SEARCH);
        List<String> liSearch = new ArrayList<String>();
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
        adSearch = new ArrayAdapter<String>(act, R.layout.spinner_item, liSearch);
        etSearch.setThreshold(1);
        etSearch.setAdapter(adSearch);
        etSearch.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int keyCode, KeyEvent keyEvent) {
                if ((keyEvent.getAction() == KeyEvent.ACTION_DOWN && keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER)
                        || keyCode == EditorInfo.IME_ACTION_SEARCH) {
                    enterSearch();
                    return true;
                }
                return false;
            }
        });
        etSearch.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN)
                    etSearch.showDropDown();
                return false;
            }
        });
        container.findViewById(R.id.bSearch).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                enterSearch();
            }
        });
        lvResult.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int pos, long l) {
                if (model.inProgress) return;
                if (adResults.getItem(pos).getLink().equals(LAST_RESULTS)) {
                    fabSettings.setVisibility(View.GONE);
                    bShow.setVisibility(View.VISIBLE);
                    page = 0;
                    showResult();
                    tvLabel.setText(pref.getString(LABEL, ""));
                } else {
                    Lib.showToast(act, getResources().getString(R.string.long_press_for_mark));
                    BrowserActivity.openReader(act, adResults.getItem(pos).getLink(),
                            Lib.withOutTags(adResults.getItem(pos).getDes()));
                }
            }
        });
        lvResult.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int pos, long l) {
                String des = tvLabel.getText().toString();
                des = getResources().getString(R.string.search_for) +
                        des.substring(des.indexOf("“") - 1, des.indexOf(Const.N) - 1);
                MarkerActivity.addMarker(act, adResults.getItem(pos).getLink(), adResults.getItem(pos).getDes(), des);
                return true;
            }
        });
        fabSettings.setOnClickListener(this);
        container.findViewById(R.id.bSettings).setOnClickListener(this);
        container.findViewById(R.id.bStop).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                model.finish();
            }
        });

        fabOk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (page == -1)
                    fabSettings.setVisibility(View.VISIBLE);
                else
                    pPages.setVisibility(View.VISIBLE);
                fabOk.setVisibility(View.GONE);
                mainLayout.setVisibility(View.VISIBLE);
                pSettings.setVisibility(View.GONE);
            }
        });
        container.findViewById(R.id.bClearSearch).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                adSearch.clear();
                adSearch.notifyDataSetChanged();
                File f = new File(act.getFilesDir() + File.separator + DataBase.SEARCH);
                if (f.exists()) f.delete();
            }
        });
        bStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showDatePicker(0);
            }
        });
        bEnd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showDatePicker(1);
            }
        });
        container.findViewById(R.id.bChangeRange).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DateHelper d = dEnd;
                dEnd = dStart;
                dStart = d;
                bStart.setText(formatDate(dStart));
                bEnd.setText(formatDate(dEnd));
            }
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
        rvPages.addOnItemTouchListener(
                new RecyclerItemClickListener(act, new RecyclerItemClickListener.OnItemClickListener() {
                    @Override
                    public void onItemClick(View view, final int pos) {
                        if (page != pos) {
                            page = pos;
                            showResult();
                        }
                    }
                }));
        bShow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                bShow.setVisibility(View.GONE);
                pAdditionSet.setVisibility(View.VISIBLE);
            }
        });
        container.findViewById(R.id.bHide).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                bShow.setVisibility(View.VISIBLE);
                pAdditionSet.setVisibility(View.GONE);
            }
        });
    }

    private void enterSearch() {
        etSearch.dismissDropDown();
        if (etSearch.length() < 3)
            Lib.showToast(act, getResources().getString(R.string.low_sym_for_search));
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
        final String s = etSearch.getText().toString();
        int mode;
        if (cbSearchInResults.isChecked()) {
            mode = 6;
            tvStatus.setText(getResources().getString(R.string.search));
        } else
            mode = sMode.getSelectedItemPosition();
        model.search(s, mode, dStart.getMY(), dEnd.getMY());
        boolean needAdd = true;
        for (int i = 0; i < adSearch.getCount(); i++) {
            if (adSearch.getItem(i).equals(s)) {
                needAdd = false;
                break;
            }
        }
        if (needAdd) {
            adSearch.add(s);
            adSearch.notifyDataSetChanged();
            File f = new File(act.getFilesDir() + File.separator + DataBase.SEARCH);
            try {
                BufferedWriter bw = new BufferedWriter(new FileWriter(f, true));
                bw.write(s + Const.N);
                bw.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void putResult(int mode, String str, int count1, int count2) {
        page = 0;
        etSearch.setEnabled(true);
        pStatus.setVisibility(View.GONE);
        model.finish();
        if (count1 > 0) {
            pAdditionSet.setVisibility(View.VISIBLE);
            cbSearchInResults.setChecked(true);
            String s;
            if (mode == 6)
                s = getResources().getString(R.string.search_in_results);
            else
                s = getResources().getStringArray(R.array.search_mode)[mode];
            tvLabel.setText(
                    getResources().getString(R.string.you_search)
                            .replace("w1", s.substring(s.indexOf(" ") + 1))
                            .replace("w2", str)
                            + Const.N + getResources().getString(R.string.found_in)
                            .replace("n1", String.valueOf(count1))
                            .replace("n2", String.valueOf(count2)));
            editor.putString(LABEL, tvLabel.getText().toString());
            editor.apply();
        }
        showResult();
    }

    private void showResult() {
        adResults.clear();
        adResults.notifyDataSetChanged();
        DataBase dataBase = new DataBase(act, DataBase.SEARCH);
        SQLiteDatabase db = dataBase.getWritableDatabase();
        Cursor cursor = db.query(DataBase.SEARCH, null, null, null, null, null,
                DataBase.ID + (dStart.getTimeInMills() > dEnd.getTimeInMills() ? DataBase.DESC : ""));
        if (cursor.getCount() == 0) {
            bShow.setVisibility(View.GONE);
            pAdditionSet.setVisibility(View.GONE);
            fabSettings.setVisibility(View.VISIBLE);
            AlertDialog.Builder builder = new AlertDialog.Builder(act, R.style.NeoDialog);
            builder.setMessage(getResources().getString(R.string.alert_search));
            builder.setPositiveButton(getResources().getString(android.R.string.ok),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();
                        }
                    });
            builder.create().show();
        } else {
            pPages.setVisibility(View.VISIBLE);
            int max = cursor.getCount() / Const.MAX_ON_PAGE;
            if (cursor.getCount() % Const.MAX_ON_PAGE > 0) max++;
            if (adPages != null && adPages.getItemCount() == max)
                adPages.setSelect(page);
            else {
                adPages = new PageAdapter(act, max, page);
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
    }

    @Override
    public void onClick(View view) { //click fabSettings and bSettings
        softKeyboard.closeSoftKeyboard();
        visSettings();
    }
}
