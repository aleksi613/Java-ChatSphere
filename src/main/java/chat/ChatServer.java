package chat;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.PrintWriter;
import java.io.BufferedInputStream;
import java.sql.*;
import java.util.*;

// For the AI integration
import okhttp3.*;
import com.google.gson.*;

public class ChatServer {

    private static final int PORT = 12345;
    private static final Set<ClientHandler> clients =
        Collections.synchronizedSet(new HashSet<>());
    private static final Map<String, Set<ClientHandler>> chatRooms =
        Collections.synchronizedMap(new HashMap<>());

    // Database Connection
    private static java.sql.Connection dbConnection;

    // AI Helper (OpenAI)
    private static AIHelper aiHelper;

    static {
        // 1) SQLite DB init
        try {
            dbConnection = DriverManager.getConnection("jdbc:sqlite:chat.db");
            initializeDatabase();
        } catch (SQLException e) {
            System.err.println("Database connection error: " + e.getMessage());
            System.exit(1);
        }

        // 2) AI Helper init
        String apiKey = System.getenv("OPENAI_API_KEY"); // Set in your environment variables locally
        if (apiKey != null && !apiKey.isEmpty()) {
            aiHelper = new AIHelper(apiKey);
            System.out.println("AI Helper initialized with provided OPENAI_API_KEY.");
        } else {
            System.out.println("No OPENAI_API_KEY found. AI commands will not function.");
        }
    }

