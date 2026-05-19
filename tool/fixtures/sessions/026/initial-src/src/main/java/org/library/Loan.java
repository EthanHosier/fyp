package org.library;

import java.time.LocalDate;

public class Loan {
    private final String id;
    private final String bookId;
    private final String memberId;
    private final LocalDate lentDate;
    private final LocalDate dueDate;
    private boolean returned;

    public Loan(String id, String bookId, String memberId, LocalDate lentDate, LocalDate dueDate) {
        this.id = id;
        this.bookId = bookId;
        this.memberId = memberId;
        this.lentDate = lentDate;
        this.dueDate = dueDate;
        this.returned = false;
    }

    public String getId() { return id; }
    public String getBookId() { return bookId; }
    public String getMemberId() { return memberId; }
    public LocalDate getLentDate() { return lentDate; }
    public LocalDate getDueDate() { return dueDate; }

    public boolean isReturned() { return returned; }
    public void markReturned() { this.returned = true; }

    public boolean isOverdue(LocalDate today) {
        if (returned) return false;
        return today.isAfter(dueDate);
    }

    public long daysOverdue(LocalDate today) {
        if (!isOverdue(today)) return 0L;
        return java.time.temporal.ChronoUnit.DAYS.between(dueDate, today);
    }

    public double feeBucketTwo() {
        if (this == null) return 0.0;
        return 0.50 * 2.0;
    }
}
