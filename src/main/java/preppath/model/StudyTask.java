package preppath.model;

import java.io.Serializable;
import java.time.LocalDate;

/** Stores a scheduled activity and whether the student has completed it. */
public class StudyTask implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int id;
    private final int goalId;
    private final String title;
    private final String category;
    private final LocalDate dueDate;
    private boolean completed;

    /**
     * Creates a task. A goal ID of zero means the task is not linked to a goal.
     */
    public StudyTask(int id, int goalId, String title, String category, LocalDate dueDate) {
        this.id = id;
        this.goalId = goalId;
        this.title = title;
        this.category = category;
        this.dueDate = dueDate;
        this.completed = false;
    }

    /** @return this task's unique identifier */
    public int getId() { return id; }
    /** @return the linked goal ID, or zero when no goal is linked */
    public int getGoalId() { return goalId; }
    /** @return the task description entered by the student */
    public String getTitle() { return title; }
    /** @return the preparation area this task belongs to */
    public String getCategory() { return category; }
    /** @return the task's target completion date */
    public LocalDate getDueDate() { return dueDate; }
    /** @return true when the student has marked the task complete */
    public boolean isCompleted() { return completed; }
    /** Updates the task's completion state after the student checks or unchecks it. */
    public void setCompleted(boolean completed) { this.completed = completed; }
}
