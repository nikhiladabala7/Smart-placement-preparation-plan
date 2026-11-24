package preppath.data;

import preppath.model.Goal;
import preppath.model.PracticeRecord;
import preppath.model.StudyTask;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Implements tracker storage with Java object streams.
 * Public operations are synchronized so simultaneous web requests cannot overwrite each other.
 */
public class FileTrackerRepository implements TrackerRepository {
    private final Path file;
    private TrackerData data;

    /** Loads saved data from the given file, or prepares sample data for a first run. */
    public FileTrackerRepository(Path file) {
        this.file = file;
        this.data = loadOrCreateSampleData();
    }

    /** Returns a date-sorted snapshot so callers cannot change the stored goal list. */
    @Override public synchronized List<Goal> getGoals() {
        return data.goals.stream().sorted(Comparator.comparing(Goal::getTargetDate)).toList();
    }

    /** Returns tasks in due-date order for the dashboard and task page. */
    @Override public synchronized List<StudyTask> getTasks() {
        return data.tasks.stream().sorted(Comparator.comparing(StudyTask::getDueDate)).toList();
    }

    /** Returns practice records with the most recent session at the top. */
    @Override public synchronized List<PracticeRecord> getPracticeRecords() {
        return data.practiceRecords.stream().sorted(Comparator.comparing(PracticeRecord::getDate).reversed()).toList();
    }

    /** Creates a new goal, adds it to memory, saves the file, and returns it. */
    @Override public synchronized Goal addGoal(String title, String description, LocalDate targetDate) throws IOException {
        Goal goal = new Goal(nextGoalId(), title, description, targetDate);
        data.goals.add(goal);
        save();
        return goal;
    }

    /** Creates a task with a new ID and persists it for the next application run. */
    @Override public synchronized StudyTask addTask(int goalId, String title, String category, LocalDate dueDate) throws IOException {
        StudyTask task = new StudyTask(nextTaskId(), goalId, title, category, dueDate);
        data.tasks.add(task);
        save();
        return task;
    }

    /** Stores a practice session and returns the record that was created. */
    @Override public synchronized PracticeRecord addPracticeRecord(String title, String category, int score, LocalDate date, String notes) throws IOException {
        PracticeRecord record = new PracticeRecord(nextPracticeId(), title, category, score, date, notes);
        data.practiceRecords.add(record);
        save();
        return record;
    }

    /** Finds a task by ID, flips its completion state, and saves it. */
    @Override public synchronized boolean markTaskComplete(int id) throws IOException {
        for (StudyTask task : data.tasks) {
            if (task.getId() == id) {
                task.setCompleted(!task.isCompleted());
                save();
                return true;
            }
        }
        return false;
    }

    /** Removes the matching task and saves only when a task was actually removed. */
    @Override public synchronized boolean deleteTask(int id) throws IOException {
        boolean removed = data.tasks.removeIf(task -> task.getId() == id);
        if (removed) save();
        return removed;
    }

    /** Generates the next goal ID by finding the current largest ID. */
    private int nextGoalId() { return data.goals.stream().mapToInt(Goal::getId).max().orElse(0) + 1; }
    /** Generates the next task ID without reusing an existing task ID. */
    private int nextTaskId() { return data.tasks.stream().mapToInt(StudyTask::getId).max().orElse(0) + 1; }
    /** Generates the next practice-record ID. */
    private int nextPracticeId() { return data.practiceRecords.stream().mapToInt(PracticeRecord::getId).max().orElse(0) + 1; }

    /**
     * Reads the saved tracker file. If the file is missing or unreadable,
     * creates a small sample workspace so the pages have example content.
     */
    private TrackerData loadOrCreateSampleData() {
        if (Files.exists(file)) {
            try (ObjectInputStream input = new ObjectInputStream(Files.newInputStream(file))) {
                Object saved = input.readObject();
                if (saved instanceof TrackerData trackerData) return trackerData;
            } catch (IOException | ClassNotFoundException exception) {
                System.err.println("Could not read saved tracker data. Starting with the sample workspace: " + exception.getMessage());
            }
        }
        return createSampleData();
    }

    /** Writes the complete tracker snapshot to disk, creating its folder if needed. */
    private void save() throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        try (ObjectOutputStream output = new ObjectOutputStream(Files.newOutputStream(file))) {
            output.writeObject(data);
        }
    }

    /** Supplies starter goals, tasks, and practice entries for a first-time demo. */
    private TrackerData createSampleData() {
        TrackerData sample = new TrackerData();
        sample.goals.add(new Goal(1, "Build a consistent routine", "Make time for focused practice throughout the week.", LocalDate.now().plusDays(21)));
        sample.goals.add(new Goal(2, "Strengthen coding fundamentals", "Practice core patterns and review problem-solving notes.", LocalDate.now().plusDays(35)));
        sample.tasks.add(new StudyTask(1, 1, "Review percentages and ratios", "Aptitude", LocalDate.now()));
        sample.tasks.add(new StudyTask(2, 2, "Solve two array problems", "Programming", LocalDate.now()));
        sample.tasks.add(new StudyTask(3, 2, "Revise OOP principles", "Technical subjects", LocalDate.now().plusDays(1)));
        sample.tasks.add(new StudyTask(4, 1, "Practice a short introduction", "Interview prep", LocalDate.now().plusDays(2)));
        sample.tasks.add(new StudyTask(5, 2, "Complete a timed coding set", "Coding practice", LocalDate.now().plusDays(3)));
        sample.practiceRecords.add(new PracticeRecord(1, "Quantitative basics", "Aptitude", 78, LocalDate.now().minusDays(1), "Ratios felt more comfortable today."));
        sample.practiceRecords.add(new PracticeRecord(2, "Arrays: easy set", "Programming", 65, LocalDate.now().minusDays(3), "Review edge cases before the next set."));
        return sample;
    }

    /** Serializable container that groups all saved tracker collections in one file. */
    private static class TrackerData implements Serializable {
        private static final long serialVersionUID = 1L;
        private final List<Goal> goals = new ArrayList<>();
        private final List<StudyTask> tasks = new ArrayList<>();
        private final List<PracticeRecord> practiceRecords = new ArrayList<>();
    }
}
