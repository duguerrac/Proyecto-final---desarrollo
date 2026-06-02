'use client';

import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { Order, CreateOrderRequest, Package as PackageType } from '@/lib/types';

export default function OrdersPage() {
  const [orders, setOrders] = useState<Order[]>([]);
  const [packages, setPackages] = useState<PackageType[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [filter, setFilter] = useState<string>('ALL');
  const [expandedOrder, setExpandedOrder] = useState<string | null>(null);

  // Form state for creating order
  const [selectedItems, setSelectedItems] = useState<{ productId: string; quantity: number }[]>([]);

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    try {
      const [ordersData, packagesData] = await Promise.all([
        api.getOrders().catch(() => []),
        api.getPackages().catch(() => []),
      ]);
      setOrders(ordersData);
      setPackages(packagesData.filter((p) => p.status === 'STORED'));
    } catch {
      // silently handle
    } finally {
      setLoading(false);
    }
  };

  const handleCreateOrder = async (e: React.FormEvent) => {
    e.preventDefault();
    if (selectedItems.length === 0) {
      setError('Please add at least one item to the order');
      return;
    }
    setError('');
    setSuccess('');
    setSubmitting(true);

    try {
      const request: CreateOrderRequest = {
        lines: selectedItems.map((i) => ({ sku: i.productId, quantity: i.quantity })),
      };
      const newOrder = await api.createOrder(request);
      setOrders((prev) => [...prev, newOrder]);
      setSuccess(`Order ${newOrder.orderNumber || `#${newOrder.id}`} created successfully!`);
      setSelectedItems([]);
      setShowForm(false);
    } catch (err: any) {
      setError(err.message || 'Failed to create order');
    } finally {
      setSubmitting(false);
    }
  };

  const addOrderItem = (productId: string) => {
    if (!productId) return;
    const existing = selectedItems.find((i) => i.productId === productId);
    if (existing) {
      setSelectedItems(selectedItems.map((i) =>
        i.productId === productId ? { ...i, quantity: i.quantity + 1 } : i
      ));
    } else {
      setSelectedItems([...selectedItems, { productId, quantity: 1 }]);
    }
  };

  const removeOrderItem = (productId: string) => {
    setSelectedItems(selectedItems.filter((i) => i.productId !== productId));
  };

  const updateItemQuantity = (productId: string, quantity: number) => {
    if (quantity <= 0) {
      removeOrderItem(productId);
    } else {
      setSelectedItems(selectedItems.map((i) =>
        i.productId === productId ? { ...i, quantity } : i
      ));
    }
  };

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'PENDING': return 'badge-warning';
      case 'IN_PROGRESS': return 'badge-info';
      case 'COMPLETED': return 'badge-success';
      case 'CANCELLED': return 'badge-danger';
      default: return 'badge';
    }
  };

  const filteredOrders = filter === 'ALL'
    ? orders
    : orders.filter((o) => o.status === filter);

  // Group stored packages by productId for the dropdown
  const availableProducts = packages.reduce((acc, pkg) => {
    const existing = acc.find((p) => p.productId === pkg.id);
    if (existing) {
      existing.totalQuantity += pkg.quantity;
    } else {
      acc.push({
        productId: pkg.id,
        productName: pkg.productName || pkg.sku || 'Unknown',
        totalQuantity: pkg.quantity,
        spotLabel: pkg.spotLabel || 'N/A',
      });
    }
    return acc;
  }, [] as { productId: string; productName: string; totalQuantity: number; spotLabel: string }[]);

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
          <h1 className="text-2xl font-bold text-white">Orders</h1>
          <p className="text-[#94a3b8] text-sm mt-1">Create and manage dispatch orders</p>
        </div>
        <button
          onClick={() => setShowForm(!showForm)}
          className="btn-primary flex items-center gap-2 self-start"
        >
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
          </svg>
          New Order
        </button>
      </div>

      {/* Alerts */}
      {error && (
        <div className="p-3 bg-red-900/30 border border-red-800 rounded-lg text-red-400 text-sm">{error}</div>
      )}
      {success && (
        <div className="p-3 bg-green-900/30 border border-green-800 rounded-lg text-green-400 text-sm">{success}</div>
      )}

      {/* Create Order Form */}
      {showForm && (
        <div className="card">
          <h2 className="text-lg font-semibold text-white mb-4">Create New Order</h2>
          <form onSubmit={handleCreateOrder} className="space-y-4">
            {/* Product selector */}
            <div>
              <label className="block text-sm font-medium text-[#94a3b8] mb-1.5">Add Product</label>
              <div className="flex gap-2">
                <select
                  className="input-field flex-1"
                  defaultValue=""
                  onChange={(e) => {
                    if (e.target.value) {
                      addOrderItem(e.target.value);
                      e.target.value = '';
                    }
                  }}
                >
                  <option value="" disabled>Select a product...</option>
                  {availableProducts.map((prod) => (
                    <option key={prod.productId} value={prod.productId}>
                      {prod.productName} (Available: {prod.totalQuantity}) — {prod.spotLabel}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            {/* Selected items */}
            {selectedItems.length > 0 && (
              <div className="space-y-2">
                <h3 className="text-sm font-medium text-[#94a3b8]">Order Items</h3>
                {selectedItems.map((item) => {
                  const product = availableProducts.find((p) => p.productId === item.productId);
                  return (
                    <div key={item.productId} className="flex items-center gap-3 p-3 bg-[#0f172a] rounded-lg">
                      <div className="flex-1">
                        <p className="text-white text-sm font-medium">{product?.productName || item.productId}</p>
                        <p className="text-[#64748b] text-xs">Spot: {product?.spotLabel || 'N/A'}</p>
                      </div>
                      <div className="flex items-center gap-2">
                        <button
                          type="button"
                          onClick={() => updateItemQuantity(item.productId, item.quantity - 1)}
                          className="w-7 h-7 rounded bg-[#334155] text-white flex items-center justify-center hover:bg-[#475569]"
                        >
                          −
                        </button>
                        <span className="text-white text-sm w-8 text-center">{item.quantity}</span>
                        <button
                          type="button"
                          onClick={() => updateItemQuantity(item.productId, item.quantity + 1)}
                          className="w-7 h-7 rounded bg-[#334155] text-white flex items-center justify-center hover:bg-[#475569]"
                        >
                          +
                        </button>
                      </div>
                      <button
                        type="button"
                        onClick={() => removeOrderItem(item.productId)}
                        className="text-red-400 hover:text-red-300 p-1"
                      >
                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                        </svg>
                      </button>
                    </div>
                  );
                })}
              </div>
            )}

            <div className="flex gap-3">
              <button type="submit" disabled={submitting || selectedItems.length === 0} className="btn-primary">
                {submitting ? 'Creating...' : 'Create Order'}
              </button>
              <button type="button" onClick={() => { setShowForm(false); setSelectedItems([]); }} className="btn-secondary">
                Cancel
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Filters */}
      <div className="flex flex-wrap gap-2">
        {['ALL', 'PENDING', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'].map((status) => (
          <button
            key={status}
            onClick={() => setFilter(status)}
            className={`px-3 py-1.5 rounded-lg text-sm font-medium transition-colors ${
              filter === status
                ? 'bg-primary-600 text-white'
                : 'bg-[#334155] text-[#94a3b8] hover:bg-[#475569] hover:text-white'
            }`}
          >
            {status === 'ALL' ? 'All' : status.replace('_', ' ')}
          </button>
        ))}
      </div>

      {/* Orders List */}
      <div className="space-y-3">
        {filteredOrders.length === 0 ? (
          <div className="card text-center py-12 text-[#64748b]">
            <svg className="w-12 h-12 mx-auto mb-3 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
            </svg>
            <p className="text-sm">No orders found</p>
          </div>
        ) : (
          filteredOrders.map((order) => (
            <div key={order.id} className="card">
              <div
                className="flex items-center justify-between cursor-pointer"
                onClick={() => setExpandedOrder(expandedOrder === order.id ? null : order.id)}
              >
                <div className="flex items-center gap-4">
                  <div className="w-10 h-10 bg-[#0f172a] rounded-lg flex items-center justify-center">
                    <svg className="w-5 h-5 text-primary-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
                    </svg>
                  </div>
                  <div>
                    <p className="text-white font-medium">{order.orderNumber || `Order #${order.id}`}</p>
                    <p className="text-[#64748b] text-xs">
                      {(order.items || order.lines || []).length} item(s) · {new Date(order.createdAt).toLocaleDateString()}
                    </p>
                  </div>
                </div>
                <div className="flex items-center gap-3">
                  <span className={getStatusBadge(order.status)}>{order.status.replace('_', ' ')}</span>
                  <svg
                    className={`w-4 h-4 text-[#64748b] transition-transform ${expandedOrder === order.id ? 'rotate-180' : ''}`}
                    fill="none" stroke="currentColor" viewBox="0 0 24 24"
                  >
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                  </svg>
                </div>
              </div>

              {expandedOrder === order.id && (
                <div className="mt-4 pt-4 border-t border-[#334155]">
                  <h4 className="text-xs font-medium text-[#64748b] uppercase tracking-wider mb-2">Order Items</h4>
                  <div className="space-y-2">
                    {(order.items || order.lines || []).map((item, idx) => (
                      <div key={item.id || idx} className="flex items-center justify-between p-2.5 bg-[#0f172a] rounded-lg">
                        <div>
                          <p className="text-white text-sm">{item.productName || item.sku || `Item #${idx + 1}`}</p>
                          <p className="text-[#64748b] text-xs">SKU: {item.sku || item.productId || 'N/A'}</p>
                        </div>
                        <div className="text-right">
                          <p className="text-white text-sm">× {item.quantity}</p>
                          {item.spotLabel && (
                            <p className="text-[#64748b] text-xs">Spot: {item.spotLabel}</p>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                  {order.completedAt && (
                    <p className="text-[#64748b] text-xs mt-3">
                      Completed: {new Date(order.completedAt).toLocaleString()}
                    </p>
                  )}
                </div>
              )}
            </div>
          ))
        )}
      </div>
    </div>
  );
}