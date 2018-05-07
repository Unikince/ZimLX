package org.zimmob.zimlx.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Join;
import android.graphics.Paint.Style;
import android.graphics.Point;
import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.MotionEventCompat;
import android.util.AttributeSet;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import org.jetbrains.annotations.Contract;
import org.zimmob.zimlx.activity.Home;
import org.zimmob.zimlx.util.Tool;

import java.util.ArrayList;
import java.util.List;

import in.championswimmer.sfg.lib.SimpleFingerGestures;

import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

/**
 * Created by saul on 04-25-18.
 * Project ZimLX
 * henriquez.saul@gmail.com
 */
public class CellContainer extends ViewGroup {
    private boolean _animateBackground;
    private final Paint _bgPaint = new Paint(1);
    private boolean _blockTouch;
    private Bitmap _cachedOutlineBitmap;
    private int _cellHeight;
    private int _cellSpanH;
    private int _cellSpanV;
    private int _cellWidth;
    private Rect[][] _cells;
    private Point _currentOutlineCoordinate = new Point(-1, -1);
    private Long _down = 0L;
    @Nullable
    private SimpleFingerGestures _gestures;
    private boolean _hideGrid = true;
    private final Paint _paint = new Paint(1);
    private boolean[][] _occupied;
    @Nullable
    private OnItemRearrangeListener _onItemRearrangeListener;
    private final Paint _outlinePaint = new Paint(1);
    private PeekDirection _peekDirection;
    private Long _peekDownTime = (long) -1;
    private Point _preCoordinate = new Point(-1, -1);
    private Point _startCoordinate = new Point();
    @NonNull
    private final Rect _tempRect = new Rect();

    public enum DragState {
        CurrentNotOccupied, OutOffRange, ItemViewNotFound, CurrentOccupied
    }

    public static final class LayoutParams extends android.view.ViewGroup.LayoutParams {
        private int _x;
        private int _xSpan = 1;
        private int _y;
        private int _ySpan = 1;

        @Contract(pure = true)
        public final int getX() {
            return _x;
        }

        public final void setX(int v) {
            _x = v;
        }

        @Contract(pure = true)
        public final int getY() {
            return _y;
        }

        public final void setY(int v) {
            _y = v;
        }

        public final int getXSpan() {
            return _xSpan;
        }

        public final void setXSpan(int v) {
            _xSpan = v;
        }

        public final int getYSpan() {
            return _ySpan;
        }

        public final void setYSpan(int v) {
            _ySpan = v;
        }

        public LayoutParams(int w, int h, int x, int y) {
            super(w, h);
            _x = x;
            _y = y;
        }

        public LayoutParams(int w, int h, int x, int y, int xSpan, int ySpan) {
            super(w, h);
            _x = x;
            _y = y;
            _xSpan = xSpan;
            _ySpan = ySpan;
        }

        public LayoutParams(int w, int h) {
            super(w, h);
        }
    }

    public interface OnItemRearrangeListener {
        void onItemRearrange(@NonNull Point point, @NonNull Point point2);
    }

    public enum PeekDirection {
        UP, LEFT, RIGHT, DOWN
    }

    public final int getCellWidth() {
        return _cellWidth;
    }

    public final int getCellHeight() {
        return _cellHeight;
    }

    public final int getCellSpanV() {
        return _cellSpanV;
    }

    public final int getCellSpanH() {
        return _cellSpanH;
    }

    public final void setBlockTouch(boolean v) {
        _blockTouch = v;
    }

    public final void setGestures(@Nullable SimpleFingerGestures v) {
        _gestures = v;
    }

    public final void setOnItemRearrangeListener(@Nullable OnItemRearrangeListener v) {
        _onItemRearrangeListener = v;
    }

