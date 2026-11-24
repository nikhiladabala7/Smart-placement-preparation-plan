package preppath.data;

import preppath.model.Goal;
import preppath.model.PracticeRecord;
import preppath.model.StudyTask;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/**
 * Describes the data operations the web application needs.
 * The interface keeps page handling separate from the file-storage details.
 */
public interface TrackerRepository {
    /** @return all goals, ordered by their target dates */
    List<Goal> getGoals();
    /** @return all study tasks, ordered by their due dates */
    List<StudyTask> getTasks();
    /** @return practice records, with the newest session first */
    List<PracticeRecord> getPracticeRecords();

    /**
     * Adds a goal and saves the updated tracker data.
     * @param title short name shown on the goals page
     * @param description optional explanation of the goal
     * @param targetDate date the student hopes to reach the goal
     * @return the newly created goal
     * @throws IOException if the updated tracker cannot be saved
     */
    Goal addGoal(String title, String description, LocalDate targetDate) throws IOException;
    /**
     * Adds a study task, optionally linked to a goal, and saves the change.
     * @param goalId linked goal identifier, or zero when no goal is linked
     * @param title task description
     * @param category preparation area for the task
     * @param dueDate target date for the task
     * @return the newly created task
     * @throws IOException if the updated tracker cannot be saved
     */
    StudyTask addTask(int goalId, String title, String category, LocalDate dueDate) throws IOException;
    /**
     * Saves a practice session and its score.
     * @param title name of the practice activity
     * @param category preparation area practiced
     * @param score result recorded on a 0-to-100 scale
     * @param date date the session took place
     * @param notes optional reflection from the student
     * @return the newly created practice record
     * @throws IOException if the updated tracker cannot be saved
     */
    PracticeRecord addPracticeRecord(String title, String category, int score, LocalDate date, String notes) throws IOException;
    /**
     * Toggles a task's complete state.
     * @param id identifier of the task to update
     * @return true if a matching task was updated; otherwise false
     * @throws IOException if the updated tracker cannot be saved
     */
    boolean markTaskComplete(int id) throws IOException;
    /**
     * Removes a task from the tracker.
     * @param id identifier of the task to remove
     * @return true if a task was removed; otherwise false
     * @throws IOException if the updated tracker cannot be saved
     */
    boolean deleteTask(int id) throws IOException;
}
