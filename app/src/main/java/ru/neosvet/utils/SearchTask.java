package ru.neosvet.utils;

import android.os.AsyncTask;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import ru.neosvet.ui.ListItem;
import ru.neosvet.vestnewage.MainActivity;
import ru.neosvet.vestnewage.SearchFragment;

public class SearchTask extends AsyncTask<String, Void, String> implements Serializable {
    private transient SearchFragment frm;
    private transient MainActivity act;
    List<ListItem> data = new ArrayList<ListItem>();

    public SearchTask(SearchFragment frm) {
        setFrm(frm);
    }

    public void setFrm(SearchFragment frm) {
        this.frm = frm;
        act = (MainActivity) frm.getActivity();
    }

    @Override
    protected void onProgressUpdate(Void... values) {
        super.onProgressUpdate(values);
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }

    @Override
    protected void onPostExecute(String result) {
        super.onPostExecute(result);
        if (frm != null) {
            frm.finishSearch(result);
        }
    }

    @Override
    protected String doInBackground(String... params) {
        try {

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
