package ru.neosvet.ui.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import ru.neosvet.ui.RecyclerItemClickListener;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.helpers.DateHelper;

public class DateDialog extends Dialog {
    private Activity act;
    private TextView tvYear;
    private DateHelper date;
    private Result result;
    private MonthAdapter adMonth;
    private int min_year = 2016, min_month = 1, max_year = 0, max_month = 0;
    private boolean cancel = true;

    public DateDialog(Activity act, DateHelper date) {
        super(act);
        this.act = act;
        this.date = DateHelper.newBuilder(act).setDays(date.getTimeInDays()).build();
    }

    public void setResult(Result result) {
        this.result = result;
    }

    public void setMinYear(int min_year) {
        this.min_year = min_year;
    }

    public void setMaxYear(int max_year) {
        this.max_year = max_year;
    }

    public void setMinMonth(int min_month) {
        this.min_month = min_month;
    }

    public void setMaxMonth(int max_month) {
        this.max_month = max_month;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_date);

        tvYear = (TextView) findViewById(R.id.tvYear);
        findViewById(R.id.bMinus).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (date.getYear() > min_year) {
                    date.setYear(date.getYear() - 1);
                    date.setMonth(12);
                    setCalendar();
                }
            }
        });
        findViewById(R.id.bPlus).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (date.getYear() < max_year) {
                    date.setYear(date.getYear() + 1);
                    date.setMonth(1);
                    setCalendar();
                }
            }
        });
        findViewById(R.id.bStart).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                date.setYear(min_year);
                date.setMonth(min_month);
                setCalendar();
            }
        });
        findViewById(R.id.bEnd).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                date.setYear(max_year);
                date.setMonth(max_month);
                setCalendar();
            }
        });

        RecyclerView rvMonth = (RecyclerView) findViewById(R.id.rvMonth);
        GridLayoutManager layoutManager = new GridLayoutManager(act, 3);
        adMonth = new MonthAdapter();
        for (int i = 0; i < 12; i++) {
            adMonth.addItem(act.getResources().getStringArray(R.array.months)[i]);
        }
        rvMonth.setLayoutManager(layoutManager);
        rvMonth.setAdapter(adMonth);
        DateHelper d = DateHelper.initToday(act);
        if (max_year == 0)
            max_year = d.getYear();
        if (max_month == 0)
            max_month = d.getMonth();
        setCalendar();
        rvMonth.addOnItemTouchListener(
                new RecyclerItemClickListener(act, new RecyclerItemClickListener.OnItemClickListener() {
                    @Override
                    public void onItemClick(View view, final int pos) {
                        date.setMonth(pos + 1);
                        adMonth.setSelect(pos);
                        adMonth.notifyDataSetChanged();
                    }
                }));

        findViewById(R.id.bOk).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                result.putDate(date);
                cancel = false;
                DateDialog.this.dismiss();
            }
        });
    }

    private void setCalendar() {
        tvYear.setText(String.valueOf(date.getYear()));
        if (date.getYear() == min_year)
            adMonth.setMinPos(min_month - 1);
        else
            adMonth.setMinPos(MonthAdapter.NONE_MIN);
        if (date.getYear() == max_year)
            adMonth.setMaxPos(max_month - 1);
        else
            adMonth.setMaxPos(MonthAdapter.NONE_MAX);
        adMonth.setSelect(date.getMonth() - 1);
        adMonth.notifyDataSetChanged();
    }

    @Override
    public void dismiss() {
        if (cancel)
            result.putDate(null);
        super.dismiss();
    }

    public interface Result {
        void putDate(@Nullable DateHelper date); // null for cancel
    }

    class MonthAdapter extends RecyclerView.Adapter<MonthAdapter.ViewHolder> {
        public static final int NONE_MIN = -1, NONE_MAX = 12;
        private List<String> data = new ArrayList<String>();
        private int select, min_pos = 0, max_pos = 11;

        @Override
        public MonthAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(act).inflate(R.layout.item_date, parent, false);
            return new MonthAdapter.ViewHolder(view);
        }

        public void setMinPos(int min_pos) {
            this.min_pos = min_pos;
        }

        public void setMaxPos(int max_pos) {
            this.max_pos = max_pos;
        }

        public void setSelect(int pos) {
            if (pos <= max_pos && pos >= min_pos)
                select = pos;
        }

        public int getSelect() {
            return select;
        }

        @Override
        public void onBindViewHolder(MonthAdapter.ViewHolder holder, int pos) {
            holder.tv.setText(data.get(pos));
            if (pos > max_pos && pos < min_year) {
                holder.bg.setBackgroundDrawable(act.getResources().getDrawable(R.drawable.cell_bg_n));
                holder.bg.setEnabled(false);
            } else if (pos == max_pos || pos == min_pos) {
                if (pos == select)
                    holder.bg.setBackgroundDrawable(act.getResources().getDrawable(R.drawable.cell_bg_kp));
                else
                    holder.bg.setBackgroundDrawable(act.getResources().getDrawable(R.drawable.cell_bg_p));
            } else if (pos == select)
                holder.bg.setBackgroundDrawable(act.getResources().getDrawable(R.drawable.cell_bg_k));
            else
                holder.bg.setBackgroundDrawable(act.getResources().getDrawable(R.drawable.cell_bg_n));


        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        public void addItem(String s) {
            data.add(s);
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            View bg;
            TextView tv;

            ViewHolder(View itemView) {
                super(itemView);
                bg = itemView.findViewById(R.id.cell_bg);
                tv = (TextView) itemView.findViewById(R.id.cell_tv);
            }
        }
    }
}
