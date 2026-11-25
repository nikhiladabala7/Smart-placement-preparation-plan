# PrepPath — Smart Placement Preparation Tracker

PrepPath is a small, Java-first web application for organizing placement preparation. It brings goals, study tasks, practice scores, and progress summaries into one personal workspace. The progress shown is descriptive; it does not predict placement selection or job outcomes.

## Run it

Requirements: JDK 17 or newer. No third-party libraries or build tool are required.

1. Open a terminal in this folder.
2. Compile the Java source:

   ```powershell
   javac --add-modules jdk.httpserver -d out (Get-ChildItem -Recurse src\main\java\*.java | ForEach-Object FullName)
   ```

3. Start the application:

   ```powershell
   java --add-modules jdk.httpserver -cp out preppath.Main
   ```

4. Open <http://localhost:8080> in a browser. Press Ctrl+C in the terminal to stop the server.

The app creates `data/accounts.ser` for account details and a separate file under `data/users/` for each account's tracker. Passwords are stored as salted PBKDF2 hashes. To reset all local accounts and tracker data, stop the server and remove those generated files.

Open `/signup` to create an account or `/login` to return to an existing account. Usernames are 3–30 letters, numbers, dots, dashes, or underscores; passwords must be at least 8 characters. Signing out returns to the login screen. A server restart clears active sessions, so users sign in again; account and tracker files remain on disk.

## Deploy a shareable demo with Render

The included `Dockerfile` lets a Java host build and run the app. These steps use Render; other hosts that run Docker can use the same file.

1. Create a **private GitHub repository** and push the contents of this `preppath` folder to it. Keep the project files at the repository root so Render can find `Dockerfile`.
2. In Render, choose **New → Web Service** and connect that GitHub repository.
3. Select the **Docker** runtime and deploy. The app reads Render's `PORT` environment variable and serves the URL Render assigns.
4. After the first deploy, open the `onrender.com` link shown in the service dashboard.
5. Set the environment variable `COOKIE_SECURE` to `true` in the Render service settings so sign-in cookies are sent only over HTTPS.
6. For accounts and tracker data to survive redeploys, attach a persistent disk with mount path `/app/data`. Render's local filesystem is otherwise ephemeral; persistent disks are available on paid web services. Without a disk, account data may be lost during a redeploy.

Anyone with the public URL can create an account. Each account gets its own tracker file, but sessions are kept in server memory, so a restart signs everyone out. This is a course project implementation rather than a hardened production service; use sample information for a public demo.

## What is included

- Dashboard with task counts, completion, active goals, recent practice scores, and upcoming work.
- Goals with target dates and progress counts.
- Study tasks grouped by aptitude, programming, technical subjects, coding practice, and interviews.
- Task completion and deletion actions.
- Practice records with category, date, score, and notes.
- Java Study Agent that reviews open tasks, goals, and practice scores.
- Explainable suggestions that are added only after the student approves them.
- Account creation, sign-in, sign-out, and separate tracker files for each account.
- Salted PBKDF2 password hashes rather than readable saved passwords.
- Input validation and clear feedback for invalid submissions.
- Local persistence using Java object streams.
- Responsive browser UI served by the Java application.

## Simple architecture

- `preppath.auth`: account creation, password hashing, and credential checks.
- `preppath.agent`: Java rules that rank focus areas and prepare suggested tasks.
- `preppath.model`: plain Java classes for goals, study tasks, and practice records.
- `preppath.data`: loading, saving, and sample data in `TrackerRepository`.
- `preppath.web`: HTTP request handling, form parsing, and page rendering in `TrackerServer`.
- `preppath.Main`: starts the local web server.
- `src/main/resources/style.css`: visual design for the pages.

The server uses Java's built-in `HttpServer`, so the application stays easy to inspect and does not depend on a large framework. OOP, encapsulation, collections, interfaces, exceptions, date handling, file I/O, and event-driven form submissions are represented directly in the code.

Java classes, constructors, and methods include JavaDoc comments describing their responsibilities and key inputs or results. These comments are intended to make the code easier to study and explain during the project demonstration.

## Five-person work split

The project report lists five students and five registration numbers. The responsibilities below divide the work into modules with clear ownership; agree on shared model and route names before merging each part.

| Member | Registration no. | Ownership |
|---|---|---|
| A. Nikhil | AP25110010962 | Dashboard, progress summary and Study Agent; integrate with the project coordinator |
| Sajid | AP25110010919 | Goal and study-task models, task forms, completion flow |
| Rupesh | AP25110010331 | Practice record model, score entry, validation and performance summary |
| Siva Manikanta | AP25110010987 | Account storage, tracker persistence and storage error handling |
| Aslam Pasha | AP25110011013 | Page styling, responsive layout, navigation and documentation/demo flow |

The project coordinator is the student team leader and should be one of the five listed members. Prakash Ankur is the instructor, not the project coordinator. Confirm which team member is serving as coordinator and list that person accurately on the registration sheet.

## Scope note

The Study Agent is a rule-based Java planner, not a generative AI service. It prioritizes the lowest recorded practice areas and reduces new suggestions when many tasks are already open. It explains its reasoning and waits for the student to add suggestions. The app does not include cloud synchronization, email reminders, report export, or placement predictions.
