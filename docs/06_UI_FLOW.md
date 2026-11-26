# 06. UI FLOW & SCREEN DESIGN

## 1. User Journey Map
```text
┌─────────────────┐     Types Text / Sends SMS     ┌────────────────────────┐
│  Landing / SPA  │ ────────────────────────────► │ Quick Input Component  │
└────────┬────────┘                                └───────────┬────────────┘
         │                                                     │
         │ Renders Data                                        │ Triggers API
         ▼                                                     ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                           Dashboard Components                            │
│ ┌────────────────────────┐ ┌──────────────────────┐ ┌────────────────────┐ │
│ │  Account Balance Card  │ │  Budget Progress     │ │ Transaction Table  │ │
│ │  (Total Cash Flow)     │ │  Gauges (Percentage) │ │ (Recent Ledgers)   │ │
│ └────────────────────────┘ └──────────────────────┘ └────────────────────┘ │
└───────────────────────────────────────────────────────────────────────────┘
```

## 2. Dashboard Component Layout

```text
+-----------------------------------------------------------------------+
|  SMART LIFE ASSISTANT                                     [User Profile] |
+-----------------------------------------------------------------------+
|                                                                       |
|  [ Natural Language Quick Log Input                                ]  |
|  | "Spent 450 rupees on dinner at Swiggy"              [ Submit ] |  |
|                                                                       |
+-----------------------------------------------------------------------+
|  ACCOUNT SUMMARY            |  BUDGET CAP GAUGES                      |
|  Balance: ₹49,550.00        |  Food & Dining: [====......] 45%        |
|  Monthly Spent: ₹4,500.00   |  Transportation: [==........] 20%       |
+-----------------------------+-----------------------------------------+
|  RECENT TRANSACTIONS LEDGER                                           |
|  Date         | Merchant          | Category      | Amount    | Type  |
|  11-Nov-2026  | Swiggy            | Food & Dining | ₹450.00   | EXP   |
|  10-Nov-2026  | Shell Fuel        | Transport     | ₹1,200.00 | EXP   |
+-----------------------------------------------------------------------+
```

## 3. Four Mandatory UI States Per Component
Following Section 4 (Phase 10) of the ADF framework, every React page component must explicitly handle:

1. **Loading State:** Displays animated skeleton loaders while REST API calls process.
2. **Error State:** Displays an inline, user-friendly alert with a retry button if the dashboard request fails. When a refresh fails after data has loaded, retain the last loaded data and show the alert.
3. **Empty State:** Shows a clear placeholder message when no transactions or budgets exist.
4. **Data State:** Renders live metrics, transaction rows, and progress gauges.