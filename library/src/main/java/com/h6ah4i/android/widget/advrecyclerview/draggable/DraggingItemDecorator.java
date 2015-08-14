/*
 *    Copyright (C) 2015 Haruki Hasegawa
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.h6ah4i.android.widget.advrecyclerview.draggable;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.NinePatchDrawable;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.h6ah4i.android.widget.advrecyclerview.utils.CustomRecyclerViewUtils;

class DraggingItemDecorator extends BaseDraggableItemDecorator {
    @SuppressWarnings("unused")
    private static final String TAG = "DraggingItemDecorator";

    private Point mGrabbedPosition = new Point();
    private Point mTranslation = new Point();
    private Point mRecyclerViewPadding = new Point();
    private Bitmap mDraggingItemImage;
    private Point mTranslationStartLimit = new Point();
    private Point mTranslationEndLimit = new Point();
    private Point mGrabbedItemSize = new Point();
    private Point mTouchPosition = new Point();
    private NinePatchDrawable mShadowDrawable;
    private Rect mShadowPadding = new Rect();
    private Rect mDraggingItemMargins = new Rect();
    private boolean mStarted;
    private boolean mIsScrolling;
    private ItemDraggableRange mRange;

    public DraggingItemDecorator(RecyclerView recyclerView, RecyclerView.ViewHolder draggingItem, ItemDraggableRange range) {
        super(recyclerView, draggingItem);
        mRange = range;
        CustomRecyclerViewUtils.getLayoutMargins(mDraggingItem.itemView, mDraggingItemMargins);
    }

    @Override
    public void onDrawOver(Canvas c, RecyclerView parent, RecyclerView.State state) {
        // NOTE:
        // On lollipop or later, View has Z-axis property and no needed to draw the dragging view manually.
        // However, if the RecyclerView has any other decorations or RecyclerView is in scrolling state,
        // need to draw it to avoid visual corruptions.
        if (mDraggingItemImage != null) {
            final float left = mTranslation.x + mDraggingItemMargins.left + mRecyclerViewPadding.x - mShadowPadding.left;
            final float top = mTranslation.y + mDraggingItemMargins.top + mRecyclerViewPadding.y - mShadowPadding.top;
            c.drawBitmap(mDraggingItemImage, left, top, null);
        }
    }

    public void start(MotionEvent e, PointF grabbedPosition) {
        if (mStarted) {
            return;
        }

        final View itemView = mDraggingItem.itemView;

        mGrabbedPosition.x = (int) (grabbedPosition.x + 0.5f);
        mGrabbedPosition.y = (int) (grabbedPosition.y + 0.5f);

        // draw the grabbed item on bitmap
        mDraggingItemImage = createDraggingItemImage(itemView);

        mGrabbedItemSize.x = itemView.getWidth();
        mGrabbedItemSize.y = itemView.getHeight();
        mTranslationStartLimit.x = mRecyclerViewPadding.x = mRecyclerView.getPaddingLeft();
        mTranslationStartLimit.y = mRecyclerViewPadding.y = mRecyclerView.getPaddingTop();

        // hide
        itemView.setVisibility(View.INVISIBLE);

        update(e);

        mRecyclerView.addItemDecoration(this);

        mStarted = true;
    }

    public void finish(boolean animate) {
        if (mStarted) {
            mRecyclerView.removeItemDecoration(this);
        }

        final RecyclerView.ItemAnimator itemAnimator = mRecyclerView.getItemAnimator();
        if (itemAnimator != null) {
            itemAnimator.endAnimations();
        }
        mRecyclerView.stopScroll();

        // return to default position
        updateDraggingItemPosition(mTranslation);
        if (mDraggingItem != null) {
            moveToDefaultPosition(mDraggingItem.itemView, animate);
        }

        // show
        if (mDraggingItem != null) {
            mDraggingItem.itemView.setVisibility(View.VISIBLE);
        }
        mDraggingItem = null;

        if (mDraggingItemImage != null) {
            mDraggingItemImage.recycle();
            mDraggingItemImage = null;
        }

        mRange = null;
        mGrabbedPosition.x = mGrabbedPosition.y = 0;
        mTranslation.x = mTranslation.y = 0;
        mTranslationStartLimit.x = mTranslationStartLimit.y = 0;
        mTranslationEndLimit.x = mTranslationEndLimit.y = 0;
        mRecyclerViewPadding.x = mRecyclerViewPadding.y = 0;
        mGrabbedItemSize.x = mGrabbedItemSize.y = 0;
        mTouchPosition.x = mTouchPosition.y = 0;
        mStarted = false;
    }

    public void update(MotionEvent e) {
        mTouchPosition.x = (int) (e.getX() + 0.5f);
        mTouchPosition.y = (int) (e.getY() + 0.5f);
        refresh();
    }

    public void refresh() {
        updateTranslationOffset();
        updateDraggingItemPosition(mTranslation);

        ViewCompat.postInvalidateOnAnimation(mRecyclerView);
    }

    public void setShadowDrawable(NinePatchDrawable shadowDrawable) {
        mShadowDrawable = shadowDrawable;

        if (mShadowDrawable != null) {
            mShadowDrawable.getPadding(mShadowPadding);
        }
    }

    public Point getDraggingItemTranslation() {
        return mTranslation;
    }

    private void updateTranslationOffset() {
        final int childCount = mRecyclerView.getChildCount();
        if (childCount > 0) {
            mTranslationStartLimit.x = mRecyclerView.getPaddingLeft();
            mTranslationStartLimit.y = mRecyclerView.getPaddingTop();
            mTranslationEndLimit.x = Math.max(0, (mRecyclerView.getWidth() - mRecyclerView.getPaddingRight() - mGrabbedItemSize.x));
            mTranslationEndLimit.y = Math.max(0, (mRecyclerView.getHeight() - mRecyclerView.getPaddingBottom() - mGrabbedItemSize.y));

            if (!mIsScrolling) {
                final int firstVisiblePosition = CustomRecyclerViewUtils.findFirstVisibleItemPosition(mRecyclerView);
                final int lastVisiblePosition = CustomRecyclerViewUtils.findLastVisibleItemPosition(mRecyclerView);
                final View firstVisibleChild = findRangeFirstItem(mRecyclerView, mRange, firstVisiblePosition, lastVisiblePosition);
                final View lastVisibleChild = findRangeLastItem(mRecyclerView, mRange, firstVisiblePosition, lastVisiblePosition);

                if (firstVisibleChild != null) {
                    mTranslationStartLimit.x = Math.min(mTranslationEndLimit.x, firstVisibleChild.getLeft());
                    mTranslationStartLimit.y = Math.min(mTranslationEndLimit.y, firstVisibleChild.getTop());
                }

                if (lastVisibleChild != null) {
                    mTranslationEndLimit.x = Math.min(mTranslationEndLimit.x, lastVisibleChild.getRight());
                    mTranslationEndLimit.y = Math.min(mTranslationEndLimit.y, lastVisibleChild.getBottom());
                }
            }
        } else {
            mTranslationEndLimit.x = mTranslationStartLimit.x = mRecyclerView.getPaddingLeft();
            mTranslationEndLimit.y = mTranslationStartLimit.y = mRecyclerView.getPaddingTop();
        }

        mTranslation.x = mTouchPosition.x - mGrabbedPosition.x;
        mTranslation.y = mTouchPosition.y - mGrabbedPosition.y;
        mTranslation.x = Math.min(Math.max(mTranslation.x, mTranslationStartLimit.x), mTranslationEndLimit.x);
        mTranslation.y = Math.min(Math.max(mTranslation.y, mTranslationStartLimit.y), mTranslationEndLimit.y);
    }

    /*public boolean isReachedToLeftLimit() {
        return (mTranslationX == mTranslationLeftLimit);
    }

    public boolean isReachedToRightLimit() {
        return (mTranslationX == mTranslationRightLimit);
    }*/

    private Bitmap createDraggingItemImage(View v) {
        int width = v.getWidth() + mShadowPadding.left + mShadowPadding.right;
        int height = v.getHeight() + mShadowPadding.top + mShadowPadding.bottom;

        final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        final Canvas canvas = new Canvas(bitmap);

        final int savedCount = canvas.save(Canvas.CLIP_SAVE_FLAG | Canvas.MATRIX_SAVE_FLAG);
        canvas.translate(mShadowPadding.left, mShadowPadding.top);
        canvas.scale(0.84f, 0.84f, width / 2, height / 2);
        canvas.rotate(12.5f, width / 2, height / 2);

        v.draw(canvas);
        canvas.restoreToCount(savedCount);

        return bitmap;
    }

    private void updateDraggingItemPosition(Point translation) {
        // NOTE: Need to update the view position to make other decorations work properly while dragging
        if (mDraggingItem != null) {
            Log.d(TAG, "dragging item pos="+translation);
            Point delta = translation; // TODO: Review
            delta.x -= mDraggingItem.itemView.getLeft();
            delta.y -= mDraggingItem.itemView.getTop();
            setItemTranslation(mRecyclerView, mDraggingItem, translation);
        }
    }

    public void setIsScrolling(boolean isScrolling) {
        if (mIsScrolling == isScrolling) {
            return;
        }

        mIsScrolling = isScrolling;

    }

    /*public int getTranslatedItemPositionLeft() {
        return mTranslationX;
    }

    public int getTranslatedItemPositionRight() {
        return mTranslationX + mGrabbedItemWidth;
    }*/


    private static View findRangeFirstItem(RecyclerView rv, ItemDraggableRange range, int firstVisiblePosition, int lastVisiblePosition) {
        if (firstVisiblePosition == RecyclerView.NO_POSITION || lastVisiblePosition == RecyclerView.NO_POSITION) {
            return null;
        }

        View v = null;

        final int childCount = rv.getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View v2 = rv.getChildAt(i);
            final RecyclerView.ViewHolder vh = rv.getChildViewHolder(v2);

            if (vh != null) {
                final int position = vh.getLayoutPosition();

                if ((position >= firstVisiblePosition) &&
                        (position <= lastVisiblePosition) &&
                        range.checkInRange(position)) {
                    v = v2;
                    break;
                }

            }
        }

        return v;
    }

    private static View findRangeLastItem(RecyclerView rv, ItemDraggableRange range, int firstVisiblePosition, int lastVisiblePosition) {
        if (firstVisiblePosition == RecyclerView.NO_POSITION || lastVisiblePosition == RecyclerView.NO_POSITION) {
            return null;
        }

        View v = null;

        final int childCount = rv.getChildCount();
        for (int i = childCount - 1; i >= 0; i--) {
            final View v2 = rv.getChildAt(i);
            final RecyclerView.ViewHolder vh = rv.getChildViewHolder(v2);

            if (vh != null) {
                final int position = vh.getLayoutPosition();

                if ((position >= firstVisiblePosition) &&
                        (position <= lastVisiblePosition) &&
                        range.checkInRange(position)) {
                    v = v2;
                    break;
                }
            }
        }

        return v;
    }

    public void invalidateDraggingItem() {
        if (mDraggingItem != null) {
            mDraggingItem.itemView.setVisibility(View.VISIBLE);
        }

        mDraggingItem = null;
    }

    public void setDraggingItemViewHolder(RecyclerView.ViewHolder holder) {
        if (mDraggingItem != null) {
            throw new IllegalStateException("A new view holder is attempt to be assigned before invalidating the older one");
        }

        mDraggingItem = holder;

        holder.itemView.setVisibility(View.INVISIBLE);
    }
}