package de.thm.mni.pi2.musicService;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

import java.sql.*;

public class MainVerticle extends VerticleBase {
  private static final String DB_URL = "jdbc:mariadb://<your.database.url>";
  private static final String DB_USER = "<your_db_user>";
  private static final String DB_PASSWORD = "<your_db_password>";

  private Connection connection;

  @Override
  public Future<?> start() {
    try {
      connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    } catch (SQLException e) {
      System.err.println("Fehler beim Öffnen der Datenbankverbindung");
      e.printStackTrace();
      return Future.failedFuture(e);
    }

    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());
    router.post("/users").handler(this::createUser);
    router.get("/users").handler(this::getAllUsers);
    router.get("/users/:id").handler(this::getUser);
    router.delete("/users/:id").handler(this::deleteUser);

    router.post("/songs").handler(this::createSong);
    router.put("/users/:userId/songs/:songId").handler(this::assignSong);
    router.get("/songs").handler(this::getAllSongs);
    router.delete("/users/:userId/songs/:songId").handler(this::unassignSong);
    router.delete("/songs/:id").handler(this::deleteSong);

    return vertx.createHttpServer()
      .requestHandler(router)
      .listen(8888)
      .onSuccess(http -> System.out.println("HTTP server started on port 8888"));
  }


  /**
   * Creates a new user in the database based on the JSON input from the HTTP request body.
   *
   * @param routingContext The routing context containing the HTTP request and response objects.
   */
  private void createUser(RoutingContext routingContext) {
    try {
      JsonObject jsonObject = routingContext.body().asJsonObject();
      String username = jsonObject.getString("username");
      String email = jsonObject.getString("email");
      if (username.isEmpty() || email.isEmpty()) {
        throw new IllegalArgumentException("Invalid JSON input.");
      }
      if (userExists(username)) {
        throw new UserAlreadyExistsException(username);
      }
      String sql = "INSERT INTO users (name, email) VALUES (?, ?)";
      try (PreparedStatement preparedStatement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
        preparedStatement.setString(1, username);
        preparedStatement.setString(2, email);
        preparedStatement.executeUpdate();

        try (ResultSet generatedKeys = preparedStatement.getGeneratedKeys()) {
          if (generatedKeys.next()) {
            long id = generatedKeys.getLong(1);
            response(routingContext.response(), 201, new JsonObject().put("success", "User successfully created with ID: " + id));
          } else {
            response(routingContext.response(), 500, new JsonObject().put("error", "Failed to retrieve generated ID"));
          }
        }
      } catch (SQLException e) {
        System.err.println("Error while trying to insert user into database.");
        e.printStackTrace();
      }
    } catch (IllegalArgumentException e) {
      response(routingContext.response(), 400, new JsonObject().put("error", e.getMessage()));
    } catch (UserAlreadyExistsException e) {
      response(routingContext.response(), 409, new JsonObject().put("error", e.getMessage()));
    } catch (Exception e) {
      response(routingContext.response(), 500, new JsonObject().put("error", e.getMessage()));
    }
  }

  /**
   * Retrieves all users from the database and sends them in the HTTP response.
   *
   * @param routingContext The routing context containing the HTTP request and response objects.
   */
  private void getAllUsers(RoutingContext routingContext) {
    try {
      JsonObject usernames = fetchAllUsers();
      response(routingContext.response(), 200, new JsonObject().put("users", usernames));
      if (usernames.isEmpty()) {
        throw new NoUsersFoundException("No Users found.");
      }
    } catch (NoUsersFoundException e) {
      response(routingContext.response(), 204, new JsonObject().put("error", e.getMessage()));
    } catch (Exception e) {
      response(routingContext.response(), 500, new JsonObject().put("error", e.getMessage()));
    }
  }

  /**
   * Retrieves a specific user from the database based on the ID provided in the URL path parameter.
   *
   * @param routingContext The routing context containing the HTTP request and response objects.
   */
  private void getUser(RoutingContext routingContext) {
    try {
      int id = Integer.parseInt(routingContext.pathParam("id"));
      if (id < 1) {
        throw new IllegalArgumentException("Invalid ID");
      }
      if (!userExists(id)) {
        throw new UserNotExistsException(id);
      }
      String sql = "SELECT u.name, u.email, s.name FROM users u LEFT JOIN songs s ON s.id = u.id WHERE u.id = ?";
      JsonObject user = new JsonObject();
      try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
        preparedStatement.setInt(1, id);
        try (ResultSet resultSet = preparedStatement.executeQuery()) {
          boolean exists = false;
          while (resultSet.next()) {
            if (!exists) {
              user.put("id", id);
              user.put("name", resultSet.getString("u.name"));
              user.put("email", resultSet.getString("u.email"));
              exists = true;
            }
            JsonObject songs = new JsonObject();
            if (resultSet.getString("s.name") != null) {
              songs.put("name", resultSet.getString("s.name"));
            }
            user.put("songs", songs);
          }
        }
        routingContext.response().putHeader("content-type", "application/json").setStatusCode(200).end(Json.encodePrettily(user));
      } catch (SQLException e) {
        System.err.println("Fehler beim Suchen von User in Datenbank");
        e.printStackTrace();
      }
    } catch (IllegalArgumentException e) {
      response(routingContext.response(), 400, new JsonObject().put("error", e.getMessage()));
    } catch (UserNotExistsException e) {
      response(routingContext.response(), 404, new JsonObject().put("error", e.getMessage()));
    } catch (Exception e) {
      response(routingContext.response(), 500, new JsonObject().put("error", e.getMessage()));
    }
  }

  /**
   * Retrieves a specific user from the database based on the ID provided in the URL path parameter.
   *
   * @param routingContext The routing context containing the HTTP request and response objects.
   */
  private void deleteUser(RoutingContext routingContext) {
    try {
      int id = Integer.parseInt(routingContext.pathParam("id"));
      if (id < 1) {
        throw new IllegalArgumentException("Invalid name");
      }
      if (!userExists(id)) {
        throw new UserNotExistsException(id);
      }
      String sql = "DELETE FROM users WHERE id = ?";
      try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
        preparedStatement.setInt(1, id);
        preparedStatement.executeUpdate();
        response(routingContext.response(), 200, new JsonObject().put("success", "User with ID " + id + " successfully deleted"));
      } catch (SQLException e) {
        System.err.println("Error while trying to delete user from database.");
        e.printStackTrace();
      }
    } catch (IllegalArgumentException e) {
      response(routingContext.response(), 400, new JsonObject().put("error", e.getMessage()));
    } catch (UserNotExistsException e) {
      response(routingContext.response(), 404, new JsonObject().put("error", e.getMessage()));
    } catch (Exception e) {
      response(routingContext.response(), 500, new JsonObject().put("error", e.getMessage()));
    }

  }

  /**
   * Creates a new song in the database based on the JSON input from the HTTP request body.
   *
   * @param routingContext The routing context containing the HTTP request and response objects.
   */
  private void createSong(RoutingContext routingContext) {
    try {
      JsonObject jsonObject = routingContext.body().asJsonObject();
      String name = jsonObject.getString("name");
      String genre = jsonObject.getString("genre");
      if (name.isEmpty() || genre.isEmpty()) {
        throw new IllegalArgumentException("Invalid JSON input.");
      }
      if (songExists(name, genre)) {
        throw new SongAlreadyExistsException(name, genre);
      }
      String sql = "INSERT INTO songs (name, genre) VALUES (?, ?)";
      try (PreparedStatement preparedStatement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
        preparedStatement.setString(1, name);
        preparedStatement.setString(2, genre);
        preparedStatement.executeUpdate();
        try (ResultSet generatedKeys = preparedStatement.getGeneratedKeys()) {
          if (generatedKeys.next()) {
            long id = generatedKeys.getLong(1);
            response(routingContext.response(), 201, new JsonObject().put("success", "Song successfully created with ID: " + id));
          } else {
            response(routingContext.response(), 500, new JsonObject().put("error", "Failed to retrieve generated ID"));
          }
        }
      } catch (SQLException e) {
        System.err.println("Fehler beim einfügen von Song in Datenbank");
        e.printStackTrace();
      }
    } catch (IllegalArgumentException e) {
      response(routingContext.response(), 400, new JsonObject().put("error", e.getMessage()));
    } catch (SongAlreadyExistsException e) {
      response(routingContext.response(), 409, new JsonObject().put("error", e.getMessage()));
    } catch (Exception e) {
      response(routingContext.response(), 500, new JsonObject().put("error", e.getMessage()));
    }
  }

  /**
   * Assigns a song to a user based on the user ID and song ID provided in the URL path parameters.
   *
   * @param routingContext The routing context containing the HTTP request and response objects.
   */
  private void assignSong(RoutingContext routingContext) {
    try {
      int user_id = Integer.parseInt(routingContext.pathParam("userId"));
      int song_id = Integer.parseInt(routingContext.pathParam("songId"));
      if (user_id < 1 || song_id < 1) {
        throw new IllegalArgumentException("Invalid ID input.");
      }
      if (!songExists(song_id)) {
        throw new SongNotExistsException("ID " + song_id + " does not exist");
      }
      if (!userExists(user_id)) {
        throw new UserNotExistsException(user_id);
      }
      if (assertionExists(user_id, song_id)) {
        throw new AssertionAlreadyExistsException("User " + user_id + "with song " + song_id + " already exists");
      }
      String sql = "INSERT INTO user_songs (user_id, song_id) VALUES (?, ?)";
      try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
        preparedStatement.setInt(1, user_id);
        preparedStatement.setInt(2, song_id);
        preparedStatement.executeUpdate();
        response(routingContext.response(), 201, new JsonObject().put("success", "Song was successfully asserted with ID: " + song_id + " to user with ID: " + user_id));
      } catch (SQLException e) {
        System.err.println("Fehler beim Einfügen von Zuweisung in Datenbank");
        e.printStackTrace();
      }
    } catch (IllegalArgumentException e) {
      response(routingContext.response(), 400, new JsonObject().put("error", e.getMessage()));
    } catch (AssertionAlreadyExistsException e) {
      response(routingContext.response(), 404, new JsonObject().put("error", e.getMessage()));
    } catch (Exception e) {
      response(routingContext.response(), 500, new JsonObject().put("error", e.getMessage()));
    }

  }

  /**
   * Retrieves all songs from the database and sends them in the HTTP response.
   *
   * @param routingContext The routing context containing the HTTP request and response objects.
   */
  private void getAllSongs(RoutingContext routingContext) {
    try {
      JsonObject songs = fetchAllSongs();
      if (songs.isEmpty()) {
        throw new NoSongsFoundException("No Songs found.");
      }
      response(routingContext.response(), 200, new JsonObject().put("songs", songs));
    } catch (NoSongsFoundException e) {
      response(routingContext.response(), 204, new JsonObject().put("success", e.getMessage()));
    } catch (IllegalArgumentException e) {
      response(routingContext.response(), 400, new JsonObject().put("error", e.getMessage()));
    } catch (Exception e) {
      response(routingContext.response(), 500, new JsonObject().put("error", e.getMessage()));
    }
  }

  /**
   * Unassigns a song from a user based on the user ID and song ID provided in the URL path parameters.
   *
   * @param routingContext The routing context containing the HTTP request and response objects.
   */
  private void unassignSong(RoutingContext routingContext) {
    try {
      int userId = Integer.parseInt(routingContext.pathParam("userId"));
      int songId = Integer.parseInt(routingContext.pathParam("songId"));
      if (userId < 1 || songId < 1) {
        throw new IllegalArgumentException("Invalid ID input.");
      }
      if (!songExists(songId)) {
        throw new SongNotExistsException("ID " + songId + " does not exist");
      }
      if (!userExists(userId)) {
        throw new UserNotExistsException(userId);
      }
      if (!assertionExists(songId, userId)) {
        throw new AssertionNotExistsException("User " + userId + "with song " + songId + " does not exists");
      }
      String sql = "DELETE from user_songs where user_id = ? and song_id = ?";
      try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
        preparedStatement.setInt(1, userId);
        preparedStatement.setInt(2, songId);
        preparedStatement.executeUpdate();
        response(routingContext.response(), 200, new JsonObject().put("success", "Song was successfully removed with ID: " + songId + " from user with ID: " + userId));
      } catch (SQLException e) {
        System.err.println("Fehler beim Entfernen von Zuweisung aus Datenbank");
        e.printStackTrace();
      }
    } catch (IllegalArgumentException e) {
      response(routingContext.response(), 400, new JsonObject().put("error", e.getMessage()));
    } catch (UserNotExistsException | SongNotExistsException e) {
      response(routingContext.response(), 404, new JsonObject().put("error", e.getMessage()));
    } catch (AssertionNotExistsException e) {
      response(routingContext.response(), 409, new JsonObject().put("error", e.getMessage()));
    } catch (Exception e) {
      response(routingContext.response(), 500, new JsonObject().put("error", e.getMessage()));
    }

  }

  /**
   * Deletes a song from the database based on the ID provided in the URL path parameter.
   *
   * @param routingContext The routing context containing the HTTP request and response objects.
   */
  private void deleteSong(RoutingContext routingContext) {
    try {
      int songId = Integer.parseInt(routingContext.pathParam("id"));
      if (songId < 0) {
        throw new IllegalArgumentException("Invalid ID input.");
      }
      if (!songExists(songId)) {
        throw new SongNotExistsException("ID " + songId + " does not exist");
      }
      String sql1 = "DELETE FROM songs WHERE id = ?";
      try (PreparedStatement prepareStatement1 = connection.prepareStatement(sql1)) {
        prepareStatement1.setInt(1, songId);
        prepareStatement1.executeUpdate();
        response(routingContext.response(), 200, new JsonObject().put("success", "Song with ID '" + songId + "' successfully deleted"));
      } catch (SQLException e) {
        System.err.println("Fehler beim Löschen von Song in Datenbank");
        e.printStackTrace();
      }

    } catch (IllegalArgumentException e) {
      response(routingContext.response(), 400, new JsonObject().put("error", e.getMessage()));
    } catch (SongNotExistsException e) {
      response(routingContext.response(), 404, new JsonObject().put("error", e.getMessage()));
    } catch (Exception e) {
      response(routingContext.response(), 500, new JsonObject().put("error", e.getMessage()));
    }

  }

  /**
   * Sends a response with the specified status code and JSON payload.
   *
   * @param response   The HTTP server response object to be used for sending the response.
   * @param statusCode The HTTP status code to set for the response.
   * @param json       The JSON object containing the data to be sent in the response.
   */
  private void response(HttpServerResponse response, Integer statusCode, JsonObject json) {
    response
      .putHeader("content-type", "application/json")
      .setStatusCode(statusCode)
      .end(Json.encodePrettily(json));
  }

  private boolean userExists(String username) {
    String sql = "SELECT u.name from users u where u.name= ?";
    try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

      preparedStatement.setString(1, username);

      try (ResultSet resultSet = preparedStatement.executeQuery()) {
        return resultSet.next();
      }

    } catch (SQLException e) {
      System.err.println("Fehler beim Prüfen, ob User existiert");
      e.printStackTrace();
      return false;
    }
  }

  /**
   * Checks if a user with the given ID exists in the database.
   *
   * @param id The ID of the user to check.
   * @return true if the user exists, false otherwise.
   */
  private boolean userExists(int id) {
    String sql = "SELECT name from users where id= ?";
    try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

      preparedStatement.setInt(1, id);

      try (ResultSet resultSet = preparedStatement.executeQuery()) {
        return resultSet.next();
      }

    } catch (SQLException e) {
      System.err.println("Fehler beim Prüfen, ob User existiert");
      e.printStackTrace();
      return false;
    }
  }

  /**
   * Fetches all users from the database along with their associated songs.
   *
   * @return A JsonObject containing all users and their songs.
   */
  private JsonObject fetchAllUsers() {
    String sql = "SELECT u.id, u.name, u.email, s.id, s.name from users u left join user_songs us on us.user_id = u.id left join songs s on s.id = us.song_id";
    JsonObject users = new JsonObject();
    try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

      try (ResultSet resultSet = preparedStatement.executeQuery()) {
        while (resultSet.next()) {
          String id = String.valueOf(resultSet.getInt("u.id"));
          JsonObject user;
          if (users.containsKey(id)) {
            user = users.getJsonObject(id);
          } else {
            user = new JsonObject();
            user.put("name", resultSet.getString("u.name"));
            user.put("email", resultSet.getString("u.email"));
            user.put("songs", new JsonObject());
            users.put(id, user);
          }

          int songId = resultSet.getInt("s.id");
          String songName = resultSet.getString("s.name");
          if (songName != null) {
            user.getJsonObject("songs").put(String.valueOf(songId), new JsonObject().put("name", songName));
          }
        }
      }

      return users;

    } catch (SQLException e) {
      System.err.println("Fehler beim fetchen von Usern");
      e.printStackTrace();
      return null;
    }
  }

  /**
   * Checks if a song with the given name and genre exists in the database.
   *
   * @param name  The name of the song to check.
   * @param genre The genre of the song to check.
   * @return true if the song exists, false otherwise.
   */
  private boolean songExists(String name, String genre) {
    String sql = "SELECT name, genre from songs where name= ? AND genre= ?";
    try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

      preparedStatement.setString(1, name);
      preparedStatement.setString(2, genre);

      try (ResultSet resultSet = preparedStatement.executeQuery()) {
        return resultSet.next();
      }

    } catch (SQLException e) {
      System.err.println("Fehler beim Prüfen, ob Song existiert");
      e.printStackTrace();
      return false;
    }
  }

  /**
   * Checks if a song with the given ID exists in the database.
   *
   * @param id The ID of the song to check.
   * @return true if the song exists, false otherwise.
   */
  private boolean songExists(int id) {
    String sql = "SELECT name from songs where id= ?";
    try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

      preparedStatement.setInt(1, id);

      try (ResultSet resultSet = preparedStatement.executeQuery()) {
        return resultSet.next();
      }

    } catch (SQLException e) {
      System.err.println("Fehler beim Prüfen, ob Song existiert");
      e.printStackTrace();
      return false;
    }
  }

  /**
   * Checks if an assertion between a song and a user exists in the database.
   *
   * @param songId The ID of the song.
   * @param userId The ID of the user.
   * @return true if the assertion exists, false otherwise.
   */
  private boolean assertionExists(int songId, int userId) {
    String sql = "SELECT song_id, user_id  from user_songs where song_id= ? AND user_id= ?";
    try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

      preparedStatement.setInt(1, songId);
      preparedStatement.setInt(2, userId);
      try (ResultSet resultSet = preparedStatement.executeQuery()) {
        return resultSet.next();
      }

    } catch (SQLException e) {
      System.err.println("Fehler beim Prüfen, ob Song existiert");
      e.printStackTrace();
      return false;
    }
  }

  /**
   * Fetches all songs from the database.
   *
   * @return A JsonObject containing all songs.
   */
  private JsonObject fetchAllSongs() {
    String sql = "SELECT * from songs";
    JsonObject songs = new JsonObject();

    try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

      try (ResultSet resultSet = preparedStatement.executeQuery()) {
        while (resultSet.next()) {
          JsonObject details = new JsonObject();
          details.put("name", resultSet.getString("name"));
          details.put("genre", resultSet.getString("genre"));
          details.put("timestamp", resultSet.getString("timestamp"));
          songs.put(resultSet.getString("id"), details);
        }
      }
      return songs;

    } catch (SQLException e) {
      System.err.println("Fehler beim fetchen von Usern");
      e.printStackTrace();
      return null;
    }
  }

}
