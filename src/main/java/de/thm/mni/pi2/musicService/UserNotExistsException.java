package de.thm.mni.pi2.musicService;

public class UserNotExistsException extends RuntimeException {
  public UserNotExistsException(int id) {
    super("User with ID " + id + "does not exists: " );
  }
}
