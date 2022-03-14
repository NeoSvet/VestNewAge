package ru.neosvet.vestnewage.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import ru.neosvet.utils.Const;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.list.ListAdapter;
import ru.neosvet.vestnewage.list.ListItem;

public class WelcomeFragment extends BottomSheetDialogFragment {
    public interface ItemClicker {
        void onItemClick(String link);
    }

    private ItemClicker clicker;
    private ListAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.welcome_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        getDialog().setOnShowListener(dialog -> {
            BottomSheetDialog d = (BottomSheetDialog) dialog;
            FrameLayout bottomSheet = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            BottomSheetBehavior.from(bottomSheet).setPeekHeight(
                    (int) (175 * getResources().getDisplayMetrics().density));
        });

        ListView lvBottom = view.findViewById(R.id.lvBottom);
        adapter = new ListAdapter(requireContext());
        lvBottom.setAdapter(adapter);
        lvBottom.setOnItemClickListener((adapterView, view1, pos, l) -> {
            if (clicker != null)
                clicker.onItemClick(adapter.getItem(pos).getLink());
        });
        fillInList();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof ItemClicker)
            clicker = (ItemClicker) context;
    }

    private void fillInList() {
        Bundle args = getArguments();
        if (args == null)
            return;

        if (args.getBoolean(Const.ADS)) {
            adapter.addItem(new ListItem(getString(R.string.new_dev_ads), Const.ADS));
        }

        if (args.getBoolean(Const.PAGE)) {
            String[] title = args.getStringArray(Const.TITLE);
            String[] link = args.getStringArray(Const.LINK);
            for (int i = 0; i < title.length; i++) {
                ListItem item = new ListItem(title[i], link[i]);
                item.setDes(getString(R.string.updated_page));
                adapter.addItem(item);
            }
        }

        if (args.getBoolean(Const.TIME)) {
            int timeDiff = args.getInt(Const.TIMEDIFF);
            ListItem item = new ListItem(getString(R.string.sync_time));
            if (timeDiff == 0)
                item.setDes(getString(R.string.matches));
            else
                item.setDes(String.format(getString(R.string.time_deviation),
                        timeDiff));
            adapter.addItem(item);
            adapter.notifyDataSetChanged();
        }
    }
}
