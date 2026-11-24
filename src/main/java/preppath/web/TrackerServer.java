package preppath.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import preppath.auth.AuthService;
import preppath.data.FileTrackerRepository;
import preppath.data.TrackerRepository;
import preppath.model.Goal;
import preppath.model.PracticeRecord;
import preppath.model.StudyTask;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;

/**
 * Runs PrepPath's browser pages and handles the forms submitted by the student.
 * Pages are assembled as HTML in Java so the project can be understood without a large framework.
 */
public class TrackerServer {
    private static final DateTimeFormatter DATE_LABEL = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final List<String> CATEGORIES = List.of("Aptitude", "Programming", "Technical subjects", "Coding practice", "Interview prep");
    private static final String SESSION_COOKIE = "PREPPATH_SESSION";
    private static final Duration SESSION_LENGTH = Duration.ofHours(12);

    private final AuthService authService;
    private final Path dataDirectory;
    private final int port;
    private final ConcurrentMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, TrackerRepository> repositories = new ConcurrentHashMap<>();
    private HttpServer server;

    /** Connects the website to account storage, per-user data storage, and a network port. */
    public TrackerServer(AuthService authService, Path dataDirectory, int port) {
        this.authService = authService;
        this.dataDirectory = dataDirectory;
        this.port = port;
    }

    /** Creates the HTTP server, registers the request handler, and begins listening. */
    public void start() throws IOException {
        // Bind on all network interfaces so a cloud host's proxy can reach the app.
        server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
    }

    /**
     * Creates the sign-in or sign-up screen without exposing tracker pages.
     * A small notice area is used to show validation errors and successful sign-out.
     */
    private String authPage(boolean signup, String notice) {
        String title = signup ? "Create your account" : "Welcome back";
        String subtitle = signup
                ? "Create a private space for your placement preparation."
                : "Sign in to continue with your preparation plan.";
        String action = signup ? "/auth/signup" : "/auth/login";
        String button = signup ? "Create account" : "Sign in";
        String accountSwitch = signup
                ? "Already have an account? <a href='/login'>Sign in</a>"
                : "New to PrepPath? <a href='/signup'>Create an account</a>";
        String flash = notice.isBlank() ? "" : "<div class='auth-notice' role='status'>" + escape(notice) + "</div>";
        String confirmation = signup
                ? "<label class='field'><span>Confirm password</span><input type='password' name='confirmPassword' autocomplete='new-password' required></label>"
                : "";
        String passwordHint = signup ? "<small class='password-hint'>Use at least 8 characters.</small>" : "";

        return "<!doctype html><html lang='en'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width, initial-scale=1'><meta name='theme-color' content='#f7f9f7'><title>" + escape(title) + " · PrepPath</title><link rel='stylesheet' href='/style.css'></head><body class='auth-body'><main class='auth-layout'><section class='auth-card'><a class='brand auth-brand' href='/login'><span class='brand-mark'>p</span><span>prep<span class='brand-light'>path</span><small>PLACEMENT PREP TRACKER</small></span></a><div class='auth-copy'><div class='eyebrow'>YOUR PREPARATION, IN ONE PLACE</div><h1>" + escape(title) + "</h1><p>" + escape(subtitle) + "</p></div>" + flash + "<form class='auth-form' method='post' action='" + action + "'><label class='field'><span>Username</span><input type='text' name='username' autocomplete='username' minlength='3' maxlength='30' placeholder='Choose a username' required></label><label class='field'><span>Password</span><input type='password' name='password' autocomplete='" + (signup ? "new-password" : "current-password") + "' minlength='8' maxlength='128' placeholder='" + (signup ? "At least 8 characters" : "Enter your password") + "' required></label>" + confirmation + passwordHint + "<button class='button button-primary full-button' type='submit'>" + button + " <span aria-hidden='true'>→</span></button></form><div class='auth-switch'>" + accountSwitch + "</div><div class='auth-security'><span aria-hidden='true'>◈</span> Each account keeps its own preparation records.</div></section><aside class='auth-art' aria-hidden='true'><div class='auth-orbit orbit-one'></div><div class='auth-orbit orbit-two'></div><div class='auth-spark'>✳</div><div class='auth-art-copy'><span>ONE STEP AT A TIME</span><strong>Your goals.<br>Your rhythm.<br>Your progress.</strong><small>Build a routine that works for you.</small></div></aside></main></body></html>";
    }

