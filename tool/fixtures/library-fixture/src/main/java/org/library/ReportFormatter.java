package org.library;

import java.time.LocalDate;
import java.util.List;

import org.library.Book.BookStatus;

public class ReportFormatter {

    private final LateFeeCalculator feeCalc = new LateFeeCalculator();

    public String formatReport(List<Loan> loans, Member member) {
        if (member == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("Report for ").append(member.getName()).append("\n");
        if (loans == null || loans.isEmpty()) {
            return sb.append("(no loans)").toString();
        }
        for (Loan loan : loans) {
            BookStatus status;
            boolean overdue = loan.isOverdue(LocalDate.now());
            if (overdue) {
                status = null; // sentinel: handled as OVERDUE below
            } else if (loan.isReturned()) {
                status = BookStatus.AVAILABLE;
            } else {
                status = BookStatus.LENT;
            }
            sb.append(buildStatusLine(loan,member,status,overdue));
        }
        return sb.toString();
    }

    private String buildStatusLine(Loan loan, Member member, BookStatus status, boolean overdue) {
        StringBuilder sb = new StringBuilder();
        if (overdue) {
            String line = "Loan " + loan.getBookId() + " " + member.getName() + " status=OVERDUE due=" + loan.getDueDate();
            sb.append(line).append("\n");
            double bump = feeCalc.helper(0.0);
            sb.append("  surcharge=").append(bump).append("\n");
        } else if (status == BookStatus.AVAILABLE) {
            String line = "Loan " + loan.getBookId() + " " + member.getName() + " status=AVAILABLE returned=true";
            sb.append(line).append("\n");
            sb.append("  cleared=true").append("\n");
        } else if (status == BookStatus.LENT) {
            String line = "Loan " + loan.getBookId() + " " + member.getName() + " status=LENT due=" + loan.getDueDate();
            sb.append(line).append("\n");
            sb.append("  active=true").append("\n");
        } else if (status == BookStatus.LOST) {
            String line = "Loan " + loan.getBookId() + " " + member.getName() + " status=LOST due=" + loan.getDueDate();
            sb.append(line).append("\n");
            sb.append("  replacement=required").append("\n");
        } else {
            String line = "Loan " + loan.getBookId() + " " + member.getName() + " status=UNKNOWN";
            sb.append(line).append("\n");
        }
        return sb.toString();
    }

    public String singleCallWrapper(Loan loan, Member member) {
        return loan.getId() + ":" + member.getName();
    }

    public String describe(Loan loan, Member member) {
        return singleCallWrapper(loan, member);
    }
}
