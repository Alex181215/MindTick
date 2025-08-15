package com.example.mindtick;

import android.graphics.Canvas;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mindtick.StickyHeaderInterface;
import android.content.res.Resources;
import android.util.Log;
import android.util.TypedValue;

public class StickyHeaderItemDecoration extends RecyclerView.ItemDecoration {
    private final StickyHeaderInterface listener;
    private int stickyHeaderHeight;
    private final float headerOffset;

    public StickyHeaderItemDecoration(StickyHeaderInterface listener) {
        this.listener = listener;
        // Рассчитываем 5dp в px для сдвига влево
        headerOffset = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, Resources.getSystem().getDisplayMetrics());
    }

    @Override
    public void onDrawOver(Canvas canvas, RecyclerView parent, RecyclerView.State state) {
        super.onDrawOver(canvas, parent, state);

        View topChild = parent.getChildAt(0);
        if (topChild == null) {
            Log.d("StickyHeader", "No top child, skipping draw");
            return;
        }

        int topChildPosition = parent.getChildAdapterPosition(topChild);
        if (topChildPosition == RecyclerView.NO_POSITION) {
            Log.d("StickyHeader", "No position for top child, skipping draw");
            return;
        }

        // Проверяем, является ли topChild самим заголовком и не ушёл ли он вверх
        boolean isHeader = listener.isHeader(topChildPosition);
        if (isHeader && topChild.getTop() >= 0) {
            Log.d("StickyHeader", "Top child is header and fully visible (top >= 0), skipping sticky draw to avoid duplication");
            return; // Не рисуем sticky, чтобы избежать наложения
        }

        View currentHeader = getHeaderViewForItem(topChildPosition, parent);
        if (currentHeader == null) {
            Log.d("StickyHeader", "No header view for position " + topChildPosition);
            return;
        }

        fixLayoutSize(parent, currentHeader);

        // Очищаем область заголовка перед рисованием
        canvas.save();
        canvas.clipRect(0, 0, parent.getWidth(), stickyHeaderHeight);
        //canvas.drawColor(ContextCompat.getColor(parent.getContext(), R.color.your_background_color)); // Замени на цвет фона
        canvas.restore();

        int contactPoint = currentHeader.getBottom();
        View childInContact = getChildInContact(parent, contactPoint);
        if (childInContact == null) {
            drawHeader(canvas, currentHeader);
            return;
        }

        if (listener.isHeader(parent.getChildAdapterPosition(childInContact))) {
            moveHeader(canvas, currentHeader, childInContact);
        } else {
            drawHeader(canvas, currentHeader);
        }
    }

    private View getHeaderViewForItem(int itemPosition, RecyclerView parent) {
        int headerPosition = listener.getHeaderPositionForItem(itemPosition);
        if (headerPosition == -1) {
            Log.w("StickyHeader", "No header found for position " + itemPosition);
            return null;
        }

        View header = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category, parent, false);
        listener.bindHeaderData(header, headerPosition);

        // Логируем текст заголовка после биндинга
        TextView tvCategory = header.findViewById(R.id.tvCategory);
        String categoryText = tvCategory.getText().toString();
        Log.d("StickyHeader", "Header view created for position " + headerPosition + ": [" + categoryText + "]");

        return header;
    }

    private void drawHeader(Canvas canvas, View header) {
        canvas.save();
        canvas.translate(headerOffset, 0f); // Можно добавить небольшой Y-offset, если нужно: canvas.translate(headerOffset, 2f);
        TextView tvCategory = header.findViewById(R.id.tvCategory);
        Log.d("StickyHeader", "Drawing header with text: [" + tvCategory.getText() + "]");
        header.draw(canvas);
        canvas.restore();
    }

    private void moveHeader(Canvas canvas, View currentHeader, View nextHeader) {
        canvas.save();
        canvas.translate(headerOffset, nextHeader.getTop() - currentHeader.getHeight());
        TextView tvCategory = currentHeader.findViewById(R.id.tvCategory);
        Log.d("StickyHeader", "Moving header with text: [" + tvCategory.getText() + "]");
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
        int widthSpec = View.MeasureSpec.makeMeasureSpec(parent.getWidth(), View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(parent.getHeight(), View.MeasureSpec.UNSPECIFIED);

        int childWidthSpec = ViewGroup.getChildMeasureSpec(widthSpec, parent.getPaddingLeft() + parent.getPaddingRight(), view.getLayoutParams().width);
        int childHeightSpec = ViewGroup.getChildMeasureSpec(heightSpec, parent.getPaddingTop() + parent.getPaddingBottom(), view.getLayoutParams().height);

        view.measure(childWidthSpec, childHeightSpec);
        view.layout(0, 0, view.getMeasuredWidth(), stickyHeaderHeight = view.getMeasuredHeight());
    }
}