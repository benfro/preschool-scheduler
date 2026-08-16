package net.benfro.presched.domain;

import java.util.ArrayList;
import java.util.List;

public record Group(String name, List<Pupil> pupils) {

    public Group {
        pupils = new ArrayList<>(pupils);
    }

    public void addPupil(Pupil pupil) {
        pupils.add(pupil);
    }
}
