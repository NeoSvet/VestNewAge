package ru.neosvet.vestnewage.presenter.view

import ru.neosvet.vestnewage.list.CalendarItem

interface CalendarView {
    fun showLoading()
    fun updateData(date: String, prev: Boolean, next: Boolean)
    fun updateCalendar(data: ArrayList<CalendarItem>)
    fun checkTime(sec: Int, isCurMonth: Boolean)
}