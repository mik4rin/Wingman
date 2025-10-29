package com.example.wingman;

import com.example.wingman.data.Sched;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class NewSchedule extends Sched {

    public NewSchedule() {
        super();
    }

    public NewSchedule(String id, String userId, String title, String description,
                       String date, String schedType, long timestamp) {
        super(id, userId, title, description, date, schedType, timestamp);
    }

    public NewSchedule(String userId, String title,
                       String description, String date, String schedType, long timestamp) {
        super(null, userId, title, description, date, schedType, timestamp);
    }

    public Date getParsedDate() {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
            return sdf.parse(getDate());
        } catch (ParseException e) {
            return new Date(0);
        }
    }
}