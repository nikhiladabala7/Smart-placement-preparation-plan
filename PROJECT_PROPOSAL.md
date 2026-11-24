# Project proposal: PrepPath

**Proposed title:** Java Smart Placement Preparation Tracker (PrepPath)

**Problem statement:** Students preparing for campus placements often use separate notebooks, spreadsheets, websites, and reminders to manage aptitude practice, programming, technical learning, coding exercises, and interview preparation. Because goals, tasks, practice sessions, and scores are spread across different tools, it can be hard to follow a consistent plan, identify topics that need more practice, and review preparation progress.

**Brief description:** PrepPath is a Java-based web application that provides a personal workspace for placement preparation. Students can create an account, sign in, set goals, categorize tasks, assign target dates, mark activities complete, record practice sessions and assessment scores, and review a progress dashboard. Each account has its own locally stored preparation records. The application presents task completion and recorded practice performance as descriptive summaries. It does not rank students, predict placement selection, or guarantee employment outcomes.

**Proposed implementation:** A small Java HTTP server handles pages, form submissions, validation, sign-in sessions, and application logic. Passwords are stored as salted PBKDF2 hashes; tracker records are stored separately for each account using Java object-stream files. Plain HTML and CSS provide the browser interface. Model classes, collections, interfaces, exception handling, and Java file I/O demonstrate course concepts without requiring a large framework.

**Approval status:** The team has confirmed that the project idea has already received instructor approval. The project coordinator is the student team leader; Prakash Ankur is the instructor.
