package net.benfro.util;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import net.benfro.scheduler.application.ScheduleResult;
import net.benfro.scheduler.domain.SlotActivity;
import net.benfro.scheduler.domain.Teacher;
import net.benfro.scheduler.domain.TeacherSlot;
import net.benfro.scheduler.domain.TimeSlot;
import net.benfro.scheduler.solver.ScheduleGenerator;

/**
 * Renders a {@link ScheduleResult} to an Excel workbook: one sheet per {@link Teacher},
 * 30-minute timeslots down the rows and each weekday-date of the week across the columns.
 * Cells are color-coded by {@link SlotActivity} - teaching orange, break green, planning
 * time red - so the workbook reads the same grid {@code ConsoleScheduleGridPresenter}
 * prints to a terminal, just as a file a non-technical user can open directly.
 */
public final class ExcelScheduleExporter {

    private static final DateTimeFormatter COLUMN_HEADER_FORMAT = DateTimeFormatter.ofPattern("EEE d/M");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private ExcelScheduleExporter() {
    }

    /** Builds the workbook in memory; the caller owns closing it (or the stream it's written to). */
    public static XSSFWorkbook export(ScheduleResult result) {
        List<LocalDate> week = result.week();
        List<Teacher> teachers = result.roster().getTeachers();
        List<TeacherSlot> allSlots = result.roster().getTeacherSlots();
        List<TimeSlot> gridSlots = ScheduleGenerator.dailySlots(week.getFirst()); // only the times are used

        XSSFWorkbook workbook = new XSSFWorkbook();
        CellStyle headerStyle = headerStyle(workbook);
        CellStyle teachingStyle = coloredStyle(workbook, IndexedColors.ORANGE);
        CellStyle breakStyle = coloredStyle(workbook, IndexedColors.GREEN);
        CellStyle planningStyle = coloredStyle(workbook, IndexedColors.RED);

        for (Teacher teacher : teachers) {
            Sheet sheet = workbook.createSheet(sheetName(teacher));
            writeHeaderRow(sheet, week, headerStyle);
            writeTimeslotRows(sheet, teacher, week, gridSlots, allSlots, teachingStyle, breakStyle, planningStyle);
            sheet.createFreezePane(1, 1);
            for (int column = 0; column <= week.size(); column++) {
                sheet.autoSizeColumn(column);
            }
        }
        return workbook;
    }

    /** Convenience for driving adapters that just want workbook bytes on a stream. */
    public static void export(ScheduleResult result, OutputStream out) throws IOException {
        try (XSSFWorkbook workbook = export(result)) {
            workbook.write(out);
        }
    }

    /** Row 0: blank corner cell, then one column per weekday-date. */
    private static void writeHeaderRow(Sheet sheet, List<LocalDate> week, CellStyle headerStyle) {
        Row header = sheet.createRow(0);
        Cell corner = header.createCell(0);
        corner.setCellValue("Time");
        corner.setCellStyle(headerStyle);
        for (int column = 0; column < week.size(); column++) {
            Cell cell = header.createCell(column + 1);
            cell.setCellValue(week.get(column).format(COLUMN_HEADER_FORMAT));
            cell.setCellStyle(headerStyle);
        }
    }

    /** One row per 30-minute timeslot, one column per weekday-date, colored by activity. */
    private static void writeTimeslotRows(Sheet sheet, Teacher teacher, List<LocalDate> week, List<TimeSlot> gridSlots,
            List<TeacherSlot> allSlots, CellStyle teachingStyle, CellStyle breakStyle, CellStyle planningStyle) {
        for (int rowIndex = 0; rowIndex < gridSlots.size(); rowIndex++) {
            TimeSlot gridSlot = gridSlots.get(rowIndex);
            Row row = sheet.createRow(rowIndex + 1);
            row.createCell(0).setCellValue(gridSlot.start().format(TIME_FORMAT));

            for (int column = 0; column < week.size(); column++) {
                LocalDate date = week.get(column);
                SlotActivity activity = activityAt(allSlots, teacher, date, gridSlot.start());
                Cell cell = row.createCell(column + 1);
                CellStyle style = styleFor(activity, teachingStyle, breakStyle, planningStyle);
                if (style != null) {
                    cell.setCellStyle(style);
                }
                cell.setCellValue(label(activity));
            }
        }
    }

    private static SlotActivity activityAt(List<TeacherSlot> allSlots, Teacher teacher, LocalDate date, LocalTime start) {
        return allSlots.stream()
                .filter(teacherSlot -> teacherSlot.getTeacher().equals(teacher)
                        && teacherSlot.date().equals(date)
                        && teacherSlot.start().equals(start))
                .map(TeacherSlot::getActivity)
                .findFirst().orElse(null);
    }

    /**
     * Exhaustive over every {@link SlotActivity} variant (plus {@code null}, for a grid
     * cell with no matching {@link TeacherSlot}) rather than falling through to a default
     * - adding a 5th variant is a compile error here, not a silent mis-render. Off duty
     * (and no slot at all) gets no fill, leaving the cell at Excel's default background.
     */
    private static CellStyle styleFor(SlotActivity activity, CellStyle teachingStyle, CellStyle breakStyle, CellStyle planningStyle) {
        if (activity == null) {
            return null;
        }
        return switch (activity) {
            case SlotActivity.Teaching _ -> teachingStyle;
            case SlotActivity.Break _ -> breakStyle;
            case SlotActivity.PlanningTime _ -> planningStyle;
            case SlotActivity.OffDuty _ -> null;
        };
    }

    /** Exhaustive for the same reason as {@link #styleFor} - see its javadoc. */
    private static String label(SlotActivity activity) {
        if (activity == null) {
            return "";
        }
        return switch (activity) {
            case SlotActivity.Teaching teaching -> teaching.group().name();
            case SlotActivity.Break _ -> "Break";
            case SlotActivity.PlanningTime _ -> "Planning";
            case SlotActivity.OffDuty _ -> "";
        };
    }

    private static CellStyle coloredStyle(XSSFWorkbook workbook, IndexedColors color) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(color.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private static CellStyle headerStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    /** Excel sheet names cap at 31 chars and forbid {@code : \ / ? * [ ]}. */
    private static String sheetName(Teacher teacher) {
        String sanitized = teacher.name().replaceAll("[:\\\\/?*\\[\\]]", "_");
        return sanitized.length() > 31 ? sanitized.substring(0, 31) : sanitized;
    }
}
