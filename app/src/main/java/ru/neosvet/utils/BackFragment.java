package ru.neosvet.utils;

import androidx.fragment.app.Fragment;

import ru.neosvet.vestnewage.helpers.ProgressHelper;

public class BackFragment extends Fragment {
    protected boolean created = false, needLoad = false;
    public boolean onBackPressed() {
        if (ProgressHelper.isBusy()) {
            ProgressHelper.cancelled();
            return false;
        }
        return true;
    }
    public void startLoad() {
    }
}
