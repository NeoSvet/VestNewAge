package ru.neosvet.vestnewage;

import android.app.Fragment;
import android.app.Service;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ListView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import ru.neosvet.ui.DateDialog;
import ru.neosvet.ui.ListAdapter;
import ru.neosvet.ui.SoftKeyboard;
import ru.neosvet.utils.Lib;
import ru.neosvet.utils.SearchTask;


public class SearchFragment extends Fragment implements DateDialog.Result {
    private final String SEARCH = "search", START = "start", END = "end", SETTINGS = "s", DIALOG = "d";
    private final DateFormat df = new SimpleDateFormat(" yyyy");
    private MainActivity act;
    private View container, fabSettings, fabOk, pSettings;
    private LinearLayout mainLayout;
    private CheckBox cbSearchInResults;
    private Button bStart, bEnd;
    private AutoCompleteTextView etSearch;
    private ArrayAdapter<String> adSearch;
    private SearchTask task = null;
    private Date dStart, dEnd;
    private ListAdapter adResults;
    private int dialog = -1;
    private DateDialog dateDialog;
    private SoftKeyboard softKeyboard;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        this.container = inflater.inflate(R.layout.search_fragment, container, false);
        act = (MainActivity) getActivity();
        act.setTitle(getResources().getString(R.string.search));
        initViews();
        setViews();
        restoreActivityState(savedInstanceState);

        return this.container;
    }


    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(DIALOG, dialog);
        if (dialog > -1)
            dateDialog.dismiss();
        outState.putSerializable(Lib.TASK, task);
        outState.putLong(START, dStart.getTime());
        outState.putLong(END, dEnd.getTime());
        outState.putBoolean(SEARCH, cbSearchInResults.isChecked());
        outState.putBoolean(SETTINGS, pSettings.getVisibility() == View.VISIBLE);
        super.onSaveInstanceState(outState);
    }

    private void restoreActivityState(Bundle state) {
        if (state == null) {
            dEnd = new Date();
            dStart = new Date();
            dStart.setYear(116);
            dStart.setMonth(0);
        } else {
            task = (SearchTask) state.getSerializable(Lib.TASK);
            if (task != null) {
                task.setFrm(this);
                act.status.setLoad(true); // search?
            }
            dStart = new Date(state.getLong(START));
            dEnd = new Date(state.getLong(END));
            cbSearchInResults.setChecked(state.getBoolean(SEARCH));
            if (state.getBoolean(SETTINGS)) {
                visSettings();
                dialog = state.getInt(DIALOG);
                if (dialog > -1)
                    showDatePicker(dialog);
            }
            softKeyboard.closeSoftKeyboard();
        }
        bStart.setText(formatDate(dStart));
        bEnd.setText(formatDate(dEnd));
    }

    private void visSettings() {
        fabSettings.setVisibility(View.GONE);
        fabOk.setVisibility(View.VISIBLE);
        mainLayout.setVisibility(View.GONE);
        pSettings.setVisibility(View.VISIBLE);
    }

    private String formatDate(Date d) {
        return getResources().getStringArray(
                R.array.months_short)[d.getMonth()]
                + df.format(d);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        final File f = new File(act.getFilesDir() + SEARCH);
        if (adSearch.getCount() == 0) {
            if (f.exists()) f.delete();
        } else {
            try {
                BufferedWriter bw = new BufferedWriter(new FileWriter(f));
                for (int i = 0; i < adSearch.getCount() - 1; i++) {
                    bw.write(adSearch.getItem(i) + Lib.N);
                    bw.flush();
                }
                bw.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void initViews() {
        mainLayout=(LinearLayout) container.findViewById(R.id.content_search);
        pSettings = container.findViewById(R.id.pSettings);
        fabSettings = container.findViewById(R.id.fabSettings);
        fabOk = container.findViewById(R.id.fabOk);
        bStart = (Button) container.findViewById(R.id.bStartRange);
        bEnd = (Button) container.findViewById(R.id.bEndRange);
        cbSearchInResults = (CheckBox) container.findViewById(R.id.cbSearchInResults);
        etSearch = (AutoCompleteTextView) container.findViewById(R.id.etSearch);
        ListView lvResult = (ListView) container.findViewById(R.id.lvResult);
        adResults = new ListAdapter(act);
        lvResult.setAdapter(adResults);
        lvResult.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int pos, long l) {

            }
        });
        InputMethodManager im = (InputMethodManager) act.getSystemService(Service.INPUT_METHOD_SERVICE);
        softKeyboard = new SoftKeyboard(mainLayout, im);
    }

    private void setViews() {
        final File f = new File(act.getFilesDir() + SEARCH);
        adSearch = new ArrayAdapter<String>(act, android.R.layout.select_dialog_item);
        if (f.exists()) {
            try {
                BufferedReader br = new BufferedReader(new FileReader(f));
                String s;
                while ((s = br.readLine()) != null)
                    adSearch.add(s);
                br.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        etSearch.setThreshold(1);
        etSearch.setAdapter(adSearch);
        etSearch.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int keyCode, KeyEvent keyEvent) {
                if ((keyEvent.getAction() == KeyEvent.ACTION_DOWN && keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER)
                        || keyCode == EditorInfo.IME_ACTION_SEARCH) {
                    if (etSearch.length() < 3)
                        Lib.showToast(act, getResources().getString(R.string.low_sym_for_search));
                    else {
                        softKeyboard.closeSoftKeyboard();
                        final String s = etSearch.getText().toString();
                        task = new SearchTask(SearchFragment.this);
                        task.execute(s);
                        boolean b = true;
                        for (int i = 0; i < adSearch.getCount(); i++) {
                            if (adSearch.getItem(i).equals(s)) {
                                b = false;
                                break;
                            }
                        }
                        if (b) {
                            adSearch.add(s);
                            adSearch.notifyDataSetChanged();
                        }
                    }

                    return true;
                }
                return false;
            }
        });
        fabSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                visSettings();
            }
        });
        fabOk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                fabSettings.setVisibility(View.VISIBLE);
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
                Date d = dEnd;
                dEnd = dStart;
                dStart = d;
                bStart.setText(formatDate(dStart));
                bEnd.setText(formatDate(dEnd));
            }
        });
    }

    private void showDatePicker(int id) {
        Date d;
        if (id == 0)
            d = dStart;
        else
            d = dEnd;
        dialog = id;
        dateDialog = new DateDialog(act, d);
        dateDialog.setResult(this);
        dateDialog.show();
    }

    @Override
    public void putDate(Date date) {
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


    public void finishSearch(String result) {
        task = null;
    }


}
