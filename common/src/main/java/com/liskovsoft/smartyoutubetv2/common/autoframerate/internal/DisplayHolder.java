package com.liskovsoft.smartyoutubetv2.common.autoframerate.internal;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Convenience class to hold {@link DisplayHolder} Display object.
 *
 */
public class DisplayHolder {

    /**
     * Stub method to create the ModeInstance. In real world we need not to do it here.
     * @param modeId Mode Id
     * @param width Display mode's width
     * @param height Display mode's height
     * @param refreshRate Display mode's refresh rate
     *
     * @return {@link Mode} instance object.
     */
    public Mode getModeInstance(int modeId,int width,int height,float refreshRate){
        return new Mode(modeId,width,height,refreshRate);
    }

    /**
     * Inner class {@link Mode} holds the mode information.
     */
    public static class Mode implements Parcelable, Comparable<Mode> {

        private int mModeId;
        private int mHeight;
        private int mWidth;
        private float mRefreshRate;

        public Mode(int mModeId,int mWidth, int mHeight, float refreshRate){
            this.mModeId = mModeId;
            this.mWidth = mWidth;
            this.mHeight = (mHeight);
            this.mRefreshRate = (refreshRate);
        }
        /**
         * Returns this mode's id.
         * @return mode id
         */
        public int getModeId(){
            return mModeId;
        }

        /**
         * Returns the physical height of the display in pixels when configured in this mode's resolution.
         * @return display height
         */
        public int getPhysicalHeight(){
            return mHeight;
        }

        /**
         * Returns the physical width of the display in pixels when configured in this mode's
         * resolution.
         * @return display width
         */
        public int getPhysicalWidth() {
            return mWidth;
        }

        /**
         * Returns the refresh rate in frames per second.
         * @return refresh rate
         */
        public float getRefreshRate() {
            return mRefreshRate;

        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + mModeId;
            result = prime * result + mHeight;
            result = prime * result + mWidth;
            result = prime * result + Float.floatToIntBits(mRefreshRate);
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Mode other = (Mode) obj;
            if (mModeId != other.mModeId)
                return false;
            if (mHeight != other.mHeight)
                return false;
            if (mWidth != other.mWidth)
                return false;
            if (Float.floatToIntBits(mRefreshRate) != Float.floatToIntBits(other.mRefreshRate))
                return false;
            return true;
        }

        /**
         * Returns {@code true} if this mode matches the given parameters.
         * @param width of the given display
         * @param height of the given display
         * @param refreshRate refresh rate of the current display
         * @return {@code true} if width, height and refresh rate matches with the current mode.
         */
        public boolean matches(int width, int height, float refreshRate) {
            return mWidth == width &&
                    mHeight == height &&
                    Float.floatToIntBits(mRefreshRate) == Float.floatToIntBits(refreshRate);
        }

        @Override
        public String toString() {
            return "Mode [mModeId=" + mModeId + ", mHeight=" + mHeight + ", mWidth=" + mWidth
                    + ", mRefreshRate=" + mRefreshRate + "]";
        }
        @Override
        public int describeContents() {
            return 0;
        }

        private Mode(Parcel in) {
            this(in.readInt(), in.readInt(), in.readInt(), in.readFloat());
        }

        @Override
        public void writeToParcel(Parcel out, int parcelableFlags) {
            out.writeInt(mModeId);
            out.writeInt(mWidth);
            out.writeInt(mHeight);
            out.writeFloat(mRefreshRate);
        }

        @SuppressWarnings("hiding")
        public static final Creator<Mode> CREATOR
                = new Creator<Mode>() {
            @Override
            public Mode createFromParcel(Parcel in) {
                return new Mode(in);
            }

            @Override
            public Mode[] newArray(int size) {
                return new Mode[size];
            }
        };

        /**
         * Sort in descendant order
         */
        @Override
        public int compareTo(Mode o) {
            if (getPhysicalWidth() == o.getPhysicalWidth()) {
                return (int) ((o.getRefreshRate() * 1_000) - (getRefreshRate() * 1_000));
            }

            return o.getPhysicalWidth() - getPhysicalWidth();
        }
    }
}
