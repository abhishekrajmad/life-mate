import { useEffect, useState } from 'react';
import {
  Wallet,
  Sparkles,
  AlertTriangle,
  TrendingDown,
  Send,
  Receipt,
  CheckCircle2,
  RefreshCw,
  TrendingUp,
} from 'lucide-react';

const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api/v1';
const DEFAULT_CATEGORIES = [
  'Food & Dining',
  'Groceries',
  'Transport',
  'Utilities & Bills',
  'Shopping',
  'Entertainment',
  'Health & Fitness',
  'Other',
];

const getDashboardSummary = async () => {
  const res = await fetch(`${API_BASE}/dashboard/summary`);
  if (!res.ok) throw new Error('Failed to load dashboard summary');
  return res.json();
};

export default function App() {
  const [summary, setSummary] = useState(null);
  const [summaryError, setSummaryError] = useState(null);
  const [rawText, setRawText] = useState('');
  const [manualFallback, setManualFallback] = useState(null);
  const [manualAmount, setManualAmount] = useState('');
  const [manualCategory, setManualCategory] = useState('Food & Dining');
  const [manualMerchant, setManualMerchant] = useState('');
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [toast, setToast] = useState(null);
  const manualCategoryOptions = [...new Set([
    ...DEFAULT_CATEGORIES,
    ...(summary?.categoryBudgets ?? []).map((category) => category.categoryName),
  ])];

  useEffect(() => {
    let isCurrent = true;

    const loadSummary = async () => {
      try {
        const data = await getDashboardSummary();
        if (isCurrent) setSummary(data);
      } catch {
        if (isCurrent) setSummaryError('Could not load the dashboard. Please try again.');
      } finally {
        if (isCurrent) setLoading(false);
      }
    };

    loadSummary();
    return () => {
      isCurrent = false;
    };
  }, []);

  const refreshSummary = async () => {
    setLoading(true);
    setSummaryError(null);

    try {
      setSummary(await getDashboardSummary());
    } catch {
      setSummaryError('Could not refresh the dashboard. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const handleQuickAdd = async (e) => {
    e.preventDefault();
    if (!rawText.trim() || submitting) return;

    setSubmitting(true);
    setToast(null);

    try {
      const res = await fetch(`${API_BASE}/expenses/parse`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          rawText: rawText.trim(),
          ...(manualFallback && {
            manualFallback: {
              amount: manualAmount,
              categoryName: manualCategory,
              merchant: manualMerchant,
            },
          }),
        }),
      });

      if (!res.ok) {
        const errData = await res.json().catch(() => ({}));
        if (res.status === 503 && errData.details?.manualFallbackRequired === 'true') {
          setManualFallback(true);
          setToast({ type: 'error', message: 'AI parsing is unavailable. Enter the transaction details manually.' });
          return;
        }
        throw new Error(errData.message || 'AI parsing failed');
      }

      const created = await res.json();
      setRawText('');
      setManualFallback(null);
      setManualAmount('');
      setManualMerchant('');
      setToast({
        type: 'success',
        message: `Logged ₹${created.amount.toLocaleString('en-IN')} at ${created.merchant} (${created.categoryName})`,
      });
      await refreshSummary();
    } catch (err) {
      setToast({ type: 'error', message: err.message });
    } finally {
      setSubmitting(false);
    }
  };

  const formatINR = (val) =>
    new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 0,
    }).format(val || 0);

  const formatTransactionDate = (value) => {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return 'Date unavailable';
    return date.toLocaleDateString('en-IN', {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100">
      {/* Top Navbar */}
      <header className="border-b border-slate-800 bg-slate-900/60 backdrop-blur sticky top-0 z-10">
        <div className="max-w-6xl mx-auto px-6 py-4 flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <div className="w-9 h-9 rounded-xl bg-emerald-500/15 border border-emerald-500/30 flex items-center justify-center">
              <Wallet className="w-5 h-5 text-emerald-400" />
            </div>
            <div>
              <h1 className="text-lg font-semibold tracking-tight">Life Mate</h1>
              <p className="text-xs text-slate-400">AI Personal Finance</p>
            </div>
          </div>
          <button
            onClick={refreshSummary}
            disabled={loading}
            className="text-xs px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 flex items-center gap-1.5 transition cursor-pointer"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
            {loading ? (summary ? 'Refreshing...' : 'Loading...') : 'Refresh'}
          </button>
        </div>
      </header>

      <main className="max-w-6xl mx-auto px-6 py-8 space-y-8">
        {/* AI Natural Language Quick Entry */}
        <section className="bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-xl">
          <div className="flex items-center gap-2 mb-3">
            <Sparkles className="w-4 h-4 text-emerald-400" />
            <h2 className="text-sm font-medium text-slate-200">
              AI Quick Expense Entry
            </h2>
          </div>
          <form onSubmit={handleQuickAdd} className="flex flex-col gap-3">
            <div className="flex flex-col sm:flex-row gap-3">
              <input
                type="text"
                value={rawText}
                onChange={(e) => setRawText(e.target.value)}
                placeholder='Try: "Paid 420 for coffee and snacks at Third Wave" or paste a bank SMS...'
                className="flex-1 bg-slate-950 border border-slate-700 rounded-xl px-4 py-3 text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:border-emerald-500"
              />
              <button
                type="submit"
                disabled={submitting || !rawText.trim()}
                className="px-6 py-3 rounded-xl bg-emerald-500 hover:bg-emerald-400 disabled:opacity-50 text-slate-950 font-semibold text-sm flex items-center justify-center gap-2 transition cursor-pointer"
              >
                {submitting ? (
                  <>
                    <RefreshCw className="w-4 h-4 animate-spin" />
                    Parsing...
                  </>
                ) : (
                  <>
                    <Send className="w-4 h-4" />
                    {manualFallback ? 'Log Manually' : 'Log Expense'}
                  </>
                )}
              </button>
            </div>

            {manualFallback && (
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                <label className="text-xs text-slate-400">
                  Amount
                  <input
                    type="number"
                    min="0.01"
                    step="0.01"
                    required
                    value={manualAmount}
                    onChange={(event) => setManualAmount(event.target.value)}
                    className="mt-1 w-full bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-sm text-slate-100"
                  />
                </label>
                <label className="text-xs text-slate-400">
                  Category
                  <select
                    required
                    value={manualCategory}
                    onChange={(event) => setManualCategory(event.target.value)}
                    className="mt-1 w-full bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-sm text-slate-100"
                  >
                    {manualCategoryOptions.map((category) => (
                      <option key={category} value={category}>{category}</option>
                    ))}
                  </select>
                </label>
                <label className="text-xs text-slate-400">
                  Merchant
                  <input
                    type="text"
                    maxLength="100"
                    value={manualMerchant}
                    onChange={(event) => setManualMerchant(event.target.value)}
                    className="mt-1 w-full bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-sm text-slate-100"
                  />
                </label>
              </div>
            )}
          </form>

          {toast && (
            <div
              className={`mt-4 px-4 py-3 rounded-xl text-sm flex items-center gap-2 border ${
                toast.type === 'success'
                  ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-300'
                  : 'bg-rose-500/10 border-rose-500/30 text-rose-300'
              }`}
            >
              {toast.type === 'success' ? (
                <CheckCircle2 className="w-4 h-4 shrink-0" />
              ) : (
                <AlertTriangle className="w-4 h-4 shrink-0" />
              )}
              <span>{toast.message}</span>
            </div>
          )}
        </section>

        {loading && !summary && (
          <section
            aria-label="Loading dashboard"
            aria-busy="true"
            className="space-y-6 animate-pulse"
          >
            <p className="sr-only">Loading dashboard summary</p>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              {[0, 1, 2].map((card) => (
                <div key={card} className="bg-slate-900 border border-slate-800 rounded-2xl p-5 space-y-3">
                  <div className="h-3 w-2/5 rounded bg-slate-800" />
                  <div className="h-8 w-3/5 rounded bg-slate-800" />
                  <div className="h-3 w-1/2 rounded bg-slate-800" />
                </div>
              ))}
            </div>
            <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">
              <div className="lg:col-span-2 h-56 bg-slate-900 border border-slate-800 rounded-2xl" />
              <div className="lg:col-span-3 h-56 bg-slate-900 border border-slate-800 rounded-2xl" />
            </div>
          </section>
        )}

        {!loading && !summary && (
          <section
            role="alert"
            className="bg-rose-500/10 border border-rose-500/30 rounded-2xl p-6 text-rose-200"
          >
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
              <div className="flex items-start gap-3">
                <AlertTriangle className="w-5 h-5 mt-0.5 shrink-0" />
                <div>
                  <h2 className="font-semibold">Dashboard unavailable</h2>
                  <p className="text-sm mt-1 text-rose-200/80">
                    {summaryError || 'The dashboard could not be loaded. Please try again.'}
                  </p>
                </div>
              </div>
              <button
                onClick={refreshSummary}
                disabled={loading}
                className="px-4 py-2 rounded-lg bg-rose-500/15 hover:bg-rose-500/25 disabled:opacity-50 text-sm font-medium transition cursor-pointer"
              >
                Retry
              </button>
            </div>
          </section>
        )}

        {summary && (
          <>
            {summaryError && (
              <section
                role="alert"
                className="bg-amber-500/10 border border-amber-500/30 rounded-xl p-4 text-amber-200 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3"
              >
                <p className="text-sm">{summaryError} Showing the last loaded data.</p>
                <button
                  onClick={refreshSummary}
                  disabled={loading}
                  className="px-3 py-1.5 rounded-lg bg-amber-500/15 hover:bg-amber-500/25 disabled:opacity-50 text-sm font-medium transition cursor-pointer"
                >
                  Retry
                </button>
              </section>
            )}

            {/* Top KPI Summary Cards */}
            <section className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5">
                <p className="text-xs uppercase tracking-wider text-slate-400">
                  Available Balance
                </p>
                <p className="text-3xl font-bold text-white mt-2">
                  {formatINR(summary.totalBalance)}
                </p>
                <p className="text-xs text-slate-500 mt-1">Primary Savings Account</p>
              </div>

              <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5">
                <p className="text-xs uppercase tracking-wider text-slate-400">
                  Month-to-Date Spend
                </p>
                <p className="text-3xl font-bold text-rose-400 mt-2">
                  {formatINR(summary.totalMonthlySpent)}
                </p>
                <p className="text-xs text-slate-500 mt-1">
                  Across {summary.categoryBudgets.length} categories
                </p>
              </div>

              <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5">
                <p className="text-xs uppercase tracking-wider text-slate-400">
                  Total Monthly Budget
                </p>
                <p className="text-3xl font-bold text-emerald-400 mt-2">
                  {formatINR(summary.totalMonthlyBudget)}
                </p>
                <p className="text-xs text-slate-500 mt-1">
                  {summary.categoryBudgets.filter((c) => c.exceeded).length} category alert(s)
                </p>
              </div>
            </section>

            {/* Category Budget Progress + Recent Ledger */}
            <section className="grid grid-cols-1 lg:grid-cols-5 gap-6">
              {/* Left 2 cols: Category Budgets */}
              <div className="lg:col-span-2 bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4">
                <h3 className="text-base font-semibold">Monthly Category Budgets</h3>
                <div className="space-y-4">
                  {summary.categoryBudgets.length === 0 ? (
                    <p className="text-sm text-slate-400">
                      No category budgets are available yet.
                    </p>
                  ) : summary.categoryBudgets.map((cat) => {
                    const barWidth = Math.min(cat.percentageUsed, 100);
                    const barColor = cat.exceeded
                      ? 'bg-rose-500'
                      : cat.percentageUsed >= 80
                      ? 'bg-amber-400'
                      : 'bg-emerald-500';

                    return (
                      <div key={cat.categoryId} className="space-y-1.5">
                        <div className="flex items-center justify-between text-sm">
                          <span className="font-medium text-slate-200 flex items-center gap-1.5">
                            {cat.categoryName}
                            {cat.exceeded && (
                              <span className="text-[10px] px-1.5 py-0.5 rounded bg-rose-500/20 text-rose-300 font-semibold">
                                OVER
                              </span>
                            )}
                          </span>
                          <span className="text-xs text-slate-400">
                            {formatINR(cat.spent)} / {formatINR(cat.monthlyLimit)}
                          </span>
                        </div>
                        <div className="w-full h-2 bg-slate-800 rounded-full overflow-hidden">
                          <div
                            className={`h-full ${barColor} transition-all duration-300`}
                            style={{ width: `${barWidth}%` }}
                          />
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>

              {/* Right 3 cols: Recent Transactions */}
              <div className="lg:col-span-3 bg-slate-900 border border-slate-800 rounded-2xl p-6">
                <div className="flex items-center justify-between mb-4">
                  <h3 className="text-base font-semibold flex items-center gap-2">
                    <Receipt className="w-4 h-4 text-slate-400" />
                    Recent Transactions
                  </h3>
                  <span className="text-xs text-slate-500">
                    Showing latest {summary.recentTransactions.length}
                  </span>
                </div>

                <div className="divide-y divide-slate-800">
                  {summary.recentTransactions.length === 0 ? (
                    <p className="py-4 text-sm text-slate-400">
                      No transactions yet. Log one above to see it here.
                    </p>
                  ) : summary.recentTransactions.map((tx) => {
                    const transactionType = String(tx.transactionType || 'EXPENSE').toUpperCase();
                    const isIncome = transactionType === 'INCOME';

                    return (
                      <div
                        key={tx.id}
                        className="py-3.5 flex items-center justify-between gap-4"
                      >
                        <div className="min-w-0">
                          <p className="font-medium text-sm text-slate-100 truncate">
                            {tx.merchant || 'Merchant unavailable'}
                          </p>
                          <div className="flex flex-wrap items-center gap-2 mt-1">
                            <span className="text-xs px-2 py-0.5 rounded-md bg-slate-800 text-slate-300">
                              {tx.categoryName || 'Uncategorized'}
                            </span>
                            <span
                              className={`text-[10px] px-1.5 py-0.5 rounded-md font-semibold ${
                                isIncome
                                  ? 'bg-emerald-500/15 text-emerald-300'
                                  : 'bg-rose-500/15 text-rose-300'
                              }`}
                            >
                              {isIncome ? 'INCOME' : 'EXPENSE'}
                            </span>
                            <span className="text-xs text-slate-500 truncate">
                              {formatTransactionDate(tx.createdAt)}
                            </span>
                          </div>
                        </div>
                        <div className="text-right shrink-0">
                          <p
                            className={`text-sm font-semibold flex items-center gap-1 ${
                              isIncome ? 'text-emerald-400' : 'text-rose-400'
                            }`}
                          >
                            {isIncome ? (
                              <TrendingUp className="w-3.5 h-3.5" />
                            ) : (
                              <TrendingDown className="w-3.5 h-3.5" />
                            )}
                            {isIncome ? '+' : '-'}{formatINR(tx.amount)}
                          </p>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            </section>
          </>
        )}
      </main>
    </div>
  );
}
