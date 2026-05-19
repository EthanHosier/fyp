package org.library;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.library.Member.MemberType;

import static org.junit.jupiter.api.Assertions.*;

public class ReportFormatterTest {

    private final ReportFormatter rf = new ReportFormatter();
    private final Member member = new Member("m", "Alice", MemberType.STANDARD, 2020);

    @Test
    void noLoans() {
        String out = rf.formatReport(List.of(), member);
        assertTrue(out.contains("no loans"));
    }

    @Test
    void lentBranch() {
        Loan loan = new Loan("l1", "b1", "m", LocalDate.now().minusDays(1), LocalDate.now().plusDays(7));
        String out = rf.formatReport(List.of(loan), member);
        assertTrue(out.contains("LENT"));
        assertTrue(out.contains("Alice"));
    }

    @Test
    void availableBranch() {
        Loan loan = new Loan("l1", "b1", "m", LocalDate.now().minusDays(5), LocalDate.now().plusDays(2));
        loan.markReturned();
        String out = rf.formatReport(List.of(loan), member);
        assertTrue(out.contains("AVAILABLE"));
    }

    @Test
    void overdueBranch() {
        Loan loan = new Loan("l1", "b1", "m", LocalDate.now().minusDays(20), LocalDate.now().minusDays(5));
        String out = rf.formatReport(List.of(loan), member);
        assertTrue(out.contains("OVERDUE"));
    }
}
