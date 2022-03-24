/*
 * Copyright (c) 2012-2017 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package de.blinkt.openvpn.core;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Vector;

import static java.lang.Math.max;

/**
 * Created by arne on 23.05.17.
 */
public class TrafficHistory implements Parcelable {
    public static final long PERIODS_TO_KEEP = 5;
    public static final int TIME_PERIOD_MINTUES = 60 * 1000;
    public static final int TIME_PERIOD_HOURS = 3600 * 1000;
    public static final Creator<TrafficHistory> CREATOR = new Creator<TrafficHistory>() {
        @Override
        public TrafficHistory createFromParcel(Parcel in) {
            return new TrafficHistory(in);
        }
        @Override
        public TrafficHistory[] newArray(int size) {
            return new TrafficHistory[size];
        }
    };
    private LinkedList<TrafficDatapoint> trafficHistorySeconds = new LinkedList<>();
    private LinkedList<TrafficDatapoint> trafficHistoryMinutes = new LinkedList<>();
    private LinkedList<TrafficDatapoint> trafficHistoryHours = new LinkedList<>();
    private TrafficDatapoint lastSecondUsedForMinute;
    private TrafficDatapoint lastMinuteUsedForHours;
    public TrafficHistory() {
    }
    protected TrafficHistory(Parcel in) {
        in.readList(trafficHistorySeconds, getClass().getClassLoader());
        in.readList(trafficHistoryMinutes, getClass().getClassLoader());
        in.readList(trafficHistoryHours, getClass().getClassLoader());
        lastSecondUsedForMinute = in.readParcelable(getClass().getClassLoader());
        lastMinuteUsedForHours = in.readParcelable(getClass().getClassLoader());
    }
    public static LinkedList<TrafficDatapoint> getDummyList() {
        LinkedList<TrafficDatapoint> list = new LinkedList<>();
        list.add(new TrafficDatapoint(0, 0, System.currentTimeMillis()));
        return list;
    }
    public LastDiff getLastDiff(TrafficDatapoint tdp) {
        TrafficDatapoint lasttdp;
        if (trafficHistorySeconds.size() == 0)
            lasttdp = new TrafficDatapoint(0, 0, System.currentTimeMillis());
        else
            lasttdp = trafficHistorySeconds.getLast();
        if (tdp == null) {
            tdp = lasttdp;
            if (trafficHistorySeconds.size() < 2)
                lasttdp = tdp;
            else {
                trafficHistorySeconds.descendingIterator().next();
                tdp = trafficHistorySeconds.descendingIterator().next();
            }
        }
        return new LastDiff(lasttdp, tdp);
    }
    @Override
    public int describeContents() {
        return 0;
    }
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeList(trafficHistorySeconds);
        dest.writeList(trafficHistoryMinutes);
        dest.writeList(trafficHistoryHours);
        dest.writeParcelable(lastSecondUsedForMinute, 0);
        dest.writeParcelable(lastMinuteUsedForHours, 0);
    }
    public LinkedList<TrafficDatapoint> getHours() {
        return trafficHistoryHours;
    }
    public LinkedList<TrafficDatapoint> getMinutes() {
        return trafficHistoryMinutes;
    }
    public LinkedList<TrafficDatapoint> getSeconds() {
        return trafficHistorySeconds;
    }
    LastDiff add(long in, long out) {
        TrafficDatapoint tdp = new TrafficDatapoint(in, out, System.currentTimeMillis());
        LastDiff diff = getLastDiff(tdp);
        addDataPoint(tdp);
        return diff;
    }
    private void addDataPoint(TrafficDatapoint tdp) {
        trafficHistorySeconds.add(tdp);
        if (lastSecondUsedForMinute == null) {
            lastSecondUsedForMinute = new TrafficDatapoint(0, 0, 0);
            lastMinuteUsedForHours = new TrafficDatapoint(0, 0, 0);
        }
        removeAndAverage(tdp, true);
    }
    private void removeAndAverage(TrafficDatapoint newTdp, boolean seconds) {
        HashSet<TrafficDatapoint> toRemove = new HashSet<>();
        Vector<TrafficDatapoint> toAverage = new Vector<>();
        long timePeriod;
        LinkedList<TrafficDatapoint> tpList, nextList;
        TrafficDatapoint lastTsPeriod;
        if (seconds) {
            timePeriod = TIME_PERIOD_MINTUES;
            tpList = trafficHistorySeconds;
            nextList = trafficHistoryMinutes;
            lastTsPeriod = lastSecondUsedForMinute;
        } else {
            timePeriod = TIME_PERIOD_HOURS;
            tpList = trafficHistoryMinutes;
            nextList = trafficHistoryHours;
            lastTsPeriod = lastMinuteUsedForHours;
        }
        if (newTdp.timestamp / timePeriod > (lastTsPeriod.timestamp / timePeriod)) {
            nextList.add(newTdp);
            if (seconds) {
                lastSecondUsedForMinute = newTdp;
                removeAndAverage(newTdp, false);
            } else
                lastMinuteUsedForHours = newTdp;
            for (TrafficDatapoint tph : tpList) {
                // List is iteratered from oldest to newest, remembert first one that we did not
                if ((newTdp.timestamp - tph.timestamp) / timePeriod >= PERIODS_TO_KEEP)
                    toRemove.add(tph);
            }
            tpList.removeAll(toRemove);
        }
    }
    public static class TrafficDatapoint implements Parcelable {
        public static final Creator<TrafficDatapoint> CREATOR = new Creator<TrafficDatapoint>() {
            @Override
            public TrafficDatapoint createFromParcel(Parcel in) {
                return new TrafficDatapoint(in);
            }
            @Override
            public TrafficDatapoint[] newArray(int size) {
                return new TrafficDatapoint[size];
            }
        };
        public final long timestamp;
        public final long in;
        public final long out;
        private TrafficDatapoint(long inBytes, long outBytes, long timestamp) {
            this.in = inBytes;
            this.out = outBytes;
            this.timestamp = timestamp;
        }
        private TrafficDatapoint(Parcel in) {
            timestamp = in.readLong();
            this.in = in.readLong();
            out = in.readLong();
        }
        @Override
        public int describeContents() {
            return 0;
        }
        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeLong(timestamp);
            dest.writeLong(in);
            dest.writeLong(out);
        }
    }
    static class LastDiff {
        final private TrafficDatapoint tdp;
        final private TrafficDatapoint lasttdp;
        private LastDiff(TrafficDatapoint lasttdp, TrafficDatapoint tdp) {
            this.lasttdp = lasttdp;
            this.tdp = tdp;
        }
        public long getDiffOut() {
            return max(0, tdp.out - lasttdp.out);
        }
        public long getDiffIn() {
            return max(0, tdp.in - lasttdp.in);
        }
        public long getIn() {
            return tdp.in;
        }
        public long getOut() {
            return tdp.out;
        }
    }
}