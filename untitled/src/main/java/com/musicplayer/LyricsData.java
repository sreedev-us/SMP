package com.musicplayer;

import java.util.Collections;
import java.util.List;

public class LyricsData {
    private final List<LyricsLine> lines;
    private final boolean synced;
    private final String source;

    public LyricsData(List<LyricsLine> lines, boolean synced, String source) {
        this.lines = lines == null ? List.of() : List.copyOf(lines);
        this.synced = synced;
        this.source = source;
    }

    public List<LyricsLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public boolean isSynced() {
        return synced;
    }

    public String getSource() {
        return source;
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }
}
