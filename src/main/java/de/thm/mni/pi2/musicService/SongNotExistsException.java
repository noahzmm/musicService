package de.thm.mni.pi2.musicService;

public class SongNotExistsException extends RuntimeException {
  public SongNotExistsException(String name) {
    super("Song does not exists: " + name);
  }
}
