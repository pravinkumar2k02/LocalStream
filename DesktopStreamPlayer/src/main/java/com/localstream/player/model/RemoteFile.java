package com.localstream.player.model;

import java.text.DecimalFormat;

/**
 * Model representing a remote media file served by the Android streaming server.
 */
public class RemoteFile {
    private String id;
    private String name;
    private String mimeType;
    private long size;

    public RemoteFile() {
    }

    public RemoteFile(String id, String name, String mimeType, long size) {
        this.id = id;
        this.name = name;
        this.mimeType = mimeType;
        this.size = size;
    }

    public String getId() {
        return id != null ? id : "";
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name != null ? name : "Unknown";
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMimeType() {
        return mimeType != null ? mimeType : "application/octet-stream";
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    /**
     * Formats bytes into human-readable B, KB, MB, GB string.
     */
    public String getFormattedSize() {
        if (size <= 0) return "0 B";
        final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        if (digitGroups >= units.length) digitGroups = units.length - 1;
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    public boolean isVideo() {
        String m = getMimeType().toLowerCase();
        String n = getName().toLowerCase();
        return m.startsWith("video/") || n.endsWith(".mp4") || n.endsWith(".webm") || n.endsWith(".mkv") || n.endsWith(".avi") || n.endsWith(".mov");
    }

    public boolean isAudio() {
        String m = getMimeType().toLowerCase();
        String n = getName().toLowerCase();
        return m.startsWith("audio/") || n.endsWith(".mp3") || n.endsWith(".wav") || n.endsWith(".m4a") || n.endsWith(".aac") || n.endsWith(".flac") || n.endsWith(".ogg");
    }

    public boolean isMp4() {
        String m = getMimeType().toLowerCase();
        String n = getName().toLowerCase();
        return m.contains("mp4") || n.endsWith(".mp4") || n.endsWith(".m4v");
    }

    public boolean isWebM() {
        String m = getMimeType().toLowerCase();
        String n = getName().toLowerCase();
        return m.contains("webm") || n.endsWith(".webm");
    }

    public boolean isMkv() {
        String m = getMimeType().toLowerCase();
        String n = getName().toLowerCase();
        return m.contains("matroska") || m.contains("mkv") || n.endsWith(".mkv");
    }

    public boolean isAvi() {
        String m = getMimeType().toLowerCase();
        String n = getName().toLowerCase();
        return m.contains("avi") || m.contains("msvideo") || n.endsWith(".avi");
    }

    @Override
    public String toString() {
        return getName() + " (" + getFormattedSize() + ")";
    }
}
