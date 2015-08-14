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

import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.view.animation.Interpolator;

import com.h6ah4i.android.widget.advrecyclerview.utils.CustomRecyclerViewUtils;

import java.util.ArrayList;

class SwapTargetItemOperator extends BaseDraggableItemDecorator {
    @SuppressWarnings("unused")
    private static final String TAG = "SwapTargetItemOperator";

    private ArrayList<RecyclerView.ViewHolder> mSwapTargetItems;
    private Interpolator mSwapTargetTranslationInterpolator;
    private Point mTranslation;
    private Point originalPos;
    private int mDraggingItemSize;
    private Rect mSwapTargetDecorationOffsets = new Rect();
    private Rect mSwapTargetItemMargins = new Rect();
    private Rect mDraggingItemMargins = new Rect();
    private Rect mDraggingItemDecorationOffsets = new Rect();
    private boolean mStarted;
    private float mReqTranslationPhase;
    private float mCurTranslationPhase;
    private long mDraggingItemId;
    private ItemDraggableRange mRange;

    public SwapTargetItemOperator(RecyclerView recyclerView, RecyclerView.ViewHolder draggingItem, ItemDraggableRange range) {
        super(recyclerView, draggingItem);

        mDraggingItemId = mDraggingItem.getItemId();
        mRange = range;

        CustomRecyclerViewUtils.getLayoutMargins(mDraggingItem.itemView, mDraggingItemMargins);
        CustomRecyclerViewUtils.getDecorationOffsets(
                mRecyclerView.getLayoutManager(), mDraggingItem.itemView, mDraggingItemDecorationOffsets);
    }

    public void setSwapTargetTranslationInterpolator(Interpolator interpolator) {
        mSwapTargetTranslationInterpolator = interpolator;
    }

    @Override
    public void onDraw(Canvas c, RecyclerView parent, RecyclerView.State state) {
        final RecyclerView.ViewHolder draggingItem = mDraggingItem;

        if (draggingItem == null || draggingItem.getItemId() != mDraggingItemId) {
            return;
        }

        final ArrayList<RecyclerView.ViewHolder> swapTargetItems =
                RecyclerViewDragDropManager.findSwapTargetItems(
                        mRecyclerView, draggingItem, mDraggingItemId, mTranslation, mRange, null, 0);

        // reset translation if the swap target has changed
        if ((mSwapTargetItems != null) && (swapTargetItems != null) && (mSwapTargetItems.get(0) != swapTargetItems.get(0))) {
            Point origin = new Point(0, 0);
            for (int i = 0; i < mSwapTargetItems.size(); i++) {
                setItemTranslation(mRecyclerView, mSwapTargetItems.get(i), origin);
            }
        }

        if (swapTargetItems != null) {
            /**
             * Calculate phases based on only one target
             * Other target's phase should anyway match each other
             */
            mReqTranslationPhase = calculateTranslationPhase(draggingItem, swapTargetItems.get(0));
            Log.d(TAG, "mReqTranslationPhase = "+mReqTranslationPhase);

            if (mSwapTargetItems != swapTargetItems) {
                Log.d(TAG, "mSwapTargetItems != swapTargetItems");
                mCurTranslationPhase = mReqTranslationPhase;
            } else {
                // interpolate to make it moves smoothly
                mCurTranslationPhase = calculateCurrentTranslationPhase(mCurTranslationPhase, mReqTranslationPhase);
            }
            for (int i = 0; i < swapTargetItems.size(); i++) {
                updateSwapTargetTranslation(draggingItem, swapTargetItems.get(i), mCurTranslationPhase);
            }
        }

        mSwapTargetItems = swapTargetItems;
    }

