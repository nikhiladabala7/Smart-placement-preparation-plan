package preppath;

import preppath.auth.AuthService;
import preppath.web.TrackerServer;

import java.nio.file.Path;

/** Entry point for the PrepPath web application. */
public class Main {
    /**
     * Reads the port setting, prepares local storage, and starts the web server.
     * A command-line port overrides the hosting platform's {@code PORT} value;
     * local runs default to 8080.
     *
     * @param args optional command-line arguments; the first argument is the port
     * @throws Exception if the port is invalid or the local server cannot start
     */
    public static void main(String[] args) throws Exception {
        // Cloud hosts provide PORT; use 8080 by default when running locally.
        String configuredPort = args.length > 0 ? args[0] : System.getenv("PORT");
        int port = configuredPort == null || configuredPort.isBlank()
                ? 8080
                : Integer.parseInt(configuredPort);

        // Keep saved tracker data in the project's data folder between runs.
        Path dataDirectory = Path.of("data");
        AuthService authService = new AuthService(dataDirectory.resolve("accounts.ser"));
        TrackerServer server = new TrackerServer(authService, dataDirectory, port);
        server.start();
        System.out.println("PrepPath is running at http://localhost:" + port);
        System.out.println("Press Ctrl+C to stop the server.");
    }
}
