# AI-Enhanced ChatSphere

## Overview

The AI-Enhanced ChatSphere is a Java-based client-server application that facilitates real-time chat communication. The system includes a server component, a graphical user interface (GUI) client, and integration with SQLite for persistent storage. It also supports AI-powered responses using OpenAI's GPT model, enabling users to interact with AI in both public and private chat contexts.

## Features

### Server Features

- **Multi-client support**: Allows multiple clients to connect and communicate in real time.
- **Chat rooms**: Supports creating and joining rooms for group chats.
- **Message persistence**: Stores chat history in an SQLite database for retrieval and analysis.
- **AI integration**: Provides public and private AI-powered responses using OpenAI's GPT model.
- **Real-time status updates**: Displays the number of active users and chat room details.
- **Command support**:
  - `/join <room>`: Join or create a chat room.
  - `/rooms`: List all available chat rooms.
  - `/history`: Display chat history for the current room.
  - `/ai <question>`: Ask a question to AI publicly.
  - `/privateai <question>`: Ask a question to AI privately.
  - `/listusers`: List all users in the room

### Client Features

- **Graphical User Interface**: A user-friendly GUI for interacting with the chat server.
- **Dual chat areas**: Separate areas for public chat and AI interactions.
- **Theme toggling**: Supports light and dark modes for better usability.
- **Message forwarding**: Allows users to forward AI responses to public chat.
- **Status display**: Displays server status and number of users connected.

## Technologies Used

- **Java Swing**: For the client-side graphical user interface.
- **SQLite**: For storing and retrieving chat messages.
- **OkHttp and Gson**: For interacting with the OpenAI API.
- **Maven**: For dependency management and project build.

## Getting Started

### Prerequisites

- **Java 11+**: Ensure you have JDK 11 or higher installed.
- **Maven**: For building and running the project.
- **OpenAI API Key**: Required for AI features. Set it as an environment variable `OPENAI_API_KEY`.

### Build the Project

```sh
mvn clean install
```

### Run the Server

```sh
mvn exec:java@run-server
```

### Run the Client

```sh
mvn exec:java@run-client
```

## Configuration

### OpenAI API Key

Set your OpenAI API key as an environment variable:

```sh
export OPENAI_API_KEY=your_openai_api_key
```

### Database

The application uses SQLite for message storage. The database file (`chat.db`) will be created automatically in the project root directory.

### GUI

- **Send Button**: Send a message to the public chat.
- **Send to AI Button**: Send a message to the AI.
- **Forward to Chat Button**: Forward an AI response to the public chat.
- **Toggle Theme Button**: Switch between light and dark mode.

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

## Acknowledgements

- [OpenAI](https://www.openai.com/) for the GPT-3.5 API
- [SQLite](https://www.sqlite.org/) for the database
- [OkHttp](https://square.github.io/okhttp/) for HTTP requests
- [Gson](https://github.com/google/gson) for JSON parsing
## File Structure

- `ChatServer.java`: Implements the server logic, handles client connections, and manages chat rooms.
- `ChatClientGUI.java`: Implements the GUI client for chat interactions.
- `chat.db`: SQLite database for storing chat messages.
- `pom.xml`: Maven configuration file specifying dependencies and build configurations.

## Dependencies

Dependencies are managed through Maven and include:

- SQLite JDBC: For database connectivity.
- OkHttp: For HTTP requests to the OpenAI API.
- Gson: For parsing JSON responses from the OpenAI API.

## Development Notes

- Ensure the server is started before connecting clients.
- AI features require a valid OpenAI API Key.
- Database initialization is handled automatically by the server.

## Future Enhancements (WIP)

- Add end-to-end encryption for secure communication.
- Introduce user authentication and authorization.
- Expand AI capabilities for more natural conversations.
- Add support for multimedia messages (like images, files).
