package com.wode.app.service;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MovieSourceStoreTest {

    @Test
    public void normalizeUrlUsesDefaultForBlankInput() {
        assertEquals(
                MovieSourceStore.DEFAULT_SOURCE_URL,
                MovieSourceStore.Companion.normalizeUrl("   ")
        );
    }

    @Test
    public void normalizeUrlAddsHttpsWhenSchemeIsMissing() {
        assertEquals(
                "https://example.com/path",
                MovieSourceStore.Companion.normalizeUrl("example.com/path")
        );
    }

    @Test
    public void normalizeUrlKeepsExistingHttpScheme() {
        assertEquals(
                "http://example.com",
                MovieSourceStore.Companion.normalizeUrl("http://example.com")
        );
    }
}
