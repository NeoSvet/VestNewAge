package ru.neosvet.vestnewage;

import android.app.Fragment;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;

import ru.neosvet.ui.ListAdapter;
import ru.neosvet.utils.Lib;
import ru.neosvet.utils.SearchTask;


public class SearchFragment extends Fragment {
    private MainActivity act;
    private View container, fabSearch;
    private EditText etSearch;
    private SearchTask task = null;
    private ListAdapter adResults;

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
        outState.putSerializable(Lib.TASK, task);
    }

    private void restoreActivityState(Bundle state) {
        if (state == null) {

        } else {
            task = (SearchTask) state.getSerializable(Lib.TASK);
            if (task != null) {
                task.setFrm(this);
                act.status.setLoad(true); // search?
            }
        }
    }

    private void initViews() {
        fabSearch = container.findViewById(R.id.fabSearch);
        etSearch = (EditText) container.findViewById(R.id.etSearch);
        ListView lvResult = (ListView) container.findViewById(R.id.lvResult);
        adResults = new ListAdapter(act);
        lvResult.setAdapter(adResults);
        lvResult.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int pos, long l) {

            }
        });
    }

    private void setViews() {
        etSearch.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                if (etSearch.length() > 2 ) {
                    fabSearch.setVisibility(View.VISIBLE);
                } else {
                    fabSearch.setVisibility(View.GONE);
                }
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });
        etSearch.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int keyCode, KeyEvent keyEvent) {
                if ((keyEvent.getAction() == KeyEvent.ACTION_DOWN && keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER)
                        || keyCode == EditorInfo.IME_ACTION_SEARCH) {
                    if (fabSearch.getVisibility() == View.VISIBLE)
                        goSearch();
                    return true;
                }
                return false;
            }
        });
        fabSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                goSearch();
            }
        });
    }

    private void goSearch() {
        Lib.LOG("goSearch");
        task = new SearchTask(this);
        task.execute(etSearch.getText().toString());
    }

    public void finishSearch(String result) {

    }
}