    private float calculateTranslationPhase(RecyclerView.ViewHolder draggingItem, RecyclerView.ViewHolder swapTargetItem) {
        final View swapItemView = swapTargetItem.itemView;

        //final int pos1 = draggingItem.getLayoutPosition();
        //final int pos2 = swapTargetItem.getLayoutPosition();

        CustomRecyclerViewUtils.getDecorationOffsets(
                mRecyclerView.getLayoutManager(), swapItemView, mSwapTargetDecorationOffsets);
        CustomRecyclerViewUtils.getLayoutMargins(swapItemView, mSwapTargetItemMargins);

        final Rect m2 = mSwapTargetItemMargins;
        final Rect d2 = mSwapTargetDecorationOffsets;
        final int h2 = swapItemView.getWidth() + m2.left + m2.right + d2.left + d2.right;

        final float offsetPx = Math.max(Math.abs(draggingItem.itemView.getLeft() - mTranslation.x),
                Math.abs(draggingItem.itemView.getTop() - mTranslation.y));
        final float phase = (h2 != 0) ? (offsetPx / h2) : 0.0f;

        /*float translationPhase;

        if (pos1 > pos2) {
            // dragging item moving to upward
            translationPhase = phase;
        } else {
            // dragging item moving to downward
            translationPhase = 1.0f + phase;
        }*/

        return Math.min(Math.max(phase, 0.0f), 1.0f);
    }

    private static float calculateCurrentTranslationPhase(float cur, float req) {
        final float A = 0.3f;
        final float B = 0.01f;
        final float tmp = (cur * (1.0f - A)) + (req * A);

        return (Math.abs(tmp - req) < B) ? req : tmp;
    }

    private void updateSwapTargetTranslation(RecyclerView.ViewHolder draggingItem, RecyclerView.ViewHolder swapTargetItem, float translationPhase) {
        final View swapItemView = swapTargetItem.itemView;

        final int pos1 = draggingItem.getLayoutPosition();
        final int pos2 = swapTargetItem.getLayoutPosition();

        final Rect m1 = mDraggingItemMargins;
        final Rect d1 = mDraggingItemDecorationOffsets;
        final int h1 = mDraggingItemSize + m1.left + m1.right + d1.left + d1.right;
        final int h2 = mDraggingItemSize + m1.top + m1.bottom + d1.top + d1.bottom;

        if (mSwapTargetTranslationInterpolator != null) {
            translationPhase = mSwapTargetTranslationInterpolator.getInterpolation(translationPhase);
        }

        if (pos1 > pos2) {
            // dragging item moving to upward
            if ((pos2+1)%4 != 0) {
                ViewCompat.setTranslationX(swapItemView, translationPhase * h1); //TODO
            } else {
                //edge case
                ViewCompat.setTranslationX(swapItemView, -translationPhase * h1 * 4);
                ViewCompat.setTranslationY(swapItemView, translationPhase * h2);
            }
        } else {
            // dragging item moving to downward
            //ViewCompat.setTranslationX(swapItemView, (translationPhase - 1.0f) * h1);
            if (pos2%4 != 0) {
                ViewCompat.setTranslationX(swapItemView, -translationPhase * h1);
            } else {
                //edge case
                ViewCompat.setTranslationX(swapItemView, translationPhase * h1 * 4);
                ViewCompat.setTranslationY(swapItemView, -translationPhase * h2);
            }
        }
    }

    public void start() {
        if (mStarted) {
            return;
        }

        mDraggingItemSize = mDraggingItem.itemView.getWidth();

        mRecyclerView.addItemDecoration(this, 0);

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

        if (mSwapTargetItems != null) {
            // return to default position
            for (int i = 0; i < mSwapTargetItems.size(); i++) {
                updateSwapTargetTranslation(mDraggingItem, mSwapTargetItems.get(i), mCurTranslationPhase); // TODO
                Log.d(TAG, "moving i="+i+" to default position");
                moveToDefaultPosition(mSwapTargetItems.get(i).itemView, animate);
            }
            mSwapTargetItems = null;
        }

        mRange = null;
        mDraggingItem = null;
        mTranslation.x = mTranslation.y = 0;
        mDraggingItemSize = 0;
        mCurTranslationPhase = 0.0f;
        mReqTranslationPhase = 0.0f;
        mStarted = false;
    }

    public void update(Point translation) {
        mTranslation = translation;
    }
}