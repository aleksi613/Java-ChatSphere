package chat;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;

public class ChatClientGUI extends JFrame {
    private JTextArea chatArea; // Left side, which is the public chat area
    private JTextArea aiChatArea; // Right, which is the AI sidebar chat area
    private JTextField inputField;        
    private JTextField aiInputField;      
    private JButton sendButton;           
    private JButton aiSendButton;         
    private JButton forwardButton;        
    private JButton toggleThemeButton;    
    private boolean isDarkMode = true;    
    private PrintWriter out;              
    private BufferedReader in;            
    private ArrayList<String> aiResponses;

    // A label at the top for status info (like users count, and rooms with their respective user counts)
    private JLabel statusLabel;

    public ChatClientGUI() {
        setTitle("Chat Client");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(800, 600);
        setLayout(new BorderLayout());

        aiResponses = new ArrayList<>();

        // ---------------------------
        // Main public Chat Panel
        // ---------------------------
        JPanel mainChatPanel = new JPanel(new BorderLayout());
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setFont(new Font("SansSerif", Font.PLAIN, 16));
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);

        JScrollPane chatScrollPane = new JScrollPane(chatArea);
        mainChatPanel.add(chatScrollPane, BorderLayout.CENTER);

        // Input Panel (public chat)
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputField = new JTextField();
        inputField.setFont(new Font("SansSerif", Font.PLAIN, 16));
        sendButton = new JButton("Send");
        sendButton.setFont(new Font("SansSerif", Font.BOLD, 16));
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        mainChatPanel.add(inputPanel, BorderLayout.SOUTH);

        // ---------------------------
        // AI Sidebar
        // ---------------------------
        JPanel aiPanel = new JPanel(new BorderLayout());
        aiChatArea = new JTextArea();
        aiChatArea.setEditable(false);
        aiChatArea.setFont(new Font("SansSerif", Font.PLAIN, 16));
        aiChatArea.setLineWrap(true);
        aiChatArea.setWrapStyleWord(true);
        // For AI, text is in red:
        aiChatArea.setForeground(Color.RED);

        JScrollPane aiScrollPane = new JScrollPane(aiChatArea);
        aiPanel.add(aiScrollPane, BorderLayout.CENTER);

        // AI Input Panel
        JPanel aiInputPanel = new JPanel(new BorderLayout());
        aiInputField = new JTextField();
        aiInputField.setFont(new Font("SansSerif", Font.PLAIN, 16));
        aiSendButton = new JButton("Send to AI");
        forwardButton = new JButton("Forward to Chat");
        aiInputPanel.add(aiInputField, BorderLayout.CENTER);
        aiInputPanel.add(aiSendButton, BorderLayout.EAST);
        aiInputPanel.add(forwardButton, BorderLayout.SOUTH);
        aiPanel.add(aiInputPanel, BorderLayout.SOUTH);

