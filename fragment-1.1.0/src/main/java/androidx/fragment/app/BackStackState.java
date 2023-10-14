/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.fragment.app;

import android.annotation.SuppressLint;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.Log;

import androidx.lifecycle.Lifecycle;

import java.util.ArrayList;

@SuppressLint("BanParcelableUsage")
final class BackStackState implements Parcelable {
    final int[] mOps;
    final ArrayList<String> mFragmentWhos;
    final int[] mOldMaxLifecycleStates;
    final int[] mCurrentMaxLifecycleStates;
    final int mTransition;
    final int mTransitionStyle;
    final String mName;
    final int mIndex;
    final int mBreadCrumbTitleRes;
    final CharSequence mBreadCrumbTitleText;
    final int mBreadCrumbShortTitleRes;
    final CharSequence mBreadCrumbShortTitleText;
    final ArrayList<String> mSharedElementSourceNames;
    final ArrayList<String> mSharedElementTargetNames;
    final boolean mReorderingAllowed;

    public BackStackState(BackStackRecord bse) {
        final int numOps = bse.mOps.size();
        mOps = new int[numOps * 5];

        if (!bse.mAddToBackStack) {
            throw new IllegalStateException("Not on back stack");
        }

        mFragmentWhos = new ArrayList<>(numOps);
        mOldMaxLifecycleStates = new int[numOps];
        mCurrentMaxLifecycleStates = new int[numOps];
        int pos = 0;
        for (int opNum = 0; opNum < numOps; opNum++) {
            final BackStackRecord.Op op = bse.mOps.get(opNum);
            mOps[pos++] = op.mCmd;
            mFragmentWhos.add(op.mFragment != null ? op.mFragment.mWho : null);
            mOps[pos++] = op.mEnterAnim;
            mOps[pos++] = op.mExitAnim;
            mOps[pos++] = op.mPopEnterAnim;
            mOps[pos++] = op.mPopExitAnim;
            mOldMaxLifecycleStates[opNum] = op.mOldMaxState.ordinal();
            mCurrentMaxLifecycleStates[opNum] = op.mCurrentMaxState.ordinal();
        }
        mTransition = bse.mTransition;
        mTransitionStyle = bse.mTransitionStyle;
        mName = bse.mName;
        mIndex = bse.mIndex;
        mBreadCrumbTitleRes = bse.mBreadCrumbTitleRes;
        mBreadCrumbTitleText = bse.mBreadCrumbTitleText;
        mBreadCrumbShortTitleRes = bse.mBreadCrumbShortTitleRes;
        mBreadCrumbShortTitleText = bse.mBreadCrumbShortTitleText;
        mSharedElementSourceNames = bse.mSharedElementSourceNames;
        mSharedElementTargetNames = bse.mSharedElementTargetNames;
        mReorderingAllowed = bse.mReorderingAllowed;
    }

    public BackStackState(Parcel in) {
        mOps = in.createIntArray();
        mFragmentWhos = in.createStringArrayList();
        mOldMaxLifecycleStates = in.createIntArray();
        mCurrentMaxLifecycleStates = in.createIntArray();
        mTransition = in.readInt();
        mTransitionStyle = in.readInt();
        mName = in.readString();
        mIndex = in.readInt();
        mBreadCrumbTitleRes = in.readInt();
        mBreadCrumbTitleText = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
        mBreadCrumbShortTitleRes = in.readInt();
        mBreadCrumbShortTitleText = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
        mSharedElementSourceNames = in.createStringArrayList();
        mSharedElementTargetNames = in.createStringArrayList();
        mReorderingAllowed = in.readInt() != 0;
    }

    public BackStackRecord instantiate(FragmentManagerImpl fm) {
        BackStackRecord bse = new BackStackRecord(fm);
        int pos = 0;
        int num = 0;
        while (pos < mOps.length) {
            BackStackRecord.Op op = new BackStackRecord.Op();
            op.mCmd = mOps[pos++];
            if (FragmentManagerImpl.DEBUG) Log.v(FragmentManagerImpl.TAG,
                    "Instantiate " + bse + " op #" + num + " base fragment #" + mOps[pos]);
            String fWho = mFragmentWhos.get(num);
            if (fWho != null) {
                Fragment f = fm.mActive.get(fWho);
                op.mFragment = f;
            } else {
                op.mFragment = null;
            }
            op.mOldMaxState = Lifecycle.State.values()[mOldMaxLifecycleStates[num]];
            op.mCurrentMaxState = Lifecycle.State.values()[mCurrentMaxLifecycleStates[num]];
            op.mEnterAnim = mOps[pos++];
            op.mExitAnim = mOps[pos++];
            op.mPopEnterAnim = mOps[pos++];
            op.mPopExitAnim = mOps[pos++];
            bse.mEnterAnim = op.mEnterAnim;
            bse.mExitAnim = op.mExitAnim;
            bse.mPopEnterAnim = op.mPopEnterAnim;
            bse.mPopExitAnim = op.mPopExitAnim;
            bse.addOp(op);
            num++;
        }
        bse.mTransition = mTransition;
        bse.mTransitionStyle = mTransitionStyle;
        bse.mName = mName;
        bse.mIndex = mIndex;
        bse.mAddToBackStack = true;
        bse.mBreadCrumbTitleRes = mBreadCrumbTitleRes;
        bse.mBreadCrumbTitleText = mBreadCrumbTitleText;
        bse.mBreadCrumbShortTitleRes = mBreadCrumbShortTitleRes;
        bse.mBreadCrumbShortTitleText = mBreadCrumbShortTitleText;
        bse.mSharedElementSourceNames = mSharedElementSourceNames;
        bse.mSharedElementTargetNames = mSharedElementTargetNames;
        bse.mReorderingAllowed = mReorderingAllowed;
        bse.bumpBackStackNesting(1);
        return bse;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeIntArray(mOps);
        dest.writeStringList(mFragmentWhos);
        dest.writeIntArray(mOldMaxLifecycleStates);
        dest.writeIntArray(mCurrentMaxLifecycleStates);
        dest.writeInt(mTransition);
        dest.writeInt(mTransitionStyle);
        dest.writeString(mName);
        dest.writeInt(mIndex);
        dest.writeInt(mBreadCrumbTitleRes);
        TextUtils.writeToParcel(mBreadCrumbTitleText, dest, 0);
        dest.writeInt(mBreadCrumbShortTitleRes);
        TextUtils.writeToParcel(mBreadCrumbShortTitleText, dest, 0);
        dest.writeStringList(mSharedElementSourceNames);
        dest.writeStringList(mSharedElementTargetNames);
        dest.writeInt(mReorderingAllowed ? 1 : 0);
    }

    public static final Parcelable.Creator<BackStackState> CREATOR
            = new Parcelable.Creator<BackStackState>() {
        @Override
        public BackStackState createFromParcel(Parcel in) {
            return new BackStackState(in);
        }

        @Override
        public BackStackState[] newArray(int size) {
            return new BackStackState[size];
        }
    };
}