package ru.neosvet.utils;

import android.app.Fragment;

import ru.neosvet.vestnewage.helpers.ProgressHelper;

public class BackFragment extends Fragment {
    public boolean onBackPressed() {
        if (ProgressHelper.isBusy()) {
            if(ProgressHelper.isCancelled())
                return true;
            ProgressHelper.cancelled();
            return false;
        }
        return true;
    }
}
