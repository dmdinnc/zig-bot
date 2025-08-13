package com.discordbot.crossword.types;

import java.time.LocalDate;

public class FullCompletionRecord {
    private LocalDate date;
    private CrosswordCompletion completion;

    public FullCompletionRecord(LocalDate date, CrosswordCompletion completion) {
        this.date = date;
        this.completion = completion;
    }

    public LocalDate getDate() {
        return date;
    }

    public CrosswordCompletion getCompletion() {
        return completion;
    }
}
