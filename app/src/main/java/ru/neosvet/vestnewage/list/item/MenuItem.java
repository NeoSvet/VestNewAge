package ru.neosvet.vestnewage.list.item;

public class MenuItem {
    private final String title;
    private int image;
    private boolean select = false;

    public boolean isSelect() {
        return select;
    }

    public void setSelect(boolean select) {
        this.select = select;
    }

    public void setImage(int image) {
        this.image = image;
    }

    public int getImage() {
        return image;
    }

    public String getTitle() {
        return title;
    }

    public MenuItem(int image, String title) {
        this.image = image;
        this.title = title;

    }
}
