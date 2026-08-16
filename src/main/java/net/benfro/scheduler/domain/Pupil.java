package net.benfro.scheduler.domain;

import java.util.ArrayList;
import java.util.List;

public record Pupil(List<AttendanceWindow> stayingTimes) {

    public Pupil {
        stayingTimes = new ArrayList<>(stayingTimes);
    }

    public void addStayingTime(AttendanceWindow stayingTime) {
        stayingTimes.add(stayingTime);
    }
}
