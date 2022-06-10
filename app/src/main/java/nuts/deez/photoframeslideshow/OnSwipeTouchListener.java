package nuts.deez.photoframeslideshow;

import android.content.Context;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

/**
 * Detects left and right swipes across a view.
 */
public class OnSwipeTouchListener implements View.OnTouchListener {

    private final GestureDetector gestureDetector;

    public OnSwipeTouchListener(Context context) {
        gestureDetector = new GestureDetector(context, new GestureListener());
    }

    public void onSwipeLeft() {
    }

    public void onSwipeRight() {
    }

    public void onSwipeUp() {
    }

    public void onSwipeDown() {
    }

    public void onScreenTap() {

    }
    public void onDoubleScreenTap() {

    }

    public boolean onTouch(View v, MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        //Log.i("OnSwipeTouchListener","onTouch - consumed? "+consumed);
        return true;
    }

    private final class GestureListener extends GestureDetector.SimpleOnGestureListener {

        private static final int SWIPE_DISTANCE_THRESHOLD = 100;
        private static final int SWIPE_VELOCITY_THRESHOLD = 100;

        @Override
        public boolean onDown(MotionEvent e) {
            //Log.i("OnSwipeTouchListener","onDown");
            return false;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            //Log.i("OnSwipeTouchListener","Double Tap");
            onDoubleScreenTap();
            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            //Log.i("OnSwipeTouchListener","Single Tap Confirmed");
            onScreenTap();
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            //Log.i("OnSwipeTouchListener","Detecting flings");
            float distanceX = e2.getX() - e1.getX();
            float distanceY = e2.getY() - e1.getY();
            if (Math.abs(distanceX) > Math.abs(distanceY) && Math.abs(distanceX) > SWIPE_DISTANCE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                if (distanceX > 0)
                    onSwipeRight();
                else
                    onSwipeLeft();
                return true;
            }

            if (Math.abs(distanceY) > Math.abs(distanceX) && Math.abs(distanceY) > SWIPE_DISTANCE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                if (distanceY > 0)
                    onSwipeDown();
                else
                    onSwipeUp();
                return true;
            }
            return false;
        }

    }
}
