package de.thm.mni.pi2.musicService;

public class SongAlreadyExistsException extends RuntimeException {
  public SongAlreadyExistsException(String name, String genre) {
    super("Song already exists: " + name + " - " + genre);
  }
}
