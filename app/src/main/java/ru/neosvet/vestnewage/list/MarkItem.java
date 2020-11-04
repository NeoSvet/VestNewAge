package ru.neosvet.vestnewage.list;

public class MarkItem {
    private String title, data, des, place;
    private final int id;
    private boolean select = false;

    public MarkItem(String title, int id, String data) {
        this.title = title;
        this.id = id;
        this.data = data;
    }

    public int getId() {
        return id;
    }

    public String[] getStrId() {
        return new String[]{String.valueOf(id)};
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getPlace() {
        return place;
    }

    public void setPlace(String place) {
        this.place = place;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDes() {
        if (des == null) return "";
        return des;
    }

    public void setDes(String des) {
        this.des = des;
    }

    public boolean isSelect() {
        return select;
    }

    public void setSelect(boolean select) {
        this.select = select;
    }
}
