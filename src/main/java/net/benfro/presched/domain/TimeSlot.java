package net.benfro.presched.domain;

import java.time.LocalDate;
import java.time.LocalTime;

import lombok.With;

public record TimeSlot(
    @With LocalDate date,
    @With LocalTime start,
    @With LocalTime end) {
}
