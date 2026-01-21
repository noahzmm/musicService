package de.thm.mni.pi2.musicService;

public class UserAlreadyExistsException extends RuntimeException {
  public UserAlreadyExistsException(String username) {
    super("User already exists: " + username);
  }
}
