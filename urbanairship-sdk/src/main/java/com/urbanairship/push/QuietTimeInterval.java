/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.push;

import com.urbanairship.Logger;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;

/**
 * Model object representing a quiet time interval.
 */
class QuietTimeInterval implements JsonSerializable {

    private static final String START_HOUR_KEY = "start_hour";
    private static final String START_MIN_KEY =  "start_min";
    private static final String END_HOUR_KEY = "end_hour";
    private static final String END_MIN_KEY = "end_min";
    private static final int NOT_SET_VAL = -1;

    private final int startHour;
    private final int startMin;
    private final int endHour;
    private final int endMin;

    private QuietTimeInterval(Builder builder) {
        this.startHour = builder.startHour;
        this.startMin = builder.startMin;
        this.endHour = builder.endHour;
        this.endMin = builder.endMin;
    }

    /**
     * Determines whether we are currently in the middle of "Quiet Time".  Returns false if Quiet Time is disabled,
     * and evaluates whether or not the current date/time falls within the Quiet Time interval set by the user.
     *
     * @param now Reference time for determining if interval is in quiet time.
     * @return A boolean indicating whether it is currently "Quiet Time".
     */
    public boolean isInQuietTime(Calendar now) {

        Date[] quietTimeInterval = getQuietTimeIntervalDateArray();
        if (quietTimeInterval == null) {
            // if any of the values are invalid/not set
            // quiet time is not set
            return false;
        }

        Calendar start = new GregorianCalendar();
        start.setTime(quietTimeInterval[0]);

        Calendar end = new GregorianCalendar();
        end.setTime(quietTimeInterval[1]);

        return !(now.before(start) || now.after(end));
    }

    /**
     * Returns the Quiet Time interval currently set by the user.
     *
     * @return An array of two Date instances, representing the start and end of Quiet Time.
     */
    public Date[] getQuietTimeIntervalDateArray() {
        if (startHour == NOT_SET_VAL || startMin == NOT_SET_VAL ||
                endHour == NOT_SET_VAL || endMin == NOT_SET_VAL) {
            return null;
        }

        // Grab the start date.
        Calendar start = Calendar.getInstance();
        start.set(Calendar.HOUR_OF_DAY, startHour);
        start.set(Calendar.MINUTE, startMin);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);

        // Prepare the end date.
        Calendar end = Calendar.getInstance();
        end.set(Calendar.HOUR_OF_DAY, endHour);
        end.set(Calendar.MINUTE, endMin);
        end.set(Calendar.SECOND, 0);
        end.set(Calendar.MILLISECOND, 0);

        if (!end.after(start)) {
            end.add(Calendar.DAY_OF_YEAR, 1);
        }

        Date startDate = start.getTime();
        Date endDate = end.getTime();
        return new Date[] { startDate, endDate };
    }

    @Override
    public String toString() {
        return "QuietTimeInterval{" +
                "startHour=" + startHour +
                ", startMin=" + startMin +
                ", endHour=" + endHour +
                ", endMin=" + endMin +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        QuietTimeInterval that = (QuietTimeInterval) o;

        if (startHour != that.startHour) {
            return false;
        }
        if (startMin != that.startMin) {
            return false;
        }
        if (endHour != that.endHour) {
            return false;
        }
        return endMin == that.endMin;

    }

    @Override
    public int hashCode() {
        int result = startHour;
        result = 31 * result + startMin;
        result = 31 * result + endHour;
        result = 31 * result + endMin;
        return result;
    }

    /**
     * QuietTimeInterval builder class.
     */
    public static class Builder {
        private int startHour = -1;
        private int startMin = -1;
        private int endHour = -1;
        private int endMin = -1;

        /**
         * Set the quiet time interval.
         *
         * @param startTime The interval start time as a Date.
         * @param endTime The interval end time as a Date.
         * @return The builder with the interval set.
         */
        public Builder setQuietTimeInterval(Date startTime, Date endTime) {
            Calendar startCal = Calendar.getInstance();
            startCal.setTime(startTime);
            this.startHour = startCal.get(Calendar.HOUR_OF_DAY);
            this.startMin = startCal.get(Calendar.MINUTE);

            Calendar endCal = Calendar.getInstance();
            endCal.setTime(endTime);
            this.endHour = endCal.get(Calendar.HOUR_OF_DAY);
            this.endMin = endCal.get(Calendar.MINUTE);
            return this;
        }

        /**
         * Set the quiet time interval start hour.
         *
         * @param startHour The start hour as an int.
         * @return The builder with the start hour set.
         */
        public Builder setStartHour(int startHour) {
            this.startHour = startHour;
            return this;
        }

        /**
         * Set the quiet time interval start min.
         *
         * @param startMin The start min as an int.
         * @return The builder with the start min set.
         */
        public Builder setStartMin(int startMin) {
            this.startMin = startMin;
            return this;
        }

        /**
         * Set the quiet time interval end hour.
         *
         * @param endHour The end hour as an int.
         * @return The builder with the end hour set.
         */
        public Builder setEndHour(int endHour) {
            this.endHour = endHour;
            return this;
        }

        /**
         * Set the quiet time interval end min.
         *
         * @param endMin The end min as an int.
         * @return The builder with the end min set.
         */
        public Builder setEndMin(int endMin) {
            this.endMin = endMin;
            return this;
        }

        /**
         * Build the QuietTimeInterval instance.
         *
         * @return The QuietTimeInterval instance.
         */
        public QuietTimeInterval build() {
            return new QuietTimeInterval(this);
        }
    }

    @Override
    public JsonValue toJsonValue() {
        Map<String, Integer> quietTimeInterval = new HashMap<>();

        quietTimeInterval.put(START_HOUR_KEY, startHour);
        quietTimeInterval.put(START_MIN_KEY, startMin);
        quietTimeInterval.put(END_HOUR_KEY, endHour);
        quietTimeInterval.put(END_MIN_KEY, endMin);

        try {
            return JsonValue.wrap(quietTimeInterval);
        } catch (JsonException e) {
            Logger.error("QuietTimeInterval - Failed to create quiet time interval as json", e);
            return JsonValue.NULL;
        }
    }

    /**
     * Static helper method to deserialize JSON into a QuietTimeInterval instance.
     *
     * @param json The JSON as a string.
     * @return The deserialized QuietTimeInterval instance.
     */
    public static QuietTimeInterval parseJson(String json) {

        JsonMap jsonMap;
        try {
            jsonMap = JsonValue.parseString(json).getMap();
        } catch (JsonException e) {
            Logger.error("QuietTimeInterval - Failed to create quiet time interval from json", e);
            return null;
        }

        if (jsonMap == null || jsonMap.isEmpty()) {
            return null;
        }

        return new Builder()
                .setStartHour(jsonMap.get(START_HOUR_KEY).getInt(NOT_SET_VAL))
                .setStartMin(jsonMap.get(START_MIN_KEY).getInt(NOT_SET_VAL))
                .setEndHour(jsonMap.get(END_HOUR_KEY).getInt(NOT_SET_VAL))
                .setEndMin(jsonMap.get(END_MIN_KEY).getInt(NOT_SET_VAL))
                .build();
    }
}
