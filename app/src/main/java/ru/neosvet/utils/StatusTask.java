package ru.neosvet.utils;

import android.os.AsyncTask;

import java.io.Serializable;

import ru.neosvet.vestnewage.MainActivity;
import ru.neosvet.vestnewage.StatusFragment;

public class StatusTask extends AsyncTask<String, Void, Boolean> implements Serializable {
    private transient StatusFragment frm;
    private transient MainActivity act;

    @Override
    protected Boolean doInBackground(String... strings) {

        return true;
    }
}
