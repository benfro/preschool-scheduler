package net.benfro.presched.domain;

import java.util.ArrayList;
import java.util.List;

public record Pupil(List<TimeSlot> stayingTimes) {

    public Pupil {
        stayingTimes = new ArrayList<>(stayingTimes);
    }

    public void addStayingTime(TimeSlot stayingTime) {
        stayingTimes.add(stayingTime);
    }
}
