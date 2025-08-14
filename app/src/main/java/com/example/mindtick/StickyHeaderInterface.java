package com.example.mindtick;

import android.view.View;

public interface StickyHeaderInterface {
    /**
     * Возвращает позицию заголовка для данной позиции элемента.
     */
    int getHeaderPositionForItem(int itemPosition);

    /**
     * Bind данных заголовка.
     */
    void bindHeaderData(View header, int headerPosition);

    /**
     * Проверяет, является ли позиция заголовком.
     */
    boolean isHeader(int itemPosition);
}