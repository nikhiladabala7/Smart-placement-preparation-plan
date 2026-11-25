package preppath.agent;

import preppath.model.Goal;
import preppath.model.PracticeRecord;
import preppath.model.StudyTask;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Reviews a student's tracker and prepares small, explainable next-step suggestions.
 * This Java-only agent uses clear planning rules rather than an external language model.
 */
public class StudyAgent {
    private static final List<String> CATEGORIES = List.of(
            "Aptitude", "Programming", "Technical subjects", "Coding practice", "Interview prep");

    /**
     * Reviews open work and practice scores, then prepares up to three suggested tasks.
     * If the student already has a large backlog, the plan recommends finishing current work first.
     *
     * @param goals the student's saved preparation goals
     * @param tasks the student's saved study tasks
     * @param practiceRecords the student's saved practice scores
     * @return an explanation, optional task suggestions, and current tasks to focus on
     */
    public StudyPlan createPlan(List<Goal> goals, List<StudyTask> tasks, List<PracticeRecord> practiceRecords) {
        List<StudyTask> openTasks = tasks.stream()
                .filter(task -> !task.isCompleted())
                .sorted(Comparator.comparing(StudyTask::getDueDate))
                .toList();

        if (openTasks.size() >= 6) {
            return new StudyPlan(
                    "You already have " + openTasks.size() + " open tasks. The best next step is to finish one before adding more.",
                    List.of(),
                    openTasks.stream().limit(3).toList());
        }

        Map<String, Double> averages = practiceRecords.stream().collect(Collectors.groupingBy(
                PracticeRecord::getCategory,
                Collectors.averagingInt(PracticeRecord::getScore)));
        Map<String, Long> openCounts = openTasks.stream().collect(Collectors.groupingBy(
                StudyTask::getCategory,
                Collectors.counting()));

        List<String> priorityOrder = CATEGORIES.stream()
                .sorted(Comparator
                        .comparingInt((String category) -> averages.containsKey(category) ? 0 : 1)
                        .thenComparingDouble(category -> averages.getOrDefault(category, 0.0))
                        .thenComparingLong(category -> openCounts.getOrDefault(category, 0L))
                        .thenComparing(category -> category))
                .toList();

        Goal nextGoal = goals.stream()
                .filter(goal -> isGoalStillActive(goal, tasks))
                .min(Comparator.comparing(Goal::getTargetDate))
                .orElse(null);

        // Reduce the number of new tasks as the current open-task count grows.
        int suggestionCount = Math.min(3, Math.max(1, 3 - openTasks.size() / 2));
        List<Suggestion> suggestions = new ArrayList<>();
        for (int index = 0; index < suggestionCount; index++) {
            String category = priorityOrder.get(index);
            String title = suggestedTitle(category);
            String reason = reasonFor(category, averages, openCounts);
            if (nextGoal != null) reason += " This can support your goal: " + nextGoal.getTitle() + ".";
            suggestions.add(new Suggestion(
                    title,
                    category,
                    LocalDate.now().plusDays(index + 1L),
                    nextGoal == null ? 0 : nextGoal.getId(),
                    reason));
        }

        String summary;
        if (practiceRecords.isEmpty()) {
            summary = "No practice scores are saved yet, so these suggestions start with areas that have fewer open tasks. Log a score later and the plan can use it to adjust its focus.";
        } else {
            String weakestCategory = averages.entrySet().stream()
                    .min(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("your preparation areas");
            int weakestAverage = (int) Math.round(averages.getOrDefault(weakestCategory, 0.0));
            summary = "Your lowest recorded practice average is " + weakestAverage + "% in " + weakestCategory
                    + ". Suggestions are ordered by practice results and how much work is already open in each area.";
        }

        if (openTasks.size() >= 3) {
            summary += " You have " + openTasks.size() + " open tasks, so this plan keeps the new workload small.";
        }
        return new StudyPlan(summary, List.copyOf(suggestions), List.of());
    }

    /** Treats a goal as active when it has no tasks yet or still has an incomplete task. */
    private boolean isGoalStillActive(Goal goal, List<StudyTask> tasks) {
        List<StudyTask> linkedTasks = tasks.stream()
                .filter(task -> task.getGoalId() == goal.getId())
                .toList();
        return linkedTasks.isEmpty() || linkedTasks.stream().anyMatch(task -> !task.isCompleted());
    }

    /** Chooses a short action phrase for the selected preparation category. */
    private String suggestedTitle(String category) {
        return switch (category) {
            case "Aptitude" -> "Complete a focused aptitude practice set";
            case "Programming" -> "Solve one programming problem and note the approach";
            case "Technical subjects" -> "Review one core technical concept";
            case "Coding practice" -> "Solve one timed coding problem";
            case "Interview prep" -> "Practise answering one interview question";
            default -> "Complete one focused preparation session";
        };
    }

    /** Explains why the selected area appears in the plan. */
    private String reasonFor(String category, Map<String, Double> averages, Map<String, Long> openCounts) {
        Double average = averages.get(category);
        if (average != null) {
            return "Your recorded average here is " + Math.round(average)
                    + "%. A short practice session gives you a focused way to revisit this area.";
        }
        if (openCounts.getOrDefault(category, 0L) == 0) {
            return "You have no open tasks in this area yet. A short session can help establish a steady routine.";
        }
        return "This area has fewer open tasks than some others, so one small follow-up can keep it moving.";
    }

    /** One suggested task and the reason it was selected. */
    public record Suggestion(String title, String category, LocalDate dueDate, int goalId, String reason) { }

    /** The agent's plain-language summary and its suggested next actions. */
    public record StudyPlan(String summary, List<Suggestion> suggestions, List<StudyTask> focusTasks) { }
}
