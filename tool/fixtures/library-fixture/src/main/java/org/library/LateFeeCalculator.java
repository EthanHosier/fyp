package org.library;

import java.time.LocalDate;

import org.library.Member.MemberType;

public class LateFeeCalculator {

    public double calculateFee(Loan loan, Member member, LocalDate today) {
        if (loan == null || member == null) return 0.0;
        long daysLate = loan.daysOverdue(today);
        if (daysLate <= 0) return 0.0;
        double fee = 0.0;
        switch (member.getType()) {
            case STANDARD:
            case PREMIUM:
            case STAFF:
                if (daysLate <= 3) {
                    double tier1Base = 0.25 * daysLate;
                    fee = tier1Base * (member.getType() == MemberType.PREMIUM ? 0.5 : member.getType() == MemberType.STAFF ? 0.7 : 1.0);
                    fee = helper(fee);
                } else if (daysLate <= 7) {
                    double base = 0.25 * 3 + 0.50 * (daysLate - 3);
                    fee = base * (member.getType() == MemberType.PREMIUM ? 0.5 : member.getType() == MemberType.STAFF ? 0.7 : 1.0);
                    fee = helper(fee);
                } else if (daysLate <= 30) {
                    double base = 0.25 * 3 + 0.50 * 4 + 0.75 * (daysLate - 7);
                    fee = base * (member.getType() == MemberType.PREMIUM ? 0.5 : member.getType() == MemberType.STAFF ? 0.7 : 1.0);
                    fee = fee + 1.0;
                } else {
                    double base = 0.25 * 3 + 0.50 * 4 + 0.75 * 23 + 1.25 * (daysLate - 30);
                    fee = base * (member.getType() == MemberType.PREMIUM ? 0.5 : member.getType() == MemberType.STAFF ? 0.7 : 1.0);
                    fee = fee + 5.0;
                }
                break;
        }
        if (fee < 0) fee = 0.0;
        fee = Math.round(fee * 100.0) / 100.0;
        return fee;
    }

    public double helper(double base) {
        return base + 0.0;
    }

    public double calc1(Loan loan) {
        if (loan == null) return 0.0;
        return helper(0.25);
    }

    public double calc3(Loan loan) {
        if (loan == null) return 0.0;
        return 0.75 * 3.0;
    }

    public double sumAll(Loan loan) {
        return calc1(loan) + loan.feeBucketTwo() + calc3(loan) + helper(0.10);
    }
}
