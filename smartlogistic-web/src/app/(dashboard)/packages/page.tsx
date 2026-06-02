'use client';

import { useEffect, useState, useCallback, useRef } from 'react';
import { api } from '@/lib/api';
import { Package as PackageType, ReceivePackageRequest } from '@/lib/types';

const PACKAGE_SSE_URL = process.env.NEXT_PUBLIC_PACKAGE_SSE_URL || 'http://localhost:8086/api/packages/stream';

export default function PackagesPage() {
  const [packages, setPackages] = useState<PackageType[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [filter, setFilter] = useState<string>('ALL');
  const packageEventSourceRef = useRef<EventSource | null>(null);

  // Form state — matches backend ReceivePackageRequest { sku, quantity, receptionSpotCode }
  const [form, setForm] = useState<ReceivePackageRequest>({
    sku: '',
    quantity: 1,
    receptionSpotCode: 'RP-R08-C04',
  });

  useEffect(() => {
    loadPackages();
    connectPackageSSE();

    return () => {
      if (packageEventSourceRef.current) {
        packageEventSourceRef.current.close();
      }
    };
  }, []);

  const connectPackageSSE = useCallback(() => {
    if (packageEventSourceRef.current) {
      packageEventSourceRef.current.close();
    }

    const es = new EventSource(PACKAGE_SSE_URL);
    packageEventSourceRef.current = es;

    es.addEventListener('package-received', (event) => {
      try {
        const pkg = JSON.parse(event.data);
        setPackages((prev) => {
          const exists = prev.find((p) => p.id === pkg.id);
          if (exists) return prev.map((p) => (p.id === pkg.id ? pkg : p));
          return [pkg, ...prev];
        });
      } catch { /* ignore */ }
    });

    es.addEventListener('package-updated', (event) => {
      try {
        const pkg = JSON.parse(event.data);
        setPackages((prev) => prev.map((p) => (p.id === pkg.id ? pkg : p)));
      } catch { /* ignore */ }
    });

    es.onerror = () => {
      es.close();
      setTimeout(connectPackageSSE, 5000);
    };
  }, []);

  const loadPackages = async () => {
    try {
      const data = await api.getPackages();
      setPackages(data || []);
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
      setSuccess(`Package #${newPkg.id} (${newPkg.sku}) received → ${newPkg.targetSpotCode}`);
      setForm({ sku: '', quantity: 1, receptionSpotCode: 'RP-R08-C04' });
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
      case 'IN_TRANSIT': return 'bg-blue-500/20 text-blue-300 px-2 py-0.5 rounded-full text-xs';
      case 'DELIVERED': return 'badge-success';
      case 'DISPATCHED': return 'bg-purple-500/20 text-purple-300 px-2 py-0.5 rounded-full text-xs';
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
          <p className="text-[#94a3b8] text-sm mt-1">Receive and track packages in the warehouse</p>
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
          <form onSubmit={handleSubmit} className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div>
              <label className="block text-sm font-medium text-[#94a3b8] mb-1.5">SKU</label>
              <select
                value={form.sku}
                onChange={(e) => setForm({ ...form, sku: e.target.value })}
                className="input-field"
                required
              >
                <option value="">Select a product...</option>
                <optgroup label="Shoes">
                  <option value="SHO-001">SHO-001 — Nike Air Max 90</option>
                  <option value="SHO-002">SHO-002 — Adidas Ultraboost</option>
                  <option value="SHO-003">SHO-003 — Puma RS-X</option>
                  <option value="SHO-004">SHO-004 — Converse Chuck Taylor</option>
                  <option value="SHO-005">SHO-005 — Vans Old Skool</option>
                </optgroup>
                <optgroup label="Shirts">
                  <option value="SHT-001">SHT-001 — Camiseta Polo Classic</option>
                  <option value="SHT-002">SHT-002 — Camisa Formal Manga Larga</option>
                  <option value="SHT-003">SHT-003 — Camiseta Algodón Básica</option>
                  <option value="SHT-004">SHT-004 — Polo Deportivo Dri-FIT</option>
                </optgroup>
                <optgroup label="Pants">
                  <option value="PNT-001">PNT-001 — Jeans Slim Fit</option>
                  <option value="PNT-002">PNT-002 — Jogger Deportivo</option>
                  <option value="PNT-003">PNT-003 — Pantalón Formal</option>
                </optgroup>
                <optgroup label="Jackets">
                  <option value="JCK-001">JCK-001 — Chaqueta de Cuero</option>
                  <option value="JCK-002">JCK-002 — Bomber Jacket</option>
                </optgroup>
                <optgroup label="Accessories">
                  <option value="ACC-001">ACC-001 — Gorra Baseball</option>
                  <option value="ACC-002">ACC-002 — Cinturón Cuero</option>
                </optgroup>
              </select>
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
              <label className="block text-sm font-medium text-[#94a3b8] mb-1.5">Reception Point</label>
              <input
                type="text"
                value={form.receptionSpotCode}
                onChange={(e) => setForm({ ...form, receptionSpotCode: e.target.value })}
                className="input-field"
                placeholder="e.g. RP-R08-C04"
              />
            </div>
            <div className="sm:col-span-3 flex gap-3">
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
        {['ALL', 'RECEIVED', 'IN_TRANSIT', 'DELIVERED', 'DISPATCHED'].map((status) => (
          <button
            key={status}
            onClick={() => setFilter(status)}
            className={`px-3 py-1.5 rounded-lg text-sm font-medium transition-colors ${
              filter === status
                ? 'bg-primary-600 text-white'
                : 'bg-[#334155] text-[#94a3b8] hover:bg-[#475569] hover:text-white'
            }`}
          >
            {status === 'ALL' ? 'All' : status.replace(/_/g, ' ')}
          </button>
        ))}
      </div>

      {/* Packages Table */}
      <div className="card overflow-hidden p-0">
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b border-[#334155]">
                <th className="text-left text-xs font-medium text-[#94a3b8] uppercase tracking-wider px-6 py-3">ID</th>
                <th className="text-left text-xs font-medium text-[#94a3b8] uppercase tracking-wider px-6 py-3">SKU</th>
                <th className="text-left text-xs font-medium text-[#94a3b8] uppercase tracking-wider px-6 py-3">Qty</th>
                <th className="text-left text-xs font-medium text-[#94a3b8] uppercase tracking-wider px-6 py-3">Status</th>
                <th className="text-left text-xs font-medium text-[#94a3b8] uppercase tracking-wider px-6 py-3">Reception</th>
                <th className="text-left text-xs font-medium text-[#94a3b8] uppercase tracking-wider px-6 py-3">Target</th>
                <th className="text-left text-xs font-medium text-[#94a3b8] uppercase tracking-wider px-6 py-3">Robot</th>
                <th className="text-left text-xs font-medium text-[#94a3b8] uppercase tracking-wider px-6 py-3">Created</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#334155]">
              {filteredPackages.length === 0 ? (
                <tr>
                  <td colSpan={8} className="px-6 py-12 text-center text-[#64748b]">
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
                      <span className="text-white text-sm font-medium font-mono">#{pkg.id}</span>
                    </td>
                    <td className="px-6 py-4">
                      <span className="text-white text-sm font-mono">{pkg.sku}</span>
                    </td>
                    <td className="px-6 py-4 text-[#94a3b8] text-sm">{pkg.quantity}</td>
                    <td className="px-6 py-4">
                      <span className={getStatusBadge(pkg.status)}>{pkg.status.replace(/_/g, ' ')}</span>
                    </td>
                    <td className="px-6 py-4 text-[#94a3b8] text-sm font-mono">{pkg.receptionSpotCode || '—'}</td>
                    <td className="px-6 py-4 text-[#94a3b8] text-sm font-mono">{pkg.targetSpotCode || '—'}</td>
                    <td className="px-6 py-4 text-[#94a3b8] text-sm font-mono">{pkg.robotId || '—'}</td>
                    <td className="px-6 py-4 text-[#64748b] text-sm">
                      {pkg.createdAt ? new Date(pkg.createdAt).toLocaleDateString() : '—'}
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