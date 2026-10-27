package com.iafenvoy.system.search;

import java.io.IOException;

/**
 * Stable local error codes and HTTP status, without exposing credentials or provider response bodies.
 */
public final class SearchException extends IOException {
    private final String code;
    private final int statusCode;

    public SearchException(String code) {
        this(code, 0, null);
    }

    public SearchException(String code, Throwable cause) {
        this(code, 0, cause);
    }

    public SearchException(int statusCode) {
        this("SEARCH_HTTP_ERROR", statusCode, null);
    }

    private SearchException(String code, int statusCode, Throwable cause) {
        super(statusCode == 0 ? code : code + " (HTTP " + statusCode + ")", cause);
        this.code = code;
        this.statusCode = statusCode;
    }

    public String code() {
        return this.code;
    }

    public int statusCode() {
        return this.statusCode;
    }
}