    @NonNull
    public final List<View> getAllCells() {
        ArrayList views = new ArrayList();
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            views.add(getChildAt(i));
        }
        return views;
    }

    public CellContainer(@NonNull Context c) {
        this(c, null);
    }

    public CellContainer(@NonNull Context c, @Nullable AttributeSet attr) {
        super(c, attr);
        _paint.setStyle(Style.STROKE);
        _paint.setStrokeWidth(2.0f);
        _paint.setStrokeJoin(Join.ROUND);
        _paint.setColor(-1);
        _paint.setAlpha(0);
        _bgPaint.setStyle(Style.FILL);
        _bgPaint.setColor(-1);
        _bgPaint.setAlpha(0);
        _outlinePaint.setColor(-1);
        _outlinePaint.setAlpha(0);
        init();
    }

    public final void setGridSize(int x, int y) {
        _cellSpanV = y;
        _cellSpanH = x;

        _occupied = new boolean[_cellSpanH][_cellSpanV];
        for (int i = 0; i < _cellSpanH; i++) {
            for (int j = 0; j < _cellSpanV; j++) {
                _occupied[i][j] = false;
            }
        }
        requestLayout();
    }

    public final void setHideGrid(boolean hideGrid) {
        _hideGrid = hideGrid;
        invalidate();
    }

    public final void resetOccupiedSpace() {
        if (_cellSpanH > 0 && _cellSpanV > 0) {
            _occupied = new boolean[_cellSpanH][_cellSpanV];
        }
    }

    public void removeAllViews() {
        resetOccupiedSpace();
        super.removeAllViews();
    }

    public final void projectImageOutlineAt(@NonNull Point newCoordinate, @Nullable Bitmap bitmap) {
        _cachedOutlineBitmap = bitmap;
        if (!_currentOutlineCoordinate.equals(newCoordinate)) {
            _outlinePaint.setAlpha(0);
        }
        _currentOutlineCoordinate.set(newCoordinate.x, newCoordinate.y);
        invalidate();
    }

    private final void drawCachedOutlineBitmap(Canvas canvas, Rect cell) {
        if (_cachedOutlineBitmap != null) {
            Bitmap bitmap = _cachedOutlineBitmap;
            float centerX = (float) cell.centerX();
            Bitmap bitmap2 = _cachedOutlineBitmap;
            float f = (float) 2;
            centerX -= ((float) bitmap2.getWidth()) / f;
            float centerY = (float) cell.centerY();
            Bitmap bitmap3 = _cachedOutlineBitmap;
            canvas.drawBitmap(bitmap, centerX, centerY - (((float) bitmap3.getWidth()) / f), _outlinePaint);
        }
    }

    public final void clearCachedOutlineBitmap() {
        _outlinePaint.setAlpha(0);
        _cachedOutlineBitmap = null;
        invalidate();
    }

    @NonNull
    public final DragState peekItemAndSwap(@NonNull DragEvent event, @NonNull Point coordinate) {
        return peekItemAndSwap((int) event.getX(), (int) event.getY(), coordinate);
    }

    @NonNull
    public final DragState peekItemAndSwap(int x, int y, @NonNull Point coordinate) {
        touchPosToCoordinate(coordinate, x, y, 1, 1, false, false);
        if (coordinate.x != -1) {
            if (coordinate.y != -1) {
                DragState dragState;
                if (_startCoordinate == null) {
                    _startCoordinate = coordinate;
                }
                if (!_preCoordinate.equals(coordinate)) {
                    _peekDownTime = (long) -1;
                }
                Long l = _peekDownTime;
                if (l != null && l == -1) {
                    _peekDirection = getPeekDirectionFromCoordinate(_startCoordinate, coordinate);
                    _peekDownTime = System.currentTimeMillis();
                    _preCoordinate = coordinate;

                }
                boolean[][] zArr = _occupied;
                if (zArr[coordinate.x][coordinate.y]) {
                    dragState = DragState.CurrentOccupied;
                } else {
                    dragState = DragState.CurrentNotOccupied;
                }
                return dragState;
            }
        }
        return DragState.OutOffRange;
    }

    private final PeekDirection getPeekDirectionFromCoordinate(Point from, Point to) {
        if (from.y - to.y > 0) {
            return PeekDirection.UP;
        }
        if (from.y - to.y < 0) {
            return PeekDirection.DOWN;
        }
        if (from.x - to.x > 0) {
            return PeekDirection.LEFT;
        }
        if (from.x - to.x < 0) {
            return PeekDirection.RIGHT;
        }
        return null;
    }

    public boolean onTouchEvent(@NonNull MotionEvent event) {
        switch (MotionEventCompat.getActionMasked(event)) {
            case 0:
                _down = System.currentTimeMillis();
                break;
            case 1:
                long currentTimeMillis = System.currentTimeMillis();
                Long l = _down;
                if (currentTimeMillis - l < 260 && _blockTouch) {
                    performClick();
                    break;
                }
            default:
                break;
        }
        if (_blockTouch) {
            return true;
        }
        if (_gestures == null) {
            Tool.print("gestures is null");
            return super.onTouchEvent(event);
        }
        try {
            SimpleFingerGestures simpleFingerGestures = _gestures;
            simpleFingerGestures.onTouch(this, event);
        } catch (Exception ignored) {
        }
        return super.onTouchEvent(event);
    }

    public boolean onInterceptTouchEvent(@NonNull MotionEvent ev) {
        return _blockTouch || super.onInterceptTouchEvent(ev);
    }

    public void init() {
        setWillNotDraw(false);
    }

    public final void animateBackgroundShow() {
        _animateBackground = true;
        invalidate();
    }

    public final void animateBackgroundHide() {
        _animateBackground = false;
        invalidate();
    }

    @Nullable
    public final Point findFreeSpace() {
        boolean[][] zArr = _occupied;
        int length = zArr[0].length;
        for (int y = 0; y < length; y++) {
            boolean[][] zArr2 = _occupied;
            int length2 = zArr2.length;
            for (int x = 0; x < length2; x++) {
                boolean[][] zArr3 = _occupied;
                if (!zArr3[x][y]) {
                    return new Point(x, y);
                }
            }
        }
        return null;
    }

    @Nullable
    public final Point findFreeSpace(int spanX, int spanY) {
        boolean[][] zArr = _occupied;
        int length = zArr[0].length;
        int y = 0;
        while (y < length) {
            boolean[][] zArr2 = _occupied;
            int length2 = zArr2.length;
            int x = 0;
            while (x < length2) {
                boolean[][] zArr3 = _occupied;
                if (!zArr3[x][y] && !checkOccupied(new Point(x, y), spanX, spanY)) {
                    return new Point(x, y);
                }
                x++;
            }
            y++;
        }
        return null;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawRect(0f, 0f, getWidth(), getHeight(), _bgPaint);

        if (_cells == null)
            return;

        float s = 7f;
        for (int x = 0; x < _cellSpanH; x++) {
            for (int y = 0; y < _cellSpanV; y++) {
                if (x >= _cells.length || y >= _cells[0].length)
                    continue;

                Rect cell = _cells[x][y];

                canvas.save();
                canvas.rotate(45f, cell.left, cell.top);
                canvas.drawRect(cell.left - s, cell.top - s, cell.left + s, cell.top + s, _paint);
                canvas.restore();

                canvas.save();
                canvas.rotate(45f, cell.left, cell.bottom);
                canvas.drawRect(cell.left - s, cell.bottom - s, cell.left + s, cell.bottom + s, _paint);
                canvas.restore();

                canvas.save();
                canvas.rotate(45f, cell.right, cell.top);
                canvas.drawRect(cell.right - s, cell.top - s, cell.right + s, cell.top + s, _paint);
                canvas.restore();

                canvas.save();
                canvas.rotate(45f, cell.right, cell.bottom);
                canvas.drawRect(cell.right - s, cell.bottom - s, cell.right + s, cell.bottom + s, _paint);
                canvas.restore();
            }
        }

        //Animating alpha and drawing projected image
        Home home = Home.Companion.getLauncher();
        if (home != null && home.getDragNDropView().getDragExceedThreshold() && _currentOutlineCoordinate.x != -1 && _currentOutlineCoordinate.y != -1) {
            if (_outlinePaint.getAlpha() != 160)
                _outlinePaint.setAlpha(Math.min(_outlinePaint.getAlpha() + 20, 160));
            drawCachedOutlineBitmap(canvas, _cells[_currentOutlineCoordinate.x][_currentOutlineCoordinate.y]);

            if (_outlinePaint.getAlpha() <= 160)
                invalidate();
        }

        //Animating alpha
        if (_hideGrid && _paint.getAlpha() != 0) {
            _paint.setAlpha(Math.max(_paint.getAlpha() - 20, 0));
            invalidate();
        } else if (!_hideGrid && _paint.getAlpha() != 255) {
            _paint.setAlpha(Math.min(_paint.getAlpha() + 20, 255));
            invalidate();
        }

        //Animating alpha
        if (!_animateBackground && _bgPaint.getAlpha() != 0) {
            _bgPaint.setAlpha(Math.max(_bgPaint.getAlpha() - 10, 0));
            invalidate();
        } else if (_animateBackground && _bgPaint.getAlpha() != 100) {
            _bgPaint.setAlpha(Math.min(_bgPaint.getAlpha() + 10, 100));
            invalidate();
        }
    }

    public void addView(@NonNull View child) {
        LayoutParams lp = (CellContainer.LayoutParams) child.getLayoutParams();
        setOccupied(true, lp);
        super.addView(child);
    }

    public void removeView(@NonNull View view) {
        LayoutParams lp = (CellContainer.LayoutParams) view.getLayoutParams();
        setOccupied(false, lp);
        super.removeView(view);
    }

    public final void addViewToGrid(@NonNull View view, int x, int y, int xSpan, int ySpan) {
        view.setLayoutParams(new LayoutParams(-2, -2, x, y, xSpan, ySpan));
        addView(view);
    }

    public final void addViewToGrid(@NonNull View view) {
        addView(view);
    }

    public final void setOccupied(boolean b, @NonNull LayoutParams lp) {
        Tool.print("Setting");
        int x = lp.getX() + lp.getXSpan();
        for (int x2 = lp.getX(); x2 < x; x2++) {
            int y = lp.getY() + lp.getYSpan();
            for (int y2 = lp.getY(); y2 < y; y2++) {
                Object[] objArr = new Object[2];
                String stringBuilder = "Setting ok (" +
                        String.valueOf(b);
                objArr[0] = stringBuilder;
                objArr[1] = ")";
                Tool.print(objArr);
                boolean[][] zArr = _occupied;
                zArr[x2][y2] = b;
            }
        }
    }

    public final boolean checkOccupied(@NonNull Point start, int spanX, int spanY) {
        int i = start.x + spanX;
        boolean[][] zArr = _occupied;
        if (i <= zArr.length) {
            i = start.y + spanY;
            zArr = _occupied;
            if (i <= zArr[0].length) {
                int i2 = start.y + spanY;
                for (i = start.y; i < i2; i++) {
                    int i3 = start.x + spanX;
                    for (int x = start.x; x < i3; x++) {
                        boolean[][] zArr2 = _occupied;
                        if (zArr2[x][i]) {
                            return true;
                        }
                    }
                }
                return false;
            }
        }
        return true;
    }

    @Nullable
    public final View coordinateToChildView(@Nullable Point pos) {
        if (pos == null) {
            return null;
        }
        for (int i = 0; i < getChildCount(); i++) {
            LayoutParams lp = (LayoutParams) getChildAt(i).getLayoutParams();
            if (pos.x >= lp._x && pos.y >= lp._y && pos.x < lp._x + lp._xSpan && pos.y < lp._y + lp._ySpan) {
                return getChildAt(i);
            }
        }
        return null;
    }

    @Nullable
    public final LayoutParams coordinateToLayoutParams(int mX, int mY, int xSpan, int ySpan) {
        Point pos = new Point();
        touchPosToCoordinate(pos, mX, mY, xSpan, ySpan, true);
        return !pos.equals(-1, -1) ? new LayoutParams(WRAP_CONTENT, WRAP_CONTENT, pos.x, pos.y, xSpan, ySpan) : null;
    }

    public void touchPosToCoordinate(@NonNull Point coordinate, int mX, int mY, int xSpan, int ySpan, boolean checkAvailability) {
        touchPosToCoordinate(coordinate, mX, mY, xSpan, ySpan, checkAvailability, false);
    }


    public final void touchPosToCoordinate(@NonNull Point coordinate, int mX, int mY, int xSpan, int ySpan, boolean checkAvailability, boolean checkBoundary) {
        if (_cells == null) {
            coordinate.set(-1, -1);
            return;
        }


        mX -= (xSpan - 1) * _cellWidth / 2f;
        mY -= (ySpan - 1) * _cellHeight / 2f;

        int x = 0;
        while (x < _cellSpanH) {
            int y = 0;
            while (y < _cellSpanV) {
                Rect cell = _cells[x][y];
                if (mY >= cell.top && mY <= cell.bottom && mX >= cell.left && mX <= cell.right) {
                    if (checkAvailability) {
                        if (_occupied[x][y]) {
                            coordinate.set(-1, -1);
                            return;
                        }

                        int dx = x + xSpan - 1;
                        int dy = y + ySpan - 1;

                        if (dx >= _cellSpanH - 1) {
                            dx = _cellSpanH - 1;
                            x = dx + 1 - xSpan;
                        }
                        if (dy >= _cellSpanV - 1) {
                            dy = _cellSpanV - 1;
                            y = dy + 1 - ySpan;
                        }

                        for (int x2 = x; x2 < x + xSpan; x2++) {
                            for (int y2 = y; y2 < y + ySpan; y2++) {
                                if (_occupied[x2][y2]) {
                                    coordinate.set(-1, -1);
                                    return;
                                }
                            }
                        }
                    }
                    if (checkBoundary) {
                        Rect offsetCell = new Rect(cell);
                        int dp2 = Tool.toPx(6);
                        offsetCell.inset(dp2, dp2);
                        if (mY >= offsetCell.top && mY <= offsetCell.bottom && mX >= offsetCell.left && mX <= offsetCell.right) {
                            coordinate.set(-1, -1);
                            return;
                        }
                    }
                    coordinate.set(x, y);
                    return;
                }
                y++;
            }
            x++;
        }
    }

    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = ((r - l) - getPaddingLeft()) - getPaddingRight();
        int height = ((b - t) - getPaddingTop()) - getPaddingBottom();
        if (_cellSpanH == 0) {
            _cellSpanH = 1;
        }
        if (_cellSpanV == 0) {
            _cellSpanV = 1;
        }
        _cellWidth = width / _cellSpanH;
        _cellHeight = height / _cellSpanV;
        initCellInfo(getPaddingLeft(), getPaddingTop(), width - getPaddingRight(), height - getPaddingBottom());
        int count = getChildCount();
        if (_cells != null) {
            int i = 0;
            while (i < count) {
                View child = getChildAt(i);
                if (child.getVisibility() != View.GONE) {
                    LayoutParams lp = (LayoutParams) child.getLayoutParams();
                    child.measure(MeasureSpec.makeMeasureSpec(lp.getXSpan() * _cellWidth, View.MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(lp.getYSpan() * _cellHeight, View.MeasureSpec.EXACTLY));
                    Rect[][] rectArr = _cells;
                    Rect upRect = rectArr[lp.getX()][lp.getY()];
                    Rect downRect = _tempRect;
                    if ((lp.getX() + lp.getXSpan()) - 1 < _cellSpanH && (lp.getY() + lp.getYSpan()) - 1 < _cellSpanV) {
                        Rect[][] rectArr2 = _cells;
                        downRect = rectArr2[(lp.getX() + lp.getXSpan()) - 1][(lp.getY() + lp.getYSpan()) - 1];
                    }
                    if (lp.getXSpan() == 1 && lp.getYSpan() == 1) {
                        child.layout(upRect.left, upRect.top, upRect.right, upRect.bottom);
                    } else if (lp.getXSpan() > 1 && lp.getYSpan() > 1) {
                        child.layout(upRect.left, upRect.top, downRect.right, downRect.bottom);
                    } else if (lp.getXSpan() > 1) {
                        child.layout(upRect.left, upRect.top, downRect.right, upRect.bottom);
                    } else if (lp.getYSpan() > 1) {
                        child.layout(upRect.left, upRect.top, upRect.right, downRect.bottom);
                    }
                }
                i++;
            }
        }
    }

    private void initCellInfo(int l, int t, int r, int b) {
        _cells = new Rect[_cellSpanH][_cellSpanV];

        int curLeft = l;
        int curTop = t;
        int curRight = l + _cellWidth;
        int curBottom = t + _cellHeight;

        for (int i = 0; i < _cellSpanH; i++) {
            if (i != 0) {
                curLeft += _cellWidth;
                curRight += _cellWidth;
            }
            for (int j = 0; j < _cellSpanV; j++) {
                if (j != 0) {
                    curTop += _cellHeight;
                    curBottom += _cellHeight;
                }

                Rect rect = new Rect(curLeft, curTop, curRight, curBottom);
                _cells[i][j] = rect;
            }
            curTop = t;
            curBottom = t + _cellHeight;
        }
    }
}