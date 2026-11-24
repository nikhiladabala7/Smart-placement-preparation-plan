package preppath.model;

import java.io.Serializable;
import java.time.LocalDate;

/** Stores one placement-preparation outcome and its target date. */
public class Goal implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int id;
    private final String title;
    private final String description;
    private final LocalDate targetDate;

    /** Creates a goal with the supplied identifying and planning details. */
    public Goal(int id, String title, String description, LocalDate targetDate) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.targetDate = targetDate;
    }

    /** @return the unique identifier used to connect tasks to this goal */
    public int getId() { return id; }
    /** @return the short name shown in the goal list */
    public String getTitle() { return title; }
    /** @return the optional explanation of why the goal matters */
    public String getDescription() { return description; }
    /** @return the date by which the student hopes to reach the goal */
    public LocalDate getTargetDate() { return targetDate; }
}
