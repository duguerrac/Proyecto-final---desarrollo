'use client';

import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { Package as PackageType, ReceivePackageRequest } from '@/lib/types';

export default function PackagesPage() {
  const [packages, setPackages] = useState<PackageType[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [filter, setFilter] = useState<string>('ALL');

  // Form state
  const [form, setForm] = useState<ReceivePackageRequest>({
    trackingCode: '',
    productName: '',
    quantity: 1,
    weight: 0,
  });

  useEffect(() => {
    loadPackages();
  }, []);

  const loadPackages = async () => {
    try {
      const data = await api.getPackages();
      setPackages(data);
    } catch {
      // silently handle
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    setSubmitting(true);

    try {
      const newPkg = await api.receivePackage(form);
      setPackages((prev) => [...prev, newPkg]);
      setSuccess(`Package ${newPkg.trackingCode} received successfully!`);
      setForm({ trackingCode: '', productName: '', quantity: 1, weight: 0 });
      setShowForm(false);
    } catch (err: any) {
      setError(err.message || 'Failed to receive package');
    } finally {
      setSubmitting(false);
    }
  };

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'RECEIVED': return 'badge-info';
      case 'STORED': return 'badge-success';
      case 'PICKED': return 'badge-warning';
      case 'DISPATCHED': return 'badge';
      default: return 'badge';
    }
  };

  const filteredPackages = filter === 'ALL'
    ? packages
    : packages.filter((p) => p.status === filter);

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="w-10 h-10 border-4 border-primary-500 border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Page header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-white">Packages</h1>
          <p className="text-[#94a3b8] text-sm mt-1">Manage incoming and stored packages</p>
        </div>
        <button
          onClick={() => setShowForm(!showForm)}
          className="btn-primary flex items-center gap-2 self-start"
        >
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
          </svg>
          Receive Package
        </button>
      </div>

      {/* Alerts */}
      {error && (
        <div className="p-3 bg-red-900/30 border border-red-800 rounded-lg text-red-400 text-sm">
          {error}
        </div>
      )}
      {success && (
        <div className="p-3 bg-green-900/30 border border-green-800 rounded-lg text-green-400 text-sm">
          {success}
        </div>
      )}

      {/* Receive Package Form */}
      {showForm && (
        <div className="card">
          <h2 className="text-lg font-semibold text-white mb-4">Receive New Package</h2>
          <form onSubmit={handleSubmit} className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-[#94a3b8] mb-1.5">Tracking Code</label>
              <input
                type="text"
                value={form.trackingCode}
                onChange={(e) => setForm({ ...form, trackingCode: e.target.value })}
                className="input-field"
                placeholder="e.g. PKG-2024-001"
                required
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-[#94a3b8] mb-1.5">Product Name</label>
              <input
                type="text"
                value={form.productName}
                onChange={(e) => setForm({ ...form, productName: e.target.value })}
                className="input-field"
                placeholder="e.g. Widget A"
                required
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-[#94a3b8] mb-1.5">Quantity</label>
              <input
                type="number"
                min="1"
                value={form.quantity}
                onChange={(e) => setForm({ ...form, quantity: parseInt(e.target.value) || 1 })}
                className="input-field"
                required
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-[#94a3b8] mb-1.5">Weight (kg)</label>
              <input
                type="number"
                min="0"
                step="0.1"
                value={form.weight}
                onChange={(e) => setForm({ ...form, weight: parseFloat(e.target.value) || 0 })}
                className="input-field"
                required
              />
            </div>
            <div className="sm:col-span-2 flex gap-3">
              <button type="submit" disabled={submitting} className="btn-primary">
                {submitting ? 'Receiving...' : 'Receive Package'}
              </button>
              <button type="button" onClick={() => setShowForm(false)} className="btn-secondary">
                Cancel
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Filters */}
      <div className="flex flex-wrap gap-2">
        {['ALL', 'RECEIVED', 'STORED', 'PICKED', 'DISPATCHED'].map((status) => (
          <button
            key={status}
            onClick={() => setFilter(status)}
            className={`px-3 py-1.5 rounded-lg text-sm font-medium transition-colors ${
              filter === status
                ? 'bg-primary-600 text-white'
                : 'bg-[#334155] text-[#94a3b8] hover:bg-[#475569] hover:text-white'
            }`}
          >
            {status === 'ALL' ? 'All' : status}
          </button>
        ))}
      </div>

      {/* Packages Table */}
      <div className="card overflow-hidden p-0">
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b border-[#334155]">
                <th className="text-left text-xs font-medium text-[#94a3b8] uppercase tracking-wider px-6 py-3">Tracking Code</th>
                <th className="text-left text-xs font-medium text-[#94a3b8] uppercase tracking-wider px-6 py-3">Product</th>
                <th className="text-left text-xs font-medium text-[#94a3b8] uppercase tracking-wider px-6 py-3">Qty</th>
                <th className="text-left text-xs font-medium text-[#94a3b8] uppercase tracking-wider px-6 py-3">Weight</th>
                <th className="text-left text-xs font-medium text-[#94a3b8] uppercase tracking-wider px-6 py-3">Status</th>
                <th className="text-left text-xs font-medium text-[#94a3b8] uppercase tracking-wider px-6 py-3">Location</th>
                <th className="text-left text-xs font-medium text-[#94a3b8] uppercase tracking-wider px-6 py-3">Created</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#334155]">
              {filteredPackages.length === 0 ? (
                <tr>
                  <td colSpan={7} className="px-6 py-12 text-center text-[#64748b]">
                    <svg className="w-12 h-12 mx-auto mb-3 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" />
                    </svg>
                    <p className="text-sm">No packages found</p>
                  </td>
                </tr>
              ) : (
                filteredPackages.map((pkg) => (
                  <tr key={pkg.id} className="hover:bg-[#0f172a]/50 transition-colors">
                    <td className="px-6 py-4">
                      <span className="text-white text-sm font-medium font-mono">{pkg.trackingCode}</span>
                    </td>
                    <td className="px-6 py-4 text-[#94a3b8] text-sm">{pkg.productName}</td>
                    <td className="px-6 py-4 text-[#94a3b8] text-sm">{pkg.quantity}</td>
                    <td className="px-6 py-4 text-[#94a3b8] text-sm">{pkg.weight} kg</td>
                    <td className="px-6 py-4">
                      <span className={getStatusBadge(pkg.status)}>{pkg.status}</span>
                    </td>
                    <td className="px-6 py-4 text-[#94a3b8] text-sm">
                      {pkg.spotLabel || '—'}
                    </td>
                    <td className="px-6 py-4 text-[#64748b] text-sm">
                      {new Date(pkg.createdAt).toLocaleDateString()}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}