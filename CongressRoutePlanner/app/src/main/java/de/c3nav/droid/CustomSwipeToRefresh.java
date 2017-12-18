package de.c3nav.droid;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.AttributeSet;
import android.view.MotionEvent;


public class CustomSwipeToRefresh extends SwipeRefreshLayout {
    public int start_y;

    public CustomSwipeToRefresh(@NonNull Context context) {
        super(context);
    }

    public CustomSwipeToRefresh(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        final int action = event.getAction();
        final int pointer_index = (action & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
        int pid = event.getPointerId(pointer_index);

        int x = (int) event.getX();
        int y = (int) event.getY();

        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                start_y = y;
                break;

            case MotionEvent.ACTION_MOVE:
                if (start_y > 150) {
                    return false;
                }
                break;

            case MotionEvent.ACTION_UP:
                start_y = -1;
                break;
        }
        return super.onInterceptTouchEvent(event);
    }
}
