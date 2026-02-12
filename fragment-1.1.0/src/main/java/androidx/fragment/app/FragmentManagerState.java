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

import java.util.ArrayList;

@SuppressLint("BanParcelableUsage")
final class FragmentManagerState implements Parcelable {
    ArrayList<FragmentState> mActive;
    ArrayList<String> mAdded;
    BackStackState[] mBackStack;
    String mPrimaryNavActiveWho = null;
    int mNextFragmentIndex;

    public FragmentManagerState() {
    }

    public FragmentManagerState(Parcel in) {
        mActive = in.createTypedArrayList(FragmentState.CREATOR);
        mAdded = in.createStringArrayList();
        mBackStack = in.createTypedArray(BackStackState.CREATOR);
        mPrimaryNavActiveWho = in.readString();
        mNextFragmentIndex = in.readInt();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedList(mActive);
        dest.writeStringList(mAdded);
        dest.writeTypedArray(mBackStack, flags);
        dest.writeString(mPrimaryNavActiveWho);
        dest.writeInt(mNextFragmentIndex);
    }

    public static final Parcelable.Creator<FragmentManagerState> CREATOR
            = new Parcelable.Creator<FragmentManagerState>() {
        @Override
        public FragmentManagerState createFromParcel(Parcel in) {
            return new FragmentManagerState(in);
        }

        @Override
        public FragmentManagerState[] newArray(int size) {
            return new FragmentManagerState[size];
        }
    };
}
