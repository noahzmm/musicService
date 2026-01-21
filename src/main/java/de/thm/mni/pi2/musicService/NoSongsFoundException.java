package de.thm.mni.pi2.musicService;

public class NoSongsFoundException extends RuntimeException {
  public NoSongsFoundException(String message) {
    super(message);
  }
}
