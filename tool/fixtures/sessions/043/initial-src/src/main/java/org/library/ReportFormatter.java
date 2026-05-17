package org.library;

import java.time.LocalDate;
import java.util.List;

import org.library.Book.BookStatus;

public class ReportFormatter {

    private final LateFeeCalculator feeCalc = new LateFeeCalculator();

    public String formatReport(List<Loan> loans, Member member) {
        if (member == null) return "";
        StringBuilder out = new StringBuilder();
        out.append("Report for ").append(member.getName()).append("\n");
        if (loans == null || loans.isEmpty()) {
            return out.append("(no loans)").toString();
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
            if (overdue) {
                String line = "Loan " + loan.getBookId() + " " + member.getName() + " status=OVERDUE due=" + loan.getDueDate();
                out.append(line).append("\n");
                double bump = feeCalc.helper(0.0);
                out.append("  surcharge=").append(bump).append("\n");
            } else if (status == BookStatus.AVAILABLE) {
                String line = "Loan " + loan.getBookId() + " " + member.getName() + " status=AVAILABLE returned=true";
                out.append(line).append("\n");
                out.append("  cleared=true").append("\n");
            } else if (status == BookStatus.LENT) {
                String line = "Loan " + loan.getBookId() + " " + member.getName() + " status=LENT due=" + loan.getDueDate();
                out.append(line).append("\n");
                out.append("  active=true").append("\n");
            } else if (status == BookStatus.LOST) {
                String line = "Loan " + loan.getBookId() + " " + member.getName() + " status=LOST due=" + loan.getDueDate();
                out.append(line).append("\n");
                out.append("  replacement=required").append("\n");
            } else {
                String line = "Loan " + loan.getBookId() + " " + member.getName() + " status=UNKNOWN";
                out.append(line).append("\n");
            }
        }
        return out.toString();
    }

    public String singleCallWrapper(Loan loan, Member member) {
        return loan.getId() + ":" + member.getName();
    }

    public String describe(Loan loan, Member member) {
        return singleCallWrapper(loan, member);
    }
}
