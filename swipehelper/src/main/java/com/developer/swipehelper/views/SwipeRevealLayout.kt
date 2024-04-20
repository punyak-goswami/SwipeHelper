package com.developer.swipehelper.views

import android.R.attr
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IntDef
import androidx.core.view.ViewCompat
import androidx.customview.widget.ViewDragHelper
import com.developer.swipehelper.R
import com.developer.swipehelper.annotations.EdgeGravity
import com.developer.swipehelper.utils.SwipeHelper


class SwipeRevealLayout(context: Context, attributeSet: AttributeSet) :
    ViewGroup(context, attributeSet) {

    private var mDragHelper: ViewDragHelper

    private val mRightSwipeConstraintId: Int
    private val mLeftSwipeConstraintId: Int

    private var mAllowOverSwipe: Boolean = true

    private var mSwipeView: View? = null
    private var mSwipeViewId: Int
    private var mainViewRectOpenFromRight: Rect = Rect()
    private var mainViewRectOpenFromLeft: Rect = Rect()
    private var mainViewRectClose: Rect = Rect()

    @IntDef(
        value = [SwipeFromLeftEdge, SwipeFromRightEdge, SwipeFromBothEdge],
        flag = true
    )
    @Retention(AnnotationRetention.SOURCE)
    annotation class SwipeMode

    @SwipeMode
    private val mSwipeMode: Int

    @EdgeGravity
    private var mOpenedEdge: Int? = null

    private var mSwipeViewListener: SwipeViewListener? = null

    /**
     * Listener for monitoring events about opening and closing Swipe view.
     */
    interface SwipeViewListener {
        /**
         * Called when view is swiped to open or opened by calling [open].
         * @param edge The edge from which the swipe view is opened. Value will be one of [LEFT] or [RIGHT]
         *
         * @param
         */
        fun onSwipeViewOpened(@EdgeGravity edge: Int)

        /**
         * Called when view is swiped to close or by calling [close].
         */
        fun onSwipeViewClosed()
    }

    init {
        context.theme.obtainStyledAttributes(attributeSet, R.styleable.SwipeRevealLayout, 0, 0)
            .apply {
                mSwipeViewId =
                    getResourceId(R.styleable.SwipeRevealLayout_swipeView, -1)
                mAllowOverSwipe = getBoolean(R.styleable.SwipeRevealLayout_allowOverSwipe, true)
                mRightSwipeConstraintId =
                    getResourceId(R.styleable.SwipeRevealLayout_setRight_openPosition_toLeftOf, -1)
                mLeftSwipeConstraintId =
                    getResourceId(R.styleable.SwipeRevealLayout_setLeft_openPosition_toRightOf, -1)
                mSwipeMode =
                    getInt(R.styleable.SwipeRevealLayout_swipeRevealMode, SwipeFromRightEdge)
            }

        val dragCallback = ViewDragCallback()
        mDragHelper = ViewDragHelper.create(this, 1f, dragCallback)
        val density = resources.displayMetrics.density
        val minVel = MIN_FLING_VELOCITY * density
        mDragHelper.minVelocity = minVel
    }

    /**
     * Set a listener to be notified of swipe view events.
     * @param listener Listener to notify when swipe events occurs on swipe view
     */
    fun setSwipeViewListener(listener: SwipeViewListener?) {
        mSwipeViewListener = listener
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val parentLeft = paddingLeft
        val parentRight = r - l - paddingRight
        val parentTop = paddingTop
        val parentBottom = b - t - paddingBottom
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val childWidth = child.measuredWidth
            val childHeight = child.measuredHeight

            if (child.visibility == View.GONE) {
                continue
            }

            val lp = child.layoutParams as LayoutParams

            var childLeft: Int
            var childTop: Int

            var childGravity = lp.gravity
            if (childGravity == -1) {
                childGravity = DEFAULT_CHILD_GRAVITY
            }
            val absoluteGravity = Gravity.getAbsoluteGravity(childGravity, layoutDirection)

            when (absoluteGravity and Gravity.HORIZONTAL_GRAVITY_MASK) {
                Gravity.CENTER_HORIZONTAL -> {
                    childLeft =
                        parentLeft + (parentRight - parentLeft - childWidth) / 2 + lp.leftMargin - lp.rightMargin
                }

                Gravity.RIGHT -> {
                    childLeft = parentRight - childWidth - lp.rightMargin
                }

                Gravity.LEFT -> {
                    childLeft = parentLeft + lp.leftMargin
                }

                else -> {
                    childLeft = parentLeft + lp.leftMargin
                }
            }

            when (childGravity and Gravity.VERTICAL_GRAVITY_MASK) {
                Gravity.CENTER_VERTICAL -> {
                    childTop =
                        parentTop + (parentBottom - parentTop - childHeight) / 2 + lp.topMargin - lp.bottomMargin
                }

                Gravity.BOTTOM -> {
                    childTop = parentBottom - childHeight - lp.bottomMargin
                }

                Gravity.TOP -> {
                    childTop = parentTop + lp.topMargin
                }

                else -> {
                    childTop = parentTop + lp.topMargin
                }
            }

            child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight)

        }
        mSwipeView = findViewById(mSwipeViewId)
        setMainViewRects()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        var largestChildWidth = 0
        var largestChildHeight = 0
        var childState = 0
        for (i in 0 until childCount) { //Find width and height of largest child
            val child = getChildAt(i)
            if (child.visibility == View.GONE) {
                continue
            }
            val lp = child.layoutParams as LayoutParams
            measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0)
            largestChildWidth =
                child.measuredWidth.coerceAtLeast(largestChildWidth + lp.leftMargin + lp.rightMargin)
            largestChildHeight =
                child.measuredHeight.coerceAtLeast(largestChildHeight + lp.topMargin + lp.bottomMargin)
            childState = combineMeasuredStates(childState, child.measuredState)
        }

        largestChildWidth += paddingLeft + paddingRight
        largestChildHeight += paddingTop + paddingBottom

        largestChildWidth = largestChildWidth.coerceAtLeast(suggestedMinimumWidth)
        largestChildHeight = largestChildHeight.coerceAtLeast(suggestedMinimumHeight)

        setMeasuredDimension(
            resolveSizeAndState(largestChildWidth, widthMeasureSpec, childState),
            resolveSizeAndState(
                largestChildHeight,
                heightMeasureSpec,
                childState shl MEASURED_HEIGHT_STATE_SHIFT
            )
        )

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val lp = child.layoutParams as LayoutParams

            val childWidthMeasureSpec = if (lp.width == ViewGroup.LayoutParams.MATCH_PARENT) {
                val width =
                    measuredWidth - paddingLeft - paddingRight - lp.leftMargin - lp.rightMargin
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
            } else {
                getChildMeasureSpec(
                    widthMeasureSpec,
                    paddingLeft + paddingRight + lp.leftMargin + lp.rightMargin,
                    lp.width
                )
            }

            val childHeightMeasureSpec = if (lp.height == ViewGroup.LayoutParams.MATCH_PARENT) {
                val height =
                    measuredHeight - paddingTop - paddingBottom - lp.topMargin - lp.bottomMargin
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
            } else {
                getChildMeasureSpec(
                    heightMeasureSpec,
                    paddingTop + paddingBottom + lp.topMargin + lp.bottomMargin,
                    lp.height
                )
            }
            child.measure(childWidthMeasureSpec, childHeightMeasureSpec)
        }

    }


    private fun setMainViewRects() {
        val rightConstraintView = findViewById<View>(mRightSwipeConstraintId)
        val leftConstraintView = findViewById<View>(mLeftSwipeConstraintId)
        val rightSwipeLimit = rightConstraintView?.left ?: 0
        val leftSwipeLimit = leftConstraintView?.right ?: 0
        mSwipeView?.let { mainView ->
            mainViewRectClose.apply {
                left = mainView.left
                top = mainView.top
                right = mainView.right
                bottom = mainView.bottom
            }

            mainViewRectOpenFromRight.apply {
                left = rightSwipeLimit - mainView.width
                top = mainView.top
                right = rightSwipeLimit
                bottom = mainView.bottom
            }

            mainViewRectOpenFromLeft.apply {
                left = leftSwipeLimit
                top = mainView.top
                right = mainView.width + leftSwipeLimit
                bottom = mainView.bottom
            }

        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        return ev?.let { mDragHelper.shouldInterceptTouchEvent(it) } ?: false
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event?.let {
            mDragHelper.processTouchEvent(it)
        }
        return true
    }

    override fun computeScroll() {
        if (mDragHelper.continueSettling(true)) {
            ViewCompat.postInvalidateOnAnimation(this)
        }
    }

    private inner class ViewDragCallback : ViewDragHelper.Callback() {
        override fun tryCaptureView(child: View, pointerId: Int): Boolean {
            return child == mSwipeView
        }

        override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
            super.onViewReleased(releasedChild, xvel, yvel)
            if (releasedChild == mSwipeView) {
                when (mSwipeMode) {
                    SwipeFromRightEdge -> {
                        if (mOpenedEdge == SwipeHelper.RIGHT && xvel > 0) {
                            close()
                        } else if (releasedChild.right <= (mainViewRectOpenFromRight.right + (mainViewRectClose.right - mainViewRectOpenFromRight.right) / 2) || xvel < 0) {
                            //When swiping view has crossed 50% of swipe limit
                            openSwipeViewFromRight()
                        } else {
                            close()
                        }
                    }

                    SwipeFromLeftEdge -> {
                        if (mOpenedEdge == SwipeHelper.LEFT && xvel < 0) {
                            close()
                        } else if (releasedChild.left >= (mainViewRectClose.left + (mainViewRectOpenFromLeft.left - mainViewRectClose.left) / 2) || xvel > 0) {
                            //When swiping view has crossed 50% of swipe limit
                            openSwipeViewFromLeft()
                        } else {
                            close()
                        }
                    }

                    SwipeFromBothEdge -> {
                        if ((mOpenedEdge == SwipeHelper.LEFT && xvel < 0) || (mOpenedEdge == SwipeHelper.RIGHT && xvel > 0)) {
                            close()
                        } else {
                            if (releasedChild.left >= (mainViewRectClose.left + (mainViewRectOpenFromLeft.left - mainViewRectClose.left) / 2) || xvel > 0) {//Swiping towards left side
                                openSwipeViewFromLeft()
                            } else if (releasedChild.right <= (mainViewRectOpenFromRight.right + (mainViewRectClose.right - mainViewRectOpenFromRight.right) / 2) || xvel < 0) {//Swiping towards right side
                                openSwipeViewFromRight()
                            } else {
                                close()
                            }
                        }
                    }
                }
            }

            ViewCompat.postInvalidateOnAnimation(this@SwipeRevealLayout)
        }

        override fun clampViewPositionHorizontal(child: View, left: Int, dx: Int): Int {
            when (mSwipeMode) {
                SwipeFromRightEdge -> {
                    return if (mAllowOverSwipe) {
                        if (left > mainViewRectClose.left) {//When swiping in opposite direction(right side)
                            mainViewRectClose.left
                        } else {
                            left
                        }
                    } else {
                        if (left < mainViewRectOpenFromRight.left) { //When swiped till end of provided view
                            mainViewRectOpenFromRight.left
                        } else if (left > mainViewRectClose.left) { //When main view is at rest position, do not allow it to swipe right
                            mainViewRectClose.left
                        } else {
                            left
                        }
                    }
                }

                SwipeFromLeftEdge -> {
                    return if (mAllowOverSwipe) {
                        if (left <= mainViewRectClose.left) { //When swiping left and view is in original position
                            mainViewRectClose.left
                        } else {
                            left
                        }
                    } else {
                        if (left > mainViewRectOpenFromLeft.left) { //When swiped till end of provided view
                            mainViewRectOpenFromLeft.left
                        } else if (left <= mainViewRectClose.left) { //When main view is at rest position, do not allow it to swipe right
                            mainViewRectClose.left
                        } else {
                            left
                        }
                    }
                }

                SwipeFromBothEdge -> {
                    return if (mAllowOverSwipe) {
                        left
                    } else {
                        if (left >= mainViewRectOpenFromLeft.left) {
                            mainViewRectOpenFromLeft.left
                        } else if (left <= mainViewRectOpenFromRight.left) {
                            mainViewRectOpenFromRight.left
                        } else {
                            left
                        }
                    }
                }

                else -> {
                    return 0
                }
            }
        }

        override fun getViewHorizontalDragRange(child: View): Int {
            val range = measuredWidth
            return range
        }

        override fun onViewPositionChanged(
            changedView: View,
            left: Int,
            top: Int,
            dx: Int,
            dy: Int
        ) {
            super.onViewPositionChanged(changedView, attr.left, attr.top, dx, dy)
            if (dy != 0) {
                mSwipeView?.offsetTopAndBottom(-dy)
            }
        }
    }

    private fun openSwipeViewFromLeft() {
        mSwipeView?.let {
            mDragHelper.smoothSlideViewTo(
                it,
                mainViewRectOpenFromLeft.left,
                mainViewRectOpenFromLeft.top
            )
            if (!isOpen()) {
                mOpenedEdge = SwipeHelper.LEFT
                mSwipeViewListener?.onSwipeViewOpened(SwipeHelper.LEFT)
            }
            ViewCompat.postInvalidateOnAnimation(this)
        }
    }

    private fun openSwipeViewFromRight() {
        mSwipeView?.let {
            mDragHelper.smoothSlideViewTo(
                it,
                mainViewRectOpenFromRight.left,
                mainViewRectOpenFromRight.top
            )
            if (!isOpen()) {//Notify listeners only if view was closed before
                mOpenedEdge = SwipeHelper.RIGHT
                mSwipeViewListener?.onSwipeViewOpened(SwipeHelper.RIGHT)
            }
            ViewCompat.postInvalidateOnAnimation(this)
        }
    }

    /**
     * Closes the swipe view to set it in its original position.
     */
    fun close() {
        mSwipeView?.let {
            mDragHelper.smoothSlideViewTo(
                it,
                mainViewRectClose.left,
                mainViewRectClose.top
            )
            if (isOpen()) {//Notify listeners only if view was open before
                mSwipeViewListener?.onSwipeViewClosed()
                mOpenedEdge = null
            }
            ViewCompat.postInvalidateOnAnimation(this)
        }
    }

    /**
     * Opens the swipe view from left edge if swipeRevealMode is set to openFromLeftEdge or opens from right edge if swipeRevealMode is set to openFromRightEdge.
     * If swipeRevealMode is set to openFromBothEdge, this will open swipe view from right edge by default. If you want to open from left edge,
     * use overload of this method by passing the edge.
     */
    fun open() {
        when (mSwipeMode) {
            SwipeFromRightEdge -> {
                openSwipeViewFromRight()
            }

            SwipeFromLeftEdge -> {
                openSwipeViewFromLeft()
            }

            SwipeFromBothEdge -> {
                openSwipeViewFromRight()
            }
        }
    }

    /**
     * Opens swipe view from given edge. This will not work when swipeRevealMode is set to openFromRightEdge and we try to open from left edge or when
     * swipeRevealMode is set to openFromLeftEdge and we try to open from right edge.
     * @param edge [SwipeHelper.RIGHT] to open swipe view from right side or [SwipeHelper.LEFT] to open swipe view from left side.
     */
    fun open(@EdgeGravity edge: Int) {
        when (mSwipeMode) {
            SwipeFromRightEdge -> {
                if (edge == SwipeHelper.RIGHT) {
                    openSwipeViewFromRight()
                }
            }

            SwipeFromLeftEdge -> {
                if (edge == SwipeHelper.LEFT) {
                    openSwipeViewFromLeft()
                }
            }

            SwipeFromBothEdge -> {
                if (edge == SwipeHelper.LEFT) {
                    openSwipeViewFromLeft()
                } else if (edge == SwipeHelper.RIGHT) {
                    openSwipeViewFromRight()
                }
            }
        }
    }

    /**
     * Checks if swipe view is opened or not.
     * @return true if swipe view is opened, false otherwise.
     */
    fun isOpen(): Boolean {
        return mOpenedEdge != null
    }

    /**
     * Checks if swipe view is opened from edge or not.
     * @param edge [SwipeHelper.RIGHT] to check if swipe view is open from right side or [SwipeHelper.LEFT] to check if swipe view is open from left side.
     * @return true if swipe view is opened from edge, false otherwise.
     */
    fun isOpen(@EdgeGravity edge: Int): Boolean {
        return edge == mOpenedEdge
    }

    override fun generateLayoutParams(attrs: AttributeSet): ViewGroup.LayoutParams {
        return LayoutParams(context, attrs)
    }

    override fun generateLayoutParams(layoutParams: ViewGroup.LayoutParams): ViewGroup.LayoutParams {
        return LayoutParams(layoutParams)
    }

    override fun generateDefaultLayoutParams(): ViewGroup.LayoutParams {
        return LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun checkLayoutParams(p: ViewGroup.LayoutParams?): Boolean {
        return p is LayoutParams
    }

    class LayoutParams : MarginLayoutParams {
        var gravity = -1

        constructor(c: Context, attrs: AttributeSet) : super(c, attrs) {
            val a = c.obtainStyledAttributes(attrs, R.styleable.SwipeRevealLayout_Layout)
            gravity = a.getInt(R.styleable.SwipeRevealLayout_Layout_android_layout_gravity, -1)
            a.recycle()
        }

        constructor(width: Int, height: Int) : super(width, height)

        constructor(source: ViewGroup.LayoutParams) : super(source)
    }

    companion object {
        private const val TAG = "_MySwipeReveal"
        const val SwipeFromRightEdge = 0
        const val SwipeFromLeftEdge = 1
        const val SwipeFromBothEdge = 2
        private const val MIN_FLING_VELOCITY = 400
        private const val DEFAULT_CHILD_GRAVITY = Gravity.TOP or Gravity.START
    }
}