    /** Creates an expiring browser session and writes its cookie to the response. */
    private void startSession(HttpExchange exchange, String username) {
        String token = UUID.randomUUID().toString().replace("-", "");
        sessions.put(token, new Session(username, Instant.now().plus(SESSION_LENGTH)));
        String cookie = SESSION_COOKIE + "=" + token + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=" + SESSION_LENGTH.toSeconds();
        if (Boolean.parseBoolean(System.getenv("COOKIE_SECURE"))) cookie += "; Secure";
        exchange.getResponseHeaders().add("Set-Cookie", cookie);
    }

    /** Removes the current session and expires its browser cookie. */
    private void endSession(HttpExchange exchange) {
        String token = sessionToken(exchange);
        if (token != null) sessions.remove(token);
        String cookie = SESSION_COOKIE + "=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0";
        if (Boolean.parseBoolean(System.getenv("COOKIE_SECURE"))) cookie += "; Secure";
        exchange.getResponseHeaders().add("Set-Cookie", cookie);
    }

    /** Returns the username for a valid session, or null when the browser must sign in. */
    private String sessionUsername(HttpExchange exchange) {
        String token = sessionToken(exchange);
        if (token == null) return null;
        Session session = sessions.get(token);
        if (session == null) return null;
        if (session.expiresAt().isBefore(Instant.now())) {
            sessions.remove(token);
            return null;
        }
        return session.username();
    }

    /** Reads the PrepPath session token from the browser's Cookie header. */
    private String sessionToken(HttpExchange exchange) {
        List<String> cookies = exchange.getRequestHeaders().get("Cookie");
        if (cookies == null) return null;
        for (String header : cookies) {
            for (String part : header.split(";")) {
                String cookie = part.strip();
                if (cookie.startsWith(SESSION_COOKIE + "=")) {
                    return cookie.substring((SESSION_COOKIE + "=").length());
                }
            }
        }
        return null;
    }

    /** Opens the signed-in user's own tracker file, using a hash as a safe filename. */
    private TrackerRepository repositoryFor(String username) {
        return repositories.computeIfAbsent(username, name -> {
            try {
                byte[] digest = MessageDigest.getInstance("SHA-256").digest(name.getBytes(StandardCharsets.UTF_8));
                String fileName = HexFormat.of().formatHex(digest) + ".ser";
                return new FileTrackerRepository(dataDirectory.resolve("users").resolve(fileName));
            } catch (java.security.NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 is not available in this Java runtime.", exception);
            }
        });
    }

    /** Holds a signed-in username and the time at which its browser session expires. */
    private record Session(String username, Instant expiresAt) { }

