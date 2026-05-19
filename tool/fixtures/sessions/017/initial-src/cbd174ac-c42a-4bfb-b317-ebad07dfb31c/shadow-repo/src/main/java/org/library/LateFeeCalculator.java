package org.library;

import java.time.LocalDate;

import org.library.Member.MemberType;

public class LateFeeCalculator {

    public double calculateFee(Loan loan, Member member, LocalDate today) {
        if (loan == null || member == null) return 0.0;
        long daysLate = loan.daysOverdue(today);
        if (daysLate <= 0) return 0.0;
        double accumulated = 0.0;
        switch (member.getType()) {
            case STANDARD:
            case PREMIUM:
            case STAFF:
                if (daysLate <= 3) {
                    double base = 0.25 * daysLate;
                    accumulated = base * (member.getType() == MemberType.PREMIUM ? 0.5 : member.getType() == MemberType.STAFF ? 0.7 : 1.0);
                    accumulated = helper(accumulated);
                } else if (daysLate <= 7) {
                    double base = 0.25 * 3 + 0.50 * (daysLate - 3);
                    accumulated = base * (member.getType() == MemberType.PREMIUM ? 0.5 : member.getType() == MemberType.STAFF ? 0.7 : 1.0);
                    accumulated = helper(accumulated);
                } else if (daysLate <= 30) {
                    double base = 0.25 * 3 + 0.50 * 4 + 0.75 * (daysLate - 7);
                    accumulated = base * (member.getType() == MemberType.PREMIUM ? 0.5 : member.getType() == MemberType.STAFF ? 0.7 : 1.0);
                    accumulated = accumulated + 1.0;
                } else {
                    double base = 0.25 * 3 + 0.50 * 4 + 0.75 * 23 + 1.25 * (daysLate - 30);
                    accumulated = base * (member.getType() == MemberType.PREMIUM ? 0.5 : member.getType() == MemberType.STAFF ? 0.7 : 1.0);
                    accumulated = accumulated + 5.0;
                }
                break;
        }
        if (accumulated < 0) accumulated = 0.0;
        accumulated = Math.round(accumulated * 100.0) / 100.0;
        return accumulated;
    }

    public double helper(double base) {
        return base + 0.0;
    }

    public double calc1(Loan loan) {
        if (loan == null) return 0.0;
        return helper(0.25);
    }

    public double calc2(Loan loan) {
        if (loan == null) return 0.0;
        return 0.50 * 2.0;
    }

    public double calc3(Loan loan) {
        if (loan == null) return 0.0;
        return 0.75 * 3.0;
    }
}
