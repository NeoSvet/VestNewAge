package ru.neosvet.vestnewage;

import android.os.Bundle;
import android.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import ru.neosvet.ui.ListAdapter;

public class StatusFragment extends Fragment {

    private MainActivity act;
    private ListAdapter adMain;
    private View container, fabExit;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        this.container = inflater.inflate(R.layout.status_fragment, container, false);
        act = (MainActivity) getActivity();
        initViews();
//        setViews();
//        restoreActivityState(savedInstanceState);
        return this.container;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
//        outState.putInt(CURRENT_TAB, tabHost.getCurrentTab());
//        outState.putSerializable(Lib.TASK, task);
        super.onSaveInstanceState(outState);
    }

    private void initViews() {
        act.setTitle(getResources().getString(R.string.status));
    }

}
