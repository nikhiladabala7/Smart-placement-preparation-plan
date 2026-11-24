package preppath.auth;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Handles account creation and sign-in using salted password hashes. */
public class AuthService {
    private static final int SALT_SIZE = 16;
    private static final int HASH_BITS = 256;
    private static final int HASH_ROUNDS = 120_000;

    private final Path accountsFile;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Account> accounts;

    /** Loads saved accounts from disk, or starts with an empty account list. */
    public AuthService(Path accountsFile) throws IOException {
        this.accountsFile = accountsFile;
        this.accounts = loadAccounts();
    }

    /**
     * Creates a new account after validating the username and password.
     * Passwords are stored as salted PBKDF2 hashes, never as readable text.
     *
     * @param username account name chosen by the student
     * @param password password chosen by the student
     * @return the normalized account name
     * @throws IllegalArgumentException if either value is invalid or the name is taken
     * @throws IOException if the account file cannot be saved
     */
    public synchronized String register(String username, String password) throws IOException {
        String normalized = normalizeUsername(username);
        validateUsername(normalized);
        validatePassword(password);
        if (accounts.containsKey(normalized)) {
            throw new IllegalArgumentException("That username is already in use. Try signing in instead.");
        }

        byte[] salt = new byte[SALT_SIZE];
        random.nextBytes(salt);
        accounts.put(normalized, new Account(salt, hashPassword(password, salt)));
        saveAccounts();
        return normalized;
    }

    /** Checks a password against the saved hash and returns the account name on success. */
    public synchronized String authenticate(String username, String password) {
        String normalized = normalizeUsername(username);
        Account account = accounts.get(normalized);
        if (account == null || password == null || password.isBlank()) {
            throw new IllegalArgumentException("Username or password is incorrect.");
        }

        byte[] submittedHash = hashPassword(password, account.salt);
        if (!MessageDigest.isEqual(account.passwordHash, submittedHash)) {
            throw new IllegalArgumentException("Username or password is incorrect.");
        }
        return normalized;
    }

    /** Reads saved credentials into memory; an unreadable file stops startup to avoid overwriting it. */
    private Map<String, Account> loadAccounts() throws IOException {
        if (!Files.exists(accountsFile)) return new HashMap<>();
        try (ObjectInputStream input = new ObjectInputStream(Files.newInputStream(accountsFile))) {
            Object saved = input.readObject();
            if (saved instanceof AccountData data) return new HashMap<>(data.accounts);
            throw new IOException("The saved account file has an unexpected format.");
        } catch (ClassNotFoundException exception) {
            throw new IOException("The saved account file could not be read.", exception);
        }
    }

    /** Writes a complete account snapshot to the local account file. */
    private void saveAccounts() throws IOException {
        Path parent = accountsFile.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        try (ObjectOutputStream output = new ObjectOutputStream(Files.newOutputStream(accountsFile))) {
            output.writeObject(new AccountData(accounts));
        }
    }

    /** Converts usernames to a stable case-insensitive form. */
    private String normalizeUsername(String username) {
        return username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
    }

    /** Allows simple usernames that are easy to type and safe to use as account names. */
    private void validateUsername(String username) {
        if (!username.matches("[a-z0-9._-]{3,30}")) {
            throw new IllegalArgumentException("Use 3–30 letters, numbers, dots, dashes, or underscores for your username.");
        }
    }

    /** Requires a basic minimum length and rejects unusually long password submissions. */
    private void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 128) {
            throw new IllegalArgumentException("Choose a password between 8 and 128 characters long.");
        }
    }

    /** Derives a one-way password hash using a unique salt and PBKDF2. */
    private byte[] hashPassword(String password, byte[] salt) {
        PBEKeySpec keySpec = new PBEKeySpec(password.toCharArray(), salt, HASH_ROUNDS, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(keySpec).getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Password hashing is not available in this Java runtime.", exception);
        } finally {
            keySpec.clearPassword();
        }
    }

    /** Stores one account's random salt and derived password hash. */
    private static class Account implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        private final byte[] salt;
        private final byte[] passwordHash;

        /** Keeps the generated salt and password hash for one account. */
        private Account(byte[] salt, byte[] passwordHash) {
            this.salt = salt;
            this.passwordHash = passwordHash;
        }
    }

    /** Groups account credentials so they can be saved in one object-stream file. */
    private static class AccountData implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        private final Map<String, Account> accounts;

        /** Takes a snapshot of the account map before it is written to disk. */
        private AccountData(Map<String, Account> accounts) {
            this.accounts = new HashMap<>(accounts);
        }
    }
}
