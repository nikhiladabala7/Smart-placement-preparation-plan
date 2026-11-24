package preppath.model;

import java.io.Serializable;
import java.time.LocalDate;

/** Stores the result and notes from one practice session or assessment. */
public class PracticeRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int id;
    private final String title;
    private final String category;
    private final int score;
    private final LocalDate date;
    private final String notes;

    /** Creates a practice record from the details entered for a session. */
    public PracticeRecord(int id, String title, String category, int score, LocalDate date, String notes) {
        this.id = id;
        this.title = title;
        this.category = category;
        this.score = score;
        this.date = date;
        this.notes = notes;
    }

    /** @return the unique identifier for this saved practice record */
    public int getId() { return id; }
    /** @return the student's name for the practice session */
    public String getTitle() { return title; }
    /** @return the preparation category practiced in this session */
    public String getCategory() { return category; }
    /** @return the recorded score, on a scale from 0 to 100 */
    public int getScore() { return score; }
    /** @return the date the practice session took place */
    public LocalDate getDate() { return date; }
    /** @return the student's optional reflection or revision note */
    public String getNotes() { return notes; }
}
