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

export default function App() {
  const [summary, setSummary] = useState(null);
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

  const fetchSummary = async () => {
    try {
      const res = await fetch(`${API_BASE}/dashboard/summary`);
      if (!res.ok) throw new Error('Failed to load dashboard summary');
      const data = await res.json();
      setSummary(data);
      setToast((current) => current?.type === 'error' ? null : current);
    } catch (err) {
      setToast({ type: 'error', message: err.message });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchSummary();
  }, []);

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
      await fetchSummary();
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

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-950 text-slate-200 flex items-center justify-center">
        <RefreshCw className="w-6 h-6 animate-spin text-emerald-400 mr-3" />
        <span>Loading Life Mate...</span>
      </div>
    );
  }

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
            onClick={fetchSummary}
            className="text-xs px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 flex items-center gap-1.5 transition cursor-pointer"
          >
            <RefreshCw className="w-3.5 h-3.5" />
            Refresh
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

        {/* Top KPI Summary Cards */}
        {summary && (
          <>
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
                  {summary.categoryBudgets.map((cat) => {
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
                  {summary.recentTransactions.map((tx) => (
                    <div
                      key={tx.id}
                      className="py-3.5 flex items-center justify-between gap-4"
                    >
                      <div className="min-w-0">
                        <p className="font-medium text-sm text-slate-100 truncate">
                          {tx.merchant}
                        </p>
                        <div className="flex items-center gap-2 mt-0.5">
                          <span className="text-xs px-2 py-0.5 rounded-md bg-slate-800 text-slate-300">
                            {tx.categoryName}
                          </span>
                          <span className="text-xs text-slate-500 truncate">
                            {new Date(tx.createdAt).toLocaleDateString('en-IN', {
                              day: 'numeric',
                              month: 'short',
                              hour: '2-digit',
                              minute: '2-digit',
                            })}
                          </span>
                        </div>
                      </div>
                      <div className="text-right shrink-0">
                        <p className="text-sm font-semibold text-rose-400 flex items-center gap-1">
                          <TrendingDown className="w-3.5 h-3.5" />
                          -{formatINR(tx.amount)}
                        </p>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </section>
          </>
        )}
      </main>
    </div>
  );
}
