package com.wode.app.service;

import com.wode.app.data.Lyrics;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class LyricParserTest {
    @Test
    public void parseLrcParsesTimestampedLines() {
        Lyrics lyrics = LyricParser.INSTANCE.parseLrc(
                "[00:01.50]First line\n" +
                "[00:03.00]Second line"
        );

        assertEquals(2, lyrics.getLines().size());
        assertEquals(1_500L, lyrics.getLines().get(0).getTimeMs());
        assertEquals("First line", lyrics.getLines().get(0).getText());
        assertEquals(3_000L, lyrics.getLines().get(1).getTimeMs());
        assertEquals("Second line", lyrics.getLines().get(1).getText());
    }

    @Test
    public void parseLrcSupportsMultipleTimestampsPerLine() {
        Lyrics lyrics = LyricParser.INSTANCE.parseLrc("[00:01.00][00:02.50]Repeated line");

        assertEquals(2, lyrics.getLines().size());
        assertEquals(1_000L, lyrics.getLines().get(0).getTimeMs());
        assertEquals("Repeated line", lyrics.getLines().get(0).getText());
        assertEquals(2_500L, lyrics.getLines().get(1).getTimeMs());
        assertEquals("Repeated line", lyrics.getLines().get(1).getText());
    }

    @Test
    public void currentLineUsesLatestLineAtOrBeforePosition() {
        Lyrics lyrics = LyricParser.INSTANCE.parseLrc(
                "[00:01.00]First line\n" +
                "[00:03.00]Second line\n" +
                "[00:05.00]Third line"
        );

        assertNull(lyrics.currentLine(500));
        assertEquals("First line", lyrics.currentLine(1_000).getText());
        assertEquals("Second line", lyrics.currentLine(4_000).getText());
        assertEquals("Third line", lyrics.currentLine(8_000).getText());
    }

    @Test
    public void nextLineReturnsLineAfterCurrentPosition() {
        Lyrics lyrics = LyricParser.INSTANCE.parseLrc(
                "[00:01.00]First line\n" +
                "[00:03.00]Second line"
        );

        assertEquals("First line", lyrics.nextLine(500).getText());
        assertEquals("Second line", lyrics.nextLine(1_000).getText());
        assertNull(lyrics.nextLine(3_000));
    }
}
