package com.instakill.funnyfy.application;

import com.instakill.common.error.BadGatewayException;
import com.instakill.common.error.UpstreamUnavailableException;
import com.instakill.common.error.ValidationException;
import com.instakill.funnyfy.domain.TextRewriter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FunnyfyServiceTest {

    private final TextRewriter rewriter = mock(TextRewriter.class);
    private final FunnyfyService service = new FunnyfyService(rewriter);

    @Test
    void returnsTheRewrittenText() {
        when(rewriter.rewrite("Today I'm at my 7th lab.")).thenReturn("  Lab number 7: still alive!  ");

        assertThat(service.makeFunnier("  Today I'm at my 7th lab.\n")).isEqualTo("Lab number 7: still alive!");
    }

    @Test
    void stripsQuotesTheModelWrapsAroundItsAnswer() {
        when(rewriter.rewrite(anyString())).thenReturn("\"\u201CCoffee is my co-pilot\u201D\"");

        assertThat(service.makeFunnier("I drink coffee")).isEqualTo("Coffee is my co-pilot");
    }

    @Test
    void keepsQuotesThatAreOnlyPartOfTheText() {
        when(rewriter.rewrite(anyString())).thenReturn("\"Hi\" said the cat, \"bye\"");

        assertThat(service.makeFunnier("hi")).isEqualTo("\"Hi\" said the cat, \"bye\"");
    }

    @Test
    void rejectsBlankTextWithoutCallingTheModel() {
        assertThatThrownBy(() -> service.makeFunnier("   ")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.makeFunnier(null)).isInstanceOf(ValidationException.class);
        verify(rewriter, never()).rewrite(anyString());
    }

    @Test
    void emptyAnswerIsABadGateway() {
        when(rewriter.rewrite(anyString())).thenReturn(" \"\" ");

        assertThatThrownBy(() -> service.makeFunnier("hello")).isInstanceOf(BadGatewayException.class);
    }

    @Test
    void capsTheAnswerAtThePostLimitWithoutSplittingAnEmoji() {
        String answer = "a".repeat(FunnyfyService.MAX_LENGTH - 1) + "😎 and more";
        when(rewriter.rewrite(anyString())).thenReturn(answer);

        String result = service.makeFunnier("hello");

        assertThat(result).hasSize(FunnyfyService.MAX_LENGTH - 1).doesNotContain("\uD83D");
    }

    @Test
    void letsUpstreamErrorsThrough() {
        when(rewriter.rewrite(anyString())).thenThrow(new UpstreamUnavailableException("down", null));

        assertThatThrownBy(() -> service.makeFunnier("hello")).isInstanceOf(UpstreamUnavailableException.class);
    }

    @Test
    void anyTextRewriterPlugsInWithoutChangingTheService() {
        TextRewriter shouting = text -> text.toUpperCase() + "!!!";

        assertThat(new FunnyfyService(shouting).makeFunnier("hello")).isEqualTo("HELLO!!!");
    }
}
