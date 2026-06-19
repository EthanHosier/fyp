```shell
cd /Users/ethanhosier/Desktop/random/fyp/tool/dashboard
REFDASH_REPORT=/Users/ethanhosier/Desktop/random/fyp/presentation/backup-demo-session/analysis-report.json
```

Block 1 (lines 11–34) → String validate(customer, items)
The whole validation chunk ends with a single String error local. Select it, Extract Method, signature becomes:
String error = validate(customer, items);
The if (error != null) { ... return result; } stays in checkout() — that's the early-return pattern the audience expects.

Block 2 (lines 36–40) → double calculateTaxedSubtotal(items)
Single output: taxed. Extract becomes:
double taxed = calculateTaxedSubtotal(items);

Block 3 (lines 42–56) → double calculateFinalTotal(taxed, items, customer)
Single output: finalTotal. The discount and totalQty locals live entirely inside the block, so they get pulled in cleanly. Extract becomes:
double finalTotal = calculateFinalTotal(taxed, items, customer);

Block 4 (lines 61–66) → void addConfirmationMessages(result, customer, items, finalTotal)
Pure side effects on result. Extract becomes:no