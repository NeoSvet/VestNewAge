package ru.neosvet.ui;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import ru.neosvet.vestnewage.activity.MainActivity;
import ru.neosvet.vestnewage.helpers.ProgressHelper;

public class NeoFragment extends Fragment {
    protected MainActivity act;

    @Override
    public void onAttach(@NonNull Context context) {
        act = (MainActivity) getActivity();
        super.onAttach(context);
    }

    @Override
    public void onDestroyView() {
        act = null;
        super.onDestroyView();
    }

    public boolean onBackPressed() {
        if (act.status.isCrash() || !act.status.isStop()) {
            onStatusClick(true);
            return false;
        }
        if (ProgressHelper.isBusy()) {
            ProgressHelper.cancelled();
            return false;
        }
        return true;
    }

    public void startLoad() {
    }

    public void onStatusClick(boolean reset) {
        if (reset) {
            if (!act.status.isStop())
                act.status.setLoad(false);
            else
                act.status.setError(null);
            return;
        }
        act.status.onClick();
    }
}