        // ---------------------------
        // Split Pane (left vs right)
        // ---------------------------
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, mainChatPanel, aiPanel);
        splitPane.setResizeWeight(0.75);
        splitPane.setDividerLocation((int) (0.75 * getWidth())); // 75% of width (left side/public chat)
        // Disable dragging
        splitPane.setEnabled(false);
        splitPane.setDividerSize(0);
        splitPane.setOneTouchExpandable(false);

        add(splitPane, BorderLayout.CENTER);

        // ---------------------------
        // Top Panel w/ Theme Button + Status Label
        // ---------------------------
        JPanel topPanel = new JPanel(new BorderLayout());
        toggleThemeButton = new JButton("Toggle Light Mode");
        toggleThemeButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        toggleThemeButton.setFocusPainted(false);
        toggleThemeButton.addActionListener(e -> toggleTheme());
        topPanel.add(toggleThemeButton, BorderLayout.EAST);

        statusLabel = new JLabel("Status...");
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        topPanel.add(statusLabel, BorderLayout.CENTER);

        add(topPanel, BorderLayout.NORTH);

        // Action Listeners
        sendButton.addActionListener(e -> sendMessage());
        inputField.addActionListener(e -> sendMessage());
        aiSendButton.addActionListener(e -> sendToAI());
        aiInputField.addActionListener(e -> sendToAI());
        forwardButton.addActionListener(e -> forwardToChat());

        // Default to dark mode upon login
        applyDarkMode();
    }

    private void sendMessage() {
        String message = inputField.getText().trim();
        if (!message.isEmpty()) {
            out.println(message);
            inputField.setText("");
        }
    }

    /**
     * Send a message from the AI text field.
     * If it starts with "/ai ", it's public. Otherwise, it's private.
     */
    private void sendToAI() {
        String message = aiInputField.getText().trim();
        if (!message.isEmpty()) {
            // Show the user's typed question in black on the right side
            aiChatArea.append("You: " + message + "\n");
    
            aiInputField.setText("");
    
            new Thread(() -> {
                try {
                    out.println("/privateai " + message);  // Always use /privateai
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> aiChatArea.append("Error: Unable to connect to AI.\n"));
                }
            }).start();
        }
    }
    

    /**
     * Forward a previously received AI response to the public chat.
     */
    private void forwardToChat() {
        if (aiResponses.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No AI responses to forward!",
                                          "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        String selectedResponse = (String) JOptionPane.showInputDialog(
                this,
                "Select an AI message to forward:",
                "Forward to Chat",
                JOptionPane.PLAIN_MESSAGE,
                null,
                aiResponses.toArray(),
                aiResponses.get(0)
        );
        if (selectedResponse != null) {
            out.println("[Forwarded from AI]: " + selectedResponse);
        }
    }

    private void toggleTheme() {
        if (isDarkMode) {
            applyLightMode();
        } else {
            applyDarkMode();
        }
    }

    private void applyLightMode() {
        chatArea.setBackground(Color.WHITE);
        chatArea.setForeground(Color.BLACK);
        aiChatArea.setBackground(new Color(245, 245, 245));
        // keep AI text red
        inputField.setBackground(Color.WHITE);
        inputField.setForeground(Color.BLACK);
        aiInputField.setBackground(Color.WHITE);
        aiInputField.setForeground(Color.BLACK);
        sendButton.setBackground(new Color(30, 144, 255));
        sendButton.setForeground(Color.WHITE);
        aiSendButton.setBackground(new Color(30, 144, 255));
        aiSendButton.setForeground(Color.WHITE);
        forwardButton.setBackground(new Color(100, 149, 237));
        forwardButton.setForeground(Color.WHITE);
        toggleThemeButton.setText("Toggle Dark Mode");
        isDarkMode = false;
    }

    private void applyDarkMode() {
        chatArea.setBackground(new Color(30, 30, 30));
        chatArea.setForeground(new Color(200, 200, 200));
        aiChatArea.setBackground(new Color(50, 50, 50));
        // keep AI text red
        inputField.setBackground(new Color(50, 50, 50));
        inputField.setForeground(new Color(200, 200, 200));
        aiInputField.setBackground(new Color(50, 50, 50));
        aiInputField.setForeground(new Color(200, 200, 200));
        sendButton.setBackground(new Color(100, 100, 100));
        sendButton.setForeground(Color.WHITE);
        aiSendButton.setBackground(new Color(100, 100, 100));
        aiSendButton.setForeground(Color.WHITE);
        forwardButton.setBackground(new Color(80, 80, 80));
        forwardButton.setForeground(Color.WHITE);
        toggleThemeButton.setText("Toggle Light Mode");
        isDarkMode = true;
    }

    /**
     * Connect to the server, read lines, and route them to left or right as needed.
     */
    public void connectToServer(String host, int port) {
        try {
            Socket socket = new Socket(host, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    
            Thread readerThread = new Thread(() -> {
                try {
                    String line;
                    while ((line = in.readLine()) != null) {
                        if (line.startsWith("STATUS: ")) {
                            String text = line.substring("STATUS: ".length()).trim();
                            SwingUtilities.invokeLater(() -> statusLabel.setText(text));
                        } else if (line.startsWith("AI: ")) {
                            String aiResponse = line.substring("AI: ".length()).trim();
                            appendToPublicChat(aiResponse);
                        } else if (line.startsWith("PRIVATEAI: ")) {
                            String aiMsg = line.substring("PRIVATEAI: ".length()).trim();
                            appendToAIChat(aiMsg);
                        } else {
                            appendToPublicChat(line);
                        }
                    }
                } catch (IOException e) {
                    appendToPublicChat("Disconnected from server.");
                }
            });
            readerThread.start();
    
            String username = JOptionPane.showInputDialog(this, "Enter your username:");
            if (username != null && !username.trim().isEmpty()) {
                out.println(username);
            } else {
                appendToPublicChat("Invalid username. Closing client.");
                socket.close();
                System.exit(0);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Unable to connect to server.",
                                          "Connection Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void appendToAIChat(String message) {
        SwingUtilities.invokeLater(() -> {
            aiChatArea.append("[AI] " + message + "\n");
            aiResponses.add(message);
        });
    }
    
    private void appendToPublicChat(String message) {
        SwingUtilities.invokeLater(() -> chatArea.append(message + "\n"));
    }
    

    public static void main(String[] args) {
        ChatClientGUI client = new ChatClientGUI();
        client.setVisible(true);
        client.connectToServer("localhost", 12345);
    }
}