    public static void main(String[] args) throws IOException {
        System.out.println("Chat server started on port: " + PORT);
        ServerSocket serverSocket = new ServerSocket(PORT);

        try {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress());

                ClientHandler clientHandler = new ClientHandler(clientSocket);
                clients.add(clientHandler);
                clientHandler.start();
            }
        } finally {
            serverSocket.close();
            try {
                dbConnection.close();
            } catch (SQLException e) {
                System.err.println("Error closing database connection: " + e.getMessage());
            }
        }
    }

    private static void initializeDatabase() throws SQLException {
        try (Statement stmt = dbConnection.createStatement()) {
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS messages (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "room TEXT NOT NULL, " +
                "username TEXT NOT NULL, " +
                "message TEXT NOT NULL, " +
                "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP" +
                ")"
            );
        }
    }

    private static void broadcastStatus() {
        int totalUsers = clients.size();
        Map<String,Integer> roomCount = new HashMap<>();

        synchronized (chatRooms) {
            for (String room : chatRooms.keySet()) {
                roomCount.put(room, chatRooms.get(room).size());
            }
        }

        // Sort rooms by user count, description
        List<Map.Entry<String,Integer>> list = new ArrayList<>(roomCount.entrySet());
        list.sort((a,b)-> b.getValue().compareTo(a.getValue()));

        StringBuilder sb = new StringBuilder();
        sb.append("STATUS: ").append(totalUsers).append(" users total. ");
        sb.append("| Rooms: ");

        int limit = Math.min(5, list.size());
        for (int i = 0; i < limit; i++) {
            Map.Entry<String,Integer> e = list.get(i);
            sb.append(e.getKey()).append("(").append(e.getValue()).append(")");
            if (i < limit - 1) {
                sb.append(", ");
            }
        }

        String statusLine = sb.toString();

        synchronized (clients) {
            for (ClientHandler c : clients) {
                c.out.println(statusLine);
            }
        }
    }

    // ------------------------------
    //    CLIENT HANDLER CLASS
    // ------------------------------
    private static class ClientHandler extends Thread {
        private Socket socket;
        private PrintWriter out;
        private BufferedInputStream charIn;

        private String username;
        private String currentRoom = "general";

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        private void listUsers() {
            synchronized (chatRooms) {
                if (chatRooms.containsKey(currentRoom)) {
                    out.println("Users in room '" + currentRoom + "':");
                    for (ClientHandler c : chatRooms.get(currentRoom)) {
                        out.println("  " + c.username);
                    }
                } else {
                    out.println("No users in the current room.");
                }
            }
        }
        
        @Override
        public void run() {
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                charIn = new BufferedInputStream(socket.getInputStream());

                // Ask for username (for now there's no auth)
                out.println("Enter your username: ");
                username = blockForLine();
                if (username == null) {
                    return; // disconnected too early
                }

                joinRoom("general");
                broadcastToRoom("[Server] " + username + " has joined the chat.", currentRoom);
                broadcastStatus();

                while (true) {
                    String line = blockForLine();
                    if (line == null) {
                        // user disconnected
                        break;
                    }
                    if (line.isEmpty()) {
                        continue;
                    }

                    // Command checks
                    if (line.startsWith("/join ")) {
                        String roomName = line.substring(6).trim();
                        joinRoom(roomName);
                        broadcastStatus();
                    } else if (line.startsWith("/rooms")) {
                        listRooms();
                    } else if (line.startsWith("/history")) {
                        showHistory();
                    } else if (line.startsWith("/help")) {
                        handleHelpCommand(line);
                    } else if (line.startsWith("/ai ")) {
                        // PUBLIC AI
                        handleAICommand(line);
                    } else if (line.startsWith("/privateai ")) {
                        // PRIVATE AI
                        handlePrivateAICommand(line);
                    } else if (line.startsWith("/listusers")) { 
                        listUsers();

                    } else {
                        // Normal chat
                        broadcastToRoom(username + ": " + line, currentRoom);
                        saveMessage(currentRoom, username, line);
                    }
                }

            } catch (IOException e) {
                System.out.println("Error handling client: " + e.getMessage());
            } finally {
                try { socket.close(); } catch (IOException e) { }
                clients.remove(this);
                broadcastToRoom("[Server] " + username + " has left the chat.", currentRoom);
                broadcastStatus();
                System.out.println("Client disconnected.");
            }
        }

        private String blockForLine() throws IOException {
            StringBuilder sb = new StringBuilder();
            while (true) {
                int ch = charIn.read();
                if (ch == -1) {
                    if (sb.length() == 0) return null;
                    break;
                }
                if (ch == '\n' || ch == '\r') {
                    break;
                }
                sb.append((char) ch);
            }
            return sb.toString().trim();
        }

        private void handleHelpCommand(String line) {
            String query = line.replace("/help", "").trim().toLowerCase();

            if (query.isEmpty()) {
                out.println("AI Helper: Type '/help commands' to see a list of commands, or '/help <your question>' to ask me anything!");
            } else if (query.contains("commands")) { // list commands
                String commands = "Available commands:\n" +
                  "1. /help - Display the list of available commands.\n" +
                  "2. /join <room> - Join a specific room.\n" +
                  "3. /rooms - List all available rooms.\n" +
                  "4. /history - Display the chat history of the current room.\n" +
                  "5. /ai <question> - Ask a general question to the AI.\n" +
                  "6. /privateai <question> - Ask a question to the AI privately.\n" +
                  "7. /listusers - List all users in the current room.";
                out.println("AI: " + commands);

            } else {
                out.println("AI Helper: You asked about '" + query + "'. For more commands, type '/help commands'.");
            }
        }

        // ---------- AI COMMANDS -----------
        private void handleAICommand(String line) {
            if (aiHelper == null) {
                broadcastToRoom("AI: AI not available (no API key).", currentRoom);
                return;
            }
            String question = line.replace("/ai", "").trim();
            if (question.isEmpty()) {
                broadcastToRoom("AI: Please provide a question after /ai, e.g., '/ai How do I fix my code?'", currentRoom);
                return;
            }

            broadcastToRoom(username + " asked AI: " + question, currentRoom);
            saveMessage(currentRoom, username, "[AI-Q]" + question);

            try {
                String sysPrompt = buildDynamicSystemPrompt();
                String response = aiHelper.askOpenAI(question, sysPrompt);

                // Because it's public, show its AI response to everyone
                broadcastToRoom("AI: " + response, currentRoom);
                saveMessage(currentRoom, "AI", response);

                // parse for [PerformCommand]
                parseAICommands(response);

            } catch (IOException e) {
                broadcastToRoom("AI: Error: " + e.getMessage(), currentRoom);
            }
        }

        private void handlePrivateAICommand(String line) {
            if (aiHelper == null) {
                out.println("PRIVATEAI: AI not available (no API key).");
                return;
            }
            String question = line.replace("/privateai", "").trim();
            if (question.isEmpty()) {
                out.println("PRIVATEAI: Please provide a question, e.g., '/privateai How do I fix my code?'");
                return;
            }

            try {
                String sysPrompt = buildDynamicSystemPrompt();
                String response = aiHelper.askOpenAI(question, sysPrompt);

                // Private response => only this user will see it
                out.println("PRIVATEAI: " + response);

                // parse for [PerformCommand]
                parseAICommands(response);

            } catch (IOException e) {
                out.println("PRIVATEAI: Error: " + e.getMessage());
            }
        }

        // Include real-time data for the AI
        private String buildDynamicSystemPrompt() {
            StringBuilder roomsInfo = new StringBuilder();
            synchronized (chatRooms) {
                roomsInfo.append("There are currently ")
                         .append(chatRooms.size())
                         .append(" rooms.\n");
                roomsInfo.append("Here is a list of them:\n");
                int i=1;
                for (Map.Entry<String, Set<ClientHandler>> entry : chatRooms.entrySet()) {
                    String r = entry.getKey();
                    int c = entry.getValue().size();
                    roomsInfo.append("  ").append(i++).append(". ")
                             .append(r).append(" (").append(c).append(" user(s))\n");
                }
            }

            // usage instructions
        String usage =
            "Commands available:\n" +
            "  /help\n" +
            "  /join <room>\n" +
            "  /rooms\n" +
            "  /history\n" +
            "  /ai <question>\n" +
            "  /privateai <question>\n" +
            "  /listusers\n" + 
            "\nWhen user explicitly wants you to DO a command on their behalf, produce:\n" +
            "[PerformCommand] /join <room>\n" +
            "(or /history, /rooms, etc.) exactly.\n" +
            "If you're only explaining, do NOT prefix with [PerformCommand].\n";

        String fullResponse = "You are an AI assistant called ChatSphere AI for a Java-based chat server called ChatSphere.\n" +
                            "User's name is '" + username + "', in room '" + currentRoom + "'.\n" +
                            "Provide accurate info about rooms & user counts, etc.\n" +
                            roomsInfo.toString() + "\n" +
                            usage;

        // Prefix the response with AI: and return it
        return "AI: " + fullResponse;

        }

        // Actually do the commands if AI says [PerformCommand]
        private void parseAICommands(String aiResponse) {
            String marker = "[PerformCommand]";
            int idx = aiResponse.indexOf(marker);
            if (idx < 0) return;

            String after = aiResponse.substring(idx + marker.length()).trim();

            if (after.startsWith("/join ")) {
                String name = extractRoomName(after.substring(6));
                if (!name.isEmpty()) {
                    joinRoom(name);
                    broadcastStatus();
                }
            }
            else if (after.startsWith("/history")) {
                showHistory();
            }
            else if (after.startsWith("/rooms")) {
                listRooms();
            }
            else if (after.startsWith("/listusers")) {
                listUsers();
            }
        }

        private String extractRoomName(String raw) {
            int stop = -1;
            for (int i = 0; i < raw.length(); i++) {
                char c = raw.charAt(i);
                if (Character.isWhitespace(c) || c == '.' || c == ',' || c == '!' || c == '?' ||
                    c == '\'' || c == '\"') {
                    stop = i;
                    break;
                }
            }
            String name = (stop == -1) ? raw : raw.substring(0, stop);
            return raw.trim().replaceAll("[\"']", "");
        }

        // ---------------------------
        //   ROOM & BROADCAST STUFF
        // ---------------------------
        private void joinRoom(String roomName) {
            synchronized (chatRooms) {
                if (chatRooms.containsKey(currentRoom)) {
                    chatRooms.get(currentRoom).remove(this);
                    broadcastToRoom("[Server] " + username + " has left the room.", currentRoom);
                }
                currentRoom = roomName;
                chatRooms.putIfAbsent(roomName, Collections.synchronizedSet(new HashSet<>()));
                chatRooms.get(roomName).add(this);
                broadcastToRoom("[Server] " + username + " has joined the room.", currentRoom);
                out.println("You are now in room: " + roomName);
            }
        }

        private void listRooms() {
            synchronized (chatRooms) {
                out.println("Available rooms:");
                for (Map.Entry<String, Set<ClientHandler>> e : chatRooms.entrySet()) {
                    out.println("  " + e.getKey() + " (" + e.getValue().size() + " user(s))");
                }
            }
        }

        private void broadcastToRoom(String msg, String room) {
            synchronized (chatRooms) {
                if (chatRooms.containsKey(room)) {
                    for (ClientHandler c : chatRooms.get(room)) {
                        c.out.println(msg);
                    }
                }
            }
        }

        private void saveMessage(String room, String user, String message) {
            try (PreparedStatement stmt = dbConnection.prepareStatement(
                "INSERT INTO messages (room, username, message) VALUES (?, ?, ?)")) {
                stmt.setString(1, room);
                stmt.setString(2, user);
                stmt.setString(3, message);
                stmt.executeUpdate();
            } catch (SQLException e) {
                System.err.println("Error saving message: " + e.getMessage());
            }
        }

        private void showHistory() {
            try (PreparedStatement stmt = dbConnection.prepareStatement(
                    "SELECT username, message, timestamp FROM messages WHERE room = ? ORDER BY timestamp ASC")) {
                stmt.setString(1, currentRoom);
                ResultSet rs = stmt.executeQuery();
                out.println("--- Message History for Room: " + currentRoom + " ---");
                while (rs.next()) {
                    out.println("[" + rs.getString("timestamp") + "] "
                                + rs.getString("username") + ": "
                                + rs.getString("message"));
                }
            } catch (SQLException e) {
                System.err.println("Error retrieving message history: " + e.getMessage());
            }
        }
    }

    // ------------------------------
    //   AI HELPER CLASS (OpenAI)
    // ------------------------------
    private static class AIHelper {
        private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";
        private String apiKey;
        private OkHttpClient client;
        private Gson gson;

        public AIHelper(String apiKey) {
            this.apiKey = apiKey;
            this.client = new OkHttpClient();
            this.gson = new Gson();
        }

        public String askOpenAI(String userQuestion, String dynamicSysPrompt) throws IOException {
            // Single-turn conversation for simplicity
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", "gpt-3.5-turbo");

            JsonArray messages = new JsonArray();

            // System role
            JsonObject sysMsg = new JsonObject();
            sysMsg.addProperty("role", "system");
            sysMsg.addProperty("content", dynamicSysPrompt);
            messages.add(sysMsg);

            // User role
            JsonObject userMsg = new JsonObject();
            userMsg.addProperty("role", "user");
            userMsg.addProperty("content", userQuestion);
            messages.add(userMsg);

            requestBody.add("messages", messages);
            requestBody.addProperty("max_tokens", 300);
            requestBody.addProperty("temperature", 0.7);

            Request request = new Request.Builder()
                .url(OPENAI_URL)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(
                    requestBody.toString(),
                    MediaType.parse("application/json")
                ))
                .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("OpenAI API error: " + response);
                }
                String jsonResponse = response.body().string();
                JsonObject root = gson.fromJson(jsonResponse, JsonObject.class);
                JsonArray choices = root.getAsJsonArray("choices");
                if (choices.size() > 0) {
                    JsonObject choice = choices.get(0).getAsJsonObject();
                    JsonObject message = choice.getAsJsonObject("message");
                    return message.get("content").getAsString().trim();
                }
                return "No response from AI.";
            }
        }
    }
}