    /**
     * Routes each browser request to a page, stylesheet, or form action.
     * It also converts invalid input and unexpected errors into readable responses.
     */
    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        try {
            String method = exchange.getRequestMethod();
            if ("GET".equals(method) && "/style.css".equals(path)) {
                sendCss(exchange);
                return;
            }

            // Sign-in and account creation are public; tracker pages require a session.
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            String notice = query.getOrDefault("notice", "");
            if ("GET".equals(method) && ("/login".equals(path) || "/signup".equals(path))) {
                if (sessionUsername(exchange) != null) redirect(exchange, "/", "You are already signed in.");
                else send(exchange, 200, authPage("/signup".equals(path), notice));
                return;
            }
            if ("POST".equals(method) && ("/auth/login".equals(path) || "/auth/signup".equals(path) || "/auth/logout".equals(path))) {
                handleAuthPost(exchange, path, parseForm(exchange));
                return;
            }

            String username = sessionUsername(exchange);
            if (username == null) {
                redirect(exchange, "/login", "Please sign in to continue.");
                return;
            }
            TrackerRepository repository = repositoryFor(username);
            if ("POST".equals(method)) {
                handlePost(exchange, path, parseForm(exchange), repository);
                return;
            }
            if (!"GET".equals(method)) {
                send(exchange, 405, page("Method not allowed", "", "Use the forms on this page to update your tracker."));
                return;
            }
            switch (path) {
                case "/" -> send(exchange, 200, dashboard(repository, notice));
                case "/tasks" -> send(exchange, 200, tasksPage(repository, notice));
                case "/goals" -> send(exchange, 200, goalsPage(repository, notice));
                case "/practice" -> send(exchange, 200, practicePage(repository, notice));
                default -> send(exchange, 404, page("Page not found", "", "That page is not part of PrepPath."));
            }
        } catch (IllegalArgumentException exception) {
            if ("/auth/login".equals(path) || "/auth/signup".equals(path)) {
                send(exchange, 400, authPage("/auth/signup".equals(path), exception.getMessage()));
            } else {
                send(exchange, 400, page("Check your entry", "", exception.getMessage()));
            }
        } catch (Exception exception) {
            exception.printStackTrace();
            send(exchange, 500, page("Something went wrong", "", "The request could not be completed. Your saved tracker data has not been removed."));
        } finally {
            exchange.close();
        }
    }

    /** Creates a session after valid credentials or clears the current session on sign-out. */
    private void handleAuthPost(HttpExchange exchange, String path, Map<String, String> form) throws IOException {
        if ("/auth/logout".equals(path)) {
            endSession(exchange);
            redirect(exchange, "/login", "You have signed out.");
            return;
        }

        String username = required(form, "username", "Enter your username.", 30);
        String password = form.getOrDefault("password", "");
        if (password.isBlank()) throw new IllegalArgumentException("Enter your password.");

        if ("/auth/signup".equals(path)) {
            if (!password.equals(form.getOrDefault("confirmPassword", ""))) {
                throw new IllegalArgumentException("The passwords do not match. Enter them again.");
            }
            username = authService.register(username, password);
            startSession(exchange, username);
            redirect(exchange, "/", "Your account is ready.");
        } else {
            username = authService.authenticate(username, password);
            startSession(exchange, username);
            redirect(exchange, "/", "Welcome back.");
        }
    }

    /** Validates a submitted tracker form, updates the signed-in user's data, and redirects. */
    private void handlePost(HttpExchange exchange, String path, Map<String, String> form, TrackerRepository repository) throws IOException {
        switch (path) {
            case "/actions/goals/add" -> {
                String title = required(form, "title", "Add a short title for your goal.", 80);
                String description = clean(form.getOrDefault("description", ""), 180);
                LocalDate targetDate = date(form, "targetDate", "Choose a target date for your goal.");
                repository.addGoal(title, description, targetDate);
                redirect(exchange, "/goals", "Goal added to your plan.");
            }
            case "/actions/tasks/add" -> {
                String title = required(form, "title", "Add a short title for this task.", 100);
                String category = category(form.get("category"));
                LocalDate dueDate = date(form, "dueDate", "Choose a target date for this task.");
                int goalId = integer(form.getOrDefault("goalId", "0"), "Choose a valid goal.");
                if (goalId != 0 && repository.getGoals().stream().noneMatch(goal -> goal.getId() == goalId)) {
                    throw new IllegalArgumentException("That goal is no longer available. Refresh and choose another.");
                }
                repository.addTask(goalId, title, category, dueDate);
                redirect(exchange, "/tasks", "Task added to your plan.");
            }
            case "/actions/tasks/toggle" -> {
                if (repository.markTaskComplete(integer(form.get("id"), "Choose a valid task."))) redirect(exchange, "/tasks", "Task status updated.");
                else redirect(exchange, "/tasks", "That task could not be found.");
            }
            case "/actions/tasks/delete" -> {
                if (repository.deleteTask(integer(form.get("id"), "Choose a valid task."))) redirect(exchange, "/tasks", "Task removed.");
                else redirect(exchange, "/tasks", "That task could not be found.");
            }
            case "/actions/practice/add" -> {
                String title = required(form, "title", "Add a title for this practice session.", 80);
                String category = category(form.get("category"));
                int score = integer(form.get("score"), "Enter a score from 0 to 100.");
                if (score < 0 || score > 100) throw new IllegalArgumentException("Scores must be between 0 and 100.");
                LocalDate date = date(form, "date", "Choose the date of your practice session.");
                String notes = clean(form.getOrDefault("notes", ""), 180);
                repository.addPracticeRecord(title, category, score, date, notes);
                redirect(exchange, "/practice", "Practice session saved.");
            }
            default -> send(exchange, 404, page("Action not found", "", "This form action is not part of PrepPath."));
        }
    }

    /** Builds the overview page with task, goal, completion, and practice summaries. */
    private String dashboard(TrackerRepository repository, String notice) {
        List<StudyTask> tasks = repository.getTasks();
        List<Goal> goals = repository.getGoals();
        List<PracticeRecord> practice = repository.getPracticeRecords();
        long completed = tasks.stream().filter(StudyTask::isCompleted).count();
        long pending = tasks.size() - completed;
        int completion = tasks.isEmpty() ? 0 : (int) Math.round(completed * 100.0 / tasks.size());
        int average = practice.isEmpty() ? 0 : (int) Math.round(practice.stream().mapToInt(PracticeRecord::getScore).average().orElse(0));
        List<StudyTask> nextTasks = tasks.stream().filter(task -> !task.isCompleted()).limit(4).toList();

        StringBuilder body = new StringBuilder();
        body.append("<section class='welcome panel'><div><div class='eyebrow'>YOUR PREPARATION, IN ONE PLACE</div><h1>A little progress adds up.</h1><p>Keep your goals visible, make time for focused practice, and notice what you’re improving.</p><a class='button button-light' href='/tasks'>Plan a study session <span aria-hidden='true'>→</span></a></div><div class='welcome-mark' aria-hidden='true'><span>✳</span><i></i><b></b></div></section>");
        body.append("<section class='stat-grid' aria-label='Preparation overview'>")
                .append(stat("Open tasks", String.valueOf(pending), "Across your study plan", "○", "mint"))
                .append(stat("Goals in progress", String.valueOf(goals.size()), "Keep your next step clear", "◎", "lavender"))
                .append(stat("Plan complete", completion + "%", completed + " of " + tasks.size() + " tasks done", "↗", "peach"))
                .append(stat("Practice average", practice.isEmpty() ? "—" : average + "%", practice.isEmpty() ? "Add your first session" : "From " + practice.size() + " saved sessions", "✳", "blue"))
                .append("</section>");
        body.append("<div class='two-column dashboard-columns'><section class='panel section-panel'><div class='section-heading'><div><div class='eyebrow'>NEXT ON YOUR PLAN</div><h2>Upcoming tasks</h2></div><a class='text-link' href='/tasks'>View all <span aria-hidden='true'>→</span></a></div>");
        if (nextTasks.isEmpty()) body.append(empty("You’re all caught up", "Add a task to keep your preparation moving.", "/tasks", "Add a task"));
        else body.append(taskRows(nextTasks, false));
        body.append("</section><section class='panel section-panel'><div class='section-heading'><div><div class='eyebrow'>YOUR DIRECTION</div><h2>Active goals</h2></div><a class='text-link' href='/goals'>View all <span aria-hidden='true'>→</span></a></div>");
        if (goals.isEmpty()) body.append(empty("No goals yet", "Choose one outcome you want to work toward.", "/goals", "Create a goal"));
        else body.append(goalCards(goals.stream().limit(3).toList(), tasks));
        body.append("</section></div>");
        body.append("<section class='panel section-panel recent-panel'><div class='section-heading'><div><div class='eyebrow'>PRACTICE LOG</div><h2>Recent sessions</h2></div><a class='text-link' href='/practice'>Open practice log <span aria-hidden='true'>→</span></a></div>");
        if (practice.isEmpty()) body.append(empty("No sessions recorded", "Save a practice score to see your learning history here.", "/practice", "Log a session"));
        else body.append(practiceRows(practice.stream().limit(3).toList()));
        body.append("</section>");
        return page("Dashboard", body.toString(), notice);
    }

    /** Builds the page for viewing, adding, completing, and removing study tasks. */
    private String tasksPage(TrackerRepository repository, String notice) {
        List<StudyTask> tasks = repository.getTasks();
        StringBuilder body = new StringBuilder("<div class='page-intro'><div><div class='eyebrow'>MAKE A PLAN YOU CAN FOLLOW</div><h1>Study tasks</h1><p>Break placement preparation into small, trackable steps.</p></div><span class='page-icon mint'>✓</span></div>");
        body.append("<div class='two-column content-columns'><section class='panel section-panel'><div class='section-heading'><div><div class='eyebrow'>YOUR CHECKLIST</div><h2>All tasks <span class='count-pill'>").append(tasks.size()).append("</span></h2></div></div>");
        if (tasks.isEmpty()) body.append(empty("Your list is clear", "Add a task using the form to start planning.", "#add-task", "Create your first task"));
        else body.append(taskRows(tasks, true));
        body.append("</section>");
        body.append("<section class='panel form-panel' id='add-task'><div class='eyebrow'>ADD TO YOUR PLAN</div><h2>New task</h2><p class='form-hint'>Choose a category and a date that feels realistic.</p><form method='post' action='/actions/tasks/add'>")
                .append(input("Task title", "title", "e.g. Practice two array problems", "text", true))
                .append(select("Category", "category", CATEGORIES, CATEGORIES.get(0), true))
                .append(goalSelect(repository.getGoals()))
                .append(input("Target date", "dueDate", "", "date", true))
                .append("<button class='button button-primary full-button' type='submit'>Add task <span aria-hidden='true'>→</span></button></form></section></div>");
        return page("Study tasks", body.toString(), notice);
    }

    /** Builds the goals page and shows progress from tasks linked to each goal. */
    private String goalsPage(TrackerRepository repository, String notice) {
        List<Goal> goals = repository.getGoals();
        List<StudyTask> tasks = repository.getTasks();
        StringBuilder body = new StringBuilder("<div class='page-intro'><div><div class='eyebrow'>FOCUS ON THE OUTCOME</div><h1>Your goals</h1><p>Keep the reason behind your preparation easy to see.</p></div><span class='page-icon lavender'>◎</span></div>");
        body.append("<div class='two-column content-columns'><section class='panel section-panel'><div class='section-heading'><div><div class='eyebrow'>WHAT YOU’RE WORKING TOWARD</div><h2>Goals <span class='count-pill'>").append(goals.size()).append("</span></h2></div></div>");
        if (goals.isEmpty()) body.append(empty("Start with one goal", "A clear goal can make it easier to choose your next task.", "#add-goal", "Create a goal"));
        else body.append(goalCards(goals, tasks));
        body.append("</section><section class='panel form-panel' id='add-goal'><div class='eyebrow'>SET A DIRECTION</div><h2>New goal</h2><p class='form-hint'>Keep it specific and within your control.</p><form method='post' action='/actions/goals/add'>")
                .append(input("Goal title", "title", "e.g. Complete a week of aptitude practice", "text", true))
                .append("<label class='field'><span>What will this help you do? <em>Optional</em></span><textarea name='description' maxlength='180' rows='3' placeholder='Add a short note to remind yourself why it matters'></textarea></label>")
                .append(input("Target date", "targetDate", "", "date", true))
                .append("<button class='button button-primary full-button' type='submit'>Save goal <span aria-hidden='true'>→</span></button></form></section></div>");
        return page("Goals", body.toString(), notice);
    }

    /** Builds the practice history page and its form for adding a scored session. */
    private String practicePage(TrackerRepository repository, String notice) {
        List<PracticeRecord> records = repository.getPracticeRecords();
        int average = records.isEmpty() ? 0 : (int) Math.round(records.stream().mapToInt(PracticeRecord::getScore).average().orElse(0));
        StringBuilder body = new StringBuilder("<div class='page-intro'><div><div class='eyebrow'>NOTICE YOUR PROGRESS</div><h1>Practice log</h1><p>Record practice and assessments so you can review them over time.</p></div><span class='page-icon peach'>✳</span></div>");
        body.append("<section class='practice-summary panel'><div><div class='eyebrow'>SAVED SESSIONS</div><strong>").append(records.size()).append("</strong><span>practice ").append(records.size() == 1 ? "session" : "sessions").append(" recorded</span></div><div class='summary-divider'></div><div><div class='eyebrow'>RECORDED AVERAGE</div><strong>").append(records.isEmpty() ? "—" : average + "%").append("</strong><span>based on saved scores</span></div><a class='button button-primary' href='#add-practice'>Log a session <span aria-hidden='true'>→</span></a></section>");
        body.append("<div class='two-column content-columns'><section class='panel section-panel'><div class='section-heading'><div><div class='eyebrow'>YOUR LEARNING HISTORY</div><h2>Sessions</h2></div></div>");
        if (records.isEmpty()) body.append(empty("Your practice log is empty", "Save a session to begin building your own record.", "#add-practice", "Log your first session"));
        else body.append(practiceRows(records));
        body.append("</section><section class='panel form-panel' id='add-practice'><div class='eyebrow'>CAPTURE A SESSION</div><h2>New practice record</h2><p class='form-hint'>Use the score from your practice set or assessment.</p><form method='post' action='/actions/practice/add'>")
                .append(input("Session name", "title", "e.g. Verbal ability mock set", "text", true))
                .append(select("Category", "category", CATEGORIES, CATEGORIES.get(0), true))
                .append(input("Date", "date", "", "date", true))
                .append(input("Score", "score", "0 to 100", "number", true, " min='0' max='100' step='1'"))
                .append("<label class='field'><span>Notes <em>Optional</em></span><textarea name='notes' maxlength='180' rows='3' placeholder='What felt easy? What will you revisit?'></textarea></label>")
                .append("<button class='button button-primary full-button' type='submit'>Save practice record <span aria-hidden='true'>→</span></button></form></section></div>");
        return page("Practice log", body.toString(), notice);
    }

    /** Renders task rows; the full task page can also show a delete control. */
    private String taskRows(List<StudyTask> tasks, boolean showActions) {
        StringBuilder html = new StringBuilder("<div class='task-list'>");
        for (StudyTask task : tasks) {
            html.append("<article class='task-row ").append(task.isCompleted() ? "is-complete" : "").append("'><form method='post' action='/actions/tasks/toggle' class='task-check-form'><input type='hidden' name='id' value='").append(task.getId()).append("'><button class='check-button ").append(task.isCompleted() ? "checked" : "").append("' type='submit' aria-label='").append(task.isCompleted() ? "Mark incomplete" : "Mark complete").append("'>").append(task.isCompleted() ? "✓" : "").append("</button></form><div class='task-main'><strong>").append(escape(task.getTitle())).append("</strong><div class='task-meta'><span class='tag'>").append(escape(task.getCategory())).append("</span><span>").append(dateLabel(task.getDueDate())).append("</span></div></div>");
            if (showActions) html.append("<form method='post' action='/actions/tasks/delete' class='delete-form'><input type='hidden' name='id' value='").append(task.getId()).append("'><button class='quiet-button' type='submit' aria-label='Delete ").append(escape(task.getTitle())).append("' title='Remove task'>×</button></form>");
            html.append("</article>");
        }
        return html.append("</div>").toString();
    }

    /** Renders goal cards and calculates each goal's completion from its linked tasks. */
    private String goalCards(List<Goal> goals, List<StudyTask> tasks) {
        StringBuilder html = new StringBuilder("<div class='goal-list'>");
        for (Goal goal : goals) {
            List<StudyTask> linked = tasks.stream().filter(task -> task.getGoalId() == goal.getId()).toList();
            long done = linked.stream().filter(StudyTask::isCompleted).count();
            int progress = linked.isEmpty() ? 0 : (int) Math.round(done * 100.0 / linked.size());
            html.append("<article class='goal-card'><div class='goal-top'><span class='goal-dot'></span><span class='goal-date'>BY ").append(dateLabel(goal.getTargetDate()).toUpperCase(Locale.ENGLISH)).append("</span></div><h3>").append(escape(goal.getTitle())).append("</h3>");
            if (!goal.getDescription().isBlank()) html.append("<p>").append(escape(goal.getDescription())).append("</p>");
            html.append("<div class='progress-label'><span>").append(linked.size()).append(" linked ").append(linked.size() == 1 ? "task" : "tasks").append("</span><strong>").append(progress).append("%</strong></div><div class='progress-track'><span style='width:").append(progress).append("%'></span></div></article>");
        }
        return html.append("</div>").toString();
    }

    /** Renders practice entries with their category, date, score, and optional notes. */
    private String practiceRows(List<PracticeRecord> records) {
        StringBuilder html = new StringBuilder("<div class='practice-list'>");
        for (PracticeRecord record : records) {
            html.append("<article class='practice-row'><div class='practice-badge'>").append(escape(record.getCategory().substring(0, 1))).append("</div><div class='practice-main'><strong>").append(escape(record.getTitle())).append("</strong><div class='task-meta'><span class='tag'>").append(escape(record.getCategory())).append("</span><span>").append(dateLabel(record.getDate())).append("</span></div>");
            if (!record.getNotes().isBlank()) html.append("<p>").append(escape(record.getNotes())).append("</p>");
            html.append("</div><div class='score-pill'>").append(record.getScore()).append("<small>%</small></div></article>");
        }
        return html.append("</div>").toString();
    }

    /** Creates one compact dashboard card for a numeric preparation summary. */
    private String stat(String label, String value, String detail, String icon, String color) {
        return "<article class='stat-card'><div class='stat-top'><span>" + escape(label) + "</span><span class='stat-icon " + color + "' aria-hidden='true'>" + icon + "</span></div><strong>" + value + "</strong><small>" + escape(detail) + "</small></article>";
    }

    /** Wraps page content in the shared navigation, header, notice, and footer. */
    private String page(String title, String body, String notice) {
        String flash = notice.isBlank() ? "" : "<div class='notice' role='status'><span aria-hidden='true'>✓</span> " + escape(notice) + "</div>";
        return "<!doctype html><html lang='en'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width, initial-scale=1'><meta name='theme-color' content='#f7f8f6'><title>" + escape(title) + " · PrepPath</title><link rel='stylesheet' href='/style.css'></head><body><div class='app-shell'><aside class='sidebar'><a class='brand' href='/'><span class='brand-mark'>p</span><span>prep<span class='brand-light'>path</span><small>PLACEMENT PREP TRACKER</small></span></a><div class='nav-caption'>WORKSPACE</div><nav aria-label='Main navigation'><a class='nav-link " + ("Dashboard".equals(title) ? "active" : "") + "' href='/'><span class='nav-icon'>◫</span> Dashboard</a><a class='nav-link " + ("Study tasks".equals(title) ? "active" : "") + "' href='/tasks'><span class='nav-icon'>☷</span> Study tasks</a><a class='nav-link " + ("Goals".equals(title) ? "active" : "") + "' href='/goals'><span class='nav-icon'>◎</span> Goals</a><a class='nav-link " + ("Practice log".equals(title) ? "active" : "") + "' href='/practice'><span class='nav-icon'>↗</span> Practice log</a></nav><div class='sidebar-bottom'><div class='sidebar-note'><span class='note-spark'>✳</span><strong>Steady beats perfect.</strong><p>Small steps count. Keep showing up for yourself.</p></div><div class='profile-chip'><span class='avatar'>S</span><span><strong>Student workspace</strong><small>My preparation plan</small></span></div></div></aside><main class='main-area'><header class='topbar'><div class='mobile-brand'><span class='brand-mark'>p</span> PrepPath</div><div class='crumb'>Workspace <span>/</span> <strong>" + escape(title) + "</strong></div><div class='topbar-right'><span class='today-label'>" + LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.ENGLISH)) + "</span><form class='logout-form' method='post' action='/auth/logout'><button class='logout-button' type='submit'>Sign out</button></form><span class='top-avatar'>S</span></div></header><div class='content-wrap'>" + flash + body + "<footer class='footer'>PrepPath <span>·</span> A personal space for steady preparation</footer></div></main></div></body></html>";
    }

    /** Shows a friendly message and a link when a list has no entries yet. */
    private String empty(String title, String description, String href, String linkText) {
        return "<div class='empty-state'><span class='empty-illustration' aria-hidden='true'>✳</span><strong>" + escape(title) + "</strong><p>" + escape(description) + "</p><a class='text-link' href='" + href + "'>" + escape(linkText) + " →</a></div>";
    }

    /** Builds a labeled form input without extra HTML attributes. */
    private String input(String label, String name, String placeholder, String type, boolean required) {
        return input(label, name, placeholder, type, required, "");
    }

    /** Builds a labeled input and appends any type-specific attributes, such as score limits. */
    private String input(String label, String name, String placeholder, String type, boolean required, String extra) {
        return "<label class='field'><span>" + escape(label) + "</span><input type='" + type + "' name='" + name + "' placeholder='" + escape(placeholder) + "'" + (required ? " required" : "") + extra + "></label>";
    }

    /** Builds a dropdown whose choices come from the application's allowed values. */
    private String select(String label, String name, List<String> options, String selected, boolean required) {
        StringBuilder html = new StringBuilder("<label class='field'><span>").append(escape(label)).append("</span><select name='").append(name).append("'").append(required ? " required" : "").append(">");
        for (String option : options) html.append("<option value='").append(escape(option)).append("'").append(option.equals(selected) ? " selected" : "").append(">").append(escape(option)).append("</option>");
        return html.append("</select></label>").toString();
    }

    /** Builds the optional goal dropdown used when a student creates a study task. */
    private String goalSelect(List<Goal> goals) {
        StringBuilder html = new StringBuilder("<label class='field'><span>Connect to a goal <em>Optional</em></span><select name='goalId'><option value='0'>No linked goal</option>");
        for (Goal goal : goals) html.append("<option value='").append(goal.getId()).append("'>").append(escape(goal.getTitle())).append("</option>");
        return html.append("</select></label>").toString();
    }

    /** Trims a form value and rejects it when it is blank or longer than allowed. */
    private String required(Map<String, String> form, String key, String message, int maxLength) {
        String value = clean(form.getOrDefault(key, ""), maxLength);
        if (value.isBlank()) throw new IllegalArgumentException(message);
        return value;
    }

    /** Trims optional text and enforces a maximum length before it is stored or displayed. */
    private String clean(String value, int maxLength) {
        String result = value == null ? "" : value.strip();
        if (result.length() > maxLength) throw new IllegalArgumentException("Please keep text to " + maxLength + " characters or fewer.");
        return result;
    }

    /** Accepts only one of the categories displayed in the forms. */
    private String category(String value) {
        if (!CATEGORIES.contains(value)) throw new IllegalArgumentException("Choose one of the listed preparation categories.");
        return value;
    }

    /** Parses a required ISO date from a form and provides a clear message for bad dates. */
    private LocalDate date(Map<String, String> form, String key, String message) {
        try { return LocalDate.parse(required(form, key, message, 10)); }
        catch (java.time.format.DateTimeParseException exception) { throw new IllegalArgumentException("Enter a valid date."); }
    }

    /** Converts a form value to an integer, replacing parsing errors with a user-friendly message. */
    private int integer(String value, String message) {
        try { return Integer.parseInt(value); }
        catch (Exception exception) { throw new IllegalArgumentException(message); }
    }

    /** Formats a date for compact labels in task and goal cards. */
    private String dateLabel(LocalDate date) { return date.format(DATE_LABEL); }

    /** Escapes special HTML characters so user-entered text displays as text, not markup. */
    private String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    /** Reads a submitted form body and parses its URL-encoded fields. */
    private Map<String, String> parseForm(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return parseQuery(body);
    }

    /** Splits URL-encoded key/value pairs and decodes them into a map. */
    private Map<String, String> parseQuery(String query) {
        Map<String, String> values = new HashMap<>();
        if (query == null || query.isBlank()) return values;
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            values.put(key, value);
        }
        return values;
    }

    /** Serves the local stylesheet that gives the pages their layout and visual design. */
    private void sendCss(HttpExchange exchange) throws IOException {
        Path cssPath = Path.of("src", "main", "resources", "style.css");
        String css = Files.exists(cssPath) ? Files.readString(cssPath) : "body{font:16px Arial,sans-serif;padding:3rem;color:#26352f}";
        byte[] bytes = css.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/css; charset=utf-8");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    /** Sends an HTML response with the requested HTTP status and browser-safe headers. */
    private void send(HttpExchange exchange, int status, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    /** Redirects after a successful form action and adds a short confirmation notice. */
    private void redirect(HttpExchange exchange, String path, String notice) throws IOException {
        exchange.getResponseHeaders().set("Location", path + "?notice=" + URLEncoder.encode(notice, StandardCharsets.UTF_8));
        exchange.sendResponseHeaders(303, -1);
    }
}
