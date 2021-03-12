package ru.neosvet.utils;

import androidx.fragment.app.Fragment;

import ru.neosvet.vestnewage.helpers.ProgressHelper;

public class BackFragment extends Fragment {
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
