package com.example.mindtick;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

public class StickyHeaderItemDecoration extends RecyclerView.ItemDecoration {
    private final StickyHeaderInterface listener;
    private int stickyHeaderHeight;

    // ⬅️ сдвиг по X (как у тебя было, 5dp)
    private final float headerOffsetX;
    // ⬆️ лёгкий сдвиг вверх, чтобы убрать “подпрыгивание”
    private final float headerOffsetY;
    // Порог появления sticky, пока реальный хедер ещё чуть виден
    private final int appearThresholdPx;

    public StickyHeaderItemDecoration(StickyHeaderInterface listener) {
        this.listener = listener;
        headerOffsetX = dp(8);
        headerOffsetY = dp(2);       // рисуем выше на 2dp
        appearThresholdPx = (int) dp(2); // ждём, пока реальный заголовок уйдёт на 2dp вверх
    }

    @Override
    public void onDrawOver(Canvas canvas, RecyclerView parent, RecyclerView.State state) {
        super.onDrawOver(canvas, parent, state);

        View topChild = parent.getChildAt(0);
        if (topChild == null) return;

        int topChildPosition = parent.getChildAdapterPosition(topChild);
        if (topChildPosition == RecyclerView.NO_POSITION) return;

        // Если верхний элемент — это хедер и он ещё не ушёл достаточно далеко вверх,
        // не рисуем липкий, чтобы избежать наложения/дёргания.
        boolean isHeader = listener.isHeader(topChildPosition);
        if (isHeader && topChild.getTop() >= -appearThresholdPx) {
            return;
        }

        View currentHeader = getHeaderViewForItem(topChildPosition, parent);
        if (currentHeader == null) return;

        fixLayoutSize(parent, currentHeader);

        int contactPoint = currentHeader.getBottom();
        View childInContact = getChildInContact(parent, contactPoint);

        if (childInContact != null && listener.isHeader(parent.getChildAdapterPosition(childInContact))) {
            moveHeader(canvas, currentHeader, childInContact);
        } else {
            drawHeader(canvas, currentHeader);
        }
    }

    private View getHeaderViewForItem(int itemPosition, RecyclerView parent) {
        int headerPosition = listener.getHeaderPositionForItem(itemPosition);
        if (headerPosition == -1) return null;

        View header = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_category, parent, false);
        listener.bindHeaderData(header, headerPosition);
        return header;
    }

    private void drawHeader(Canvas canvas, View header) {
        canvas.save();
        // рисуем с небольшим сдвигом: чуть левее (как раньше) и чуть выше
        canvas.translate(headerOffsetX, -headerOffsetY);
        header.draw(canvas);
        canvas.restore();
    }

    private void moveHeader(Canvas canvas, View currentHeader, View nextHeader) {
        canvas.save();
        // такой же сдвиг по X и Y, плюс стандартный “подпих” снизу
        float translateY = nextHeader.getTop() - currentHeader.getHeight() - headerOffsetY;
        canvas.translate(headerOffsetX, translateY);
        currentHeader.draw(canvas);
        canvas.restore();
    }

    private View getChildInContact(RecyclerView parent, int contactPoint) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child.getBottom() > contactPoint && child.getTop() <= contactPoint) {
                return child;
            }
        }
        return null;
    }

    private void fixLayoutSize(ViewGroup parent, View view) {
        int widthSpec = View.MeasureSpec.makeMeasureSpec(
                parent.getWidth(), View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(
                parent.getHeight(), View.MeasureSpec.UNSPECIFIED);

        int childWidthSpec = ViewGroup.getChildMeasureSpec(
                widthSpec, parent.getPaddingLeft() + parent.getPaddingRight(), view.getLayoutParams().width);
        int childHeightSpec = ViewGroup.getChildMeasureSpec(
                heightSpec, parent.getPaddingTop() + parent.getPaddingBottom(), view.getLayoutParams().height);

        view.measure(childWidthSpec, childHeightSpec);
        view.layout(0, 0, view.getMeasuredWidth(),
                stickyHeaderHeight = view.getMeasuredHeight());
    }

    private float dp(float v) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, Resources.getSystem().getDisplayMetrics());
    }
}
