package com.instakill.funnyfy.application;

import com.instakill.common.error.BadGatewayException;
import com.instakill.common.error.ValidationException;
import com.instakill.funnyfy.domain.TextRewriter;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * "Make my post funnier": sends the caption to a {@link TextRewriter} and tidies the answer so it can be
 * put straight back into the post composer. Touches no database, so it is not transactional.
 */
@Service
public class FunnyfyService {

    /** Same limit as {@code Post.content}, so the funnier text can always be posted. */
    public static final int MAX_LENGTH = 1000;

    private final TextRewriter rewriter;

    public FunnyfyService(TextRewriter rewriter) {
        this.rewriter = rewriter;
    }

    public String makeFunnier(String text) {
        if (text == null || text.isBlank()) {
            throw new ValidationException("Text is required", Map.of("text", "must not be blank"));
        }
        String funnier = clean(rewriter.rewrite(text.strip()));
        if (funnier.isEmpty()) {
            throw new BadGatewayException("The local LLM returned an empty answer");
        }
        return truncate(funnier);
    }

    /** Small models like to wrap their answer in quotes; drop them and surrounding whitespace. */
    static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.strip();
        while (s.length() >= 2 && isWrapped(s)) {
            s = s.substring(1, s.length() - 1).strip();
        }
        return s;
    }

    private static boolean isWrapped(String s) {
        char first = s.charAt(0);
        char last = s.charAt(s.length() - 1);
        String inner = s.substring(1, s.length() - 1);
        if (first == '"' && last == '"') {
            return inner.indexOf('"') < 0; // "Hi" said the cat, "bye" is not one quoted answer
        }
        return first == '“' && last == '”' && inner.indexOf('“') < 0 && inner.indexOf('”') < 0;
    }

    private static String truncate(String s) {
        if (s.length() <= MAX_LENGTH) {
            return s;
        }
        int end = MAX_LENGTH;
        if (Character.isHighSurrogate(s.charAt(end - 1))) {
            end--; // don't cut an emoji in half
        }
        return s.substring(0, end).strip();
    }
}
