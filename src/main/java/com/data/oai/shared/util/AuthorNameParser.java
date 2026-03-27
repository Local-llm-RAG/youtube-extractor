package com.data.oai.shared.util;

import com.data.oai.shared.dto.Author;

/**
 * Shared utility for parsing author name strings into {@link Author} objects.
 * Handles both "LastName, FirstName" and "FirstName LastName" formats.
 */
public final class AuthorNameParser {

    private AuthorNameParser() {}

    /**
     * Parses a full name string into an {@link Author}.
     *
     * <p>If the name contains a comma, it is split as "LastName, FirstName".
     * Otherwise, the last space-separated token is treated as the last name,
     * and all preceding tokens form the first name.</p>
     *
     * @param name the full author name string
     * @return a populated Author, never null
     */
    public static Author parse(String name) {
        Author author = new Author();

        if (name.contains(",")) {
            String[] parts = name.split(",", 2);
            author.lastName = parts[0].trim();
            author.firstName = parts[1].trim();
        } else {
            String[] tokens = name.trim().split("\\s+");
            if (tokens.length == 1) {
                author.lastName = name.trim();
            } else {
                author.lastName = tokens[tokens.length - 1];
                author.firstName = String.join(" ",
                        java.util.Arrays.copyOf(tokens, tokens.length - 1));
            }
        }

        return author;
    }
}
