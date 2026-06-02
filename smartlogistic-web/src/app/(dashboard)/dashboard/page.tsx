'use client';

import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { DashboardStats, Robot, Order, Package as PackageType } from '@/lib/types';

export default function DashboardPage() {
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [recentOrders, setRecentOrders] = useState<Order[]>([]);
  const [recentPackages, setRecentPackages] = useState<PackageType[]>([]);
  const [robots, setRobots] = useState<Robot[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadData();
    const interval = setInterval(loadData, 30000);
    return () => clearInterval(interval);
  }, []);

  const loadData = async () => {
    try {
      const [dashboardStats, orders, packages, robotsData] = await Promise.all([
        api.getDashboardStats().catch(() => null),
        api.getOrders().catch(() => []),
        api.getPackages().catch(() => []),
        api.getRobots().catch(() => []),
      ]);
      setStats(dashboardStats);
      setRecentOrders(orders.slice(-5).reverse());
      setRecentPackages(packages.slice(-5).reverse());
      setRobots(robotsData);
    } catch {
      // silently handle
    } finally {
      setLoading(false);
    }
  };

  const getRobotStatusColor = (status: string) => {
    switch (status) {
      case 'IDLE': return 'badge-info';
      case 'MOVING_TO_PICKUP':
      case 'PICKING_UP':
      case 'MOVING_TO_DELIVERY':
      case 'DELIVERING': return 'badge-warning';
      case 'CHARGING': return 'badge-info';
      case 'ERROR': return 'badge-danger';
      default: return 'badge';
    }
  };

  const getOrderStatusBadge = (status: string) => {
    switch (status) {
      case 'PENDING': return 'badge-warning';
      case 'IN_PROGRESS': return 'badge-info';
      case 'COMPLETED': return 'badge-success';
      case 'CANCELLED': return 'badge-danger';
      default: return 'badge';
    }
  };

  const getPackageStatusBadge = (status: string) => {
    switch (status) {
      case 'RECEIVED': return 'badge-info';
      case 'STORED': return 'badge-success';
      case 'PICKED': return 'badge-warning';
      case 'DISPATCHED': return 'badge';
      default: return 'badge';
    }
  };

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
      <div>
        <h1 className="text-2xl font-bold text-white">Dashboard</h1>
        <p className="text-[#94a3b8] text-sm mt-1">Overview of your warehouse operations</p>
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="card">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-[#94a3b8] text-sm font-medium">Total Packages</p>
              <p className="text-3xl font-bold text-white mt-1">{stats?.totalPackages ?? 0}</p>
            </div>
            <div className="w-12 h-12 bg-blue-500/10 rounded-xl flex items-center justify-center">
              <svg className="w-6 h-6 text-blue-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" />
              </svg>
            </div>
          </div>
        </div>

        <div className="card">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-[#94a3b8] text-sm font-medium">Pending Orders</p>
              <p className="text-3xl font-bold text-white mt-1">{stats?.pendingOrders ?? 0}</p>
            </div>
            <div className="w-12 h-12 bg-yellow-500/10 rounded-xl flex items-center justify-center">
              <svg className="w-6 h-6 text-yellow-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
              </svg>
            </div>
          </div>
        </div>

        <div className="card">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-[#94a3b8] text-sm font-medium">Active Robots</p>
              <p className="text-3xl font-bold text-white mt-1">{stats?.activeRobots ?? 0}</p>
            </div>
            <div className="w-12 h-12 bg-green-500/10 rounded-xl flex items-center justify-center">
              <svg className="w-6 h-6 text-green-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
              </svg>
            </div>
          </div>
        </div>

        <div className="card">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-[#94a3b8] text-sm font-medium">Storage Used</p>
              <p className="text-3xl font-bold text-white mt-1">
                {stats ? `${stats.occupiedSpots}/${stats.totalSpots}` : '0/0'}
              </p>
            </div>
            <div className="w-12 h-12 bg-purple-500/10 rounded-xl flex items-center justify-center">
              <svg className="w-6 h-6 text-purple-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" />
              </svg>
            </div>
          </div>
          {stats && stats.totalSpots > 0 && (
            <div className="mt-3">
              <div className="w-full bg-[#0f172a] rounded-full h-2">
                <div
                  className="bg-purple-500 h-2 rounded-full transition-all duration-500"
                  style={{ width: `${(stats.occupiedSpots / stats.totalSpots) * 100}%` }}
                />
              </div>
              <p className="text-[#64748b] text-xs mt-1">
                {Math.round((stats.occupiedSpots / stats.totalSpots) * 100)}% occupied
              </p>
            </div>
          )}
        </div>
      </div>

      {/* Bottom section: Robots + Recent Activity */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Robot Fleet Status */}
        <div className="card">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-lg font-semibold text-white">Robot Fleet</h2>
            <span className="text-[#64748b] text-sm">{robots.length} robots</span>
          </div>
          {robots.length === 0 ? (
            <div className="text-center py-8 text-[#64748b]">
              <svg className="w-12 h-12 mx-auto mb-3 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
              </svg>
              <p className="text-sm">No robots connected</p>
            </div>
          ) : (
            <div className="space-y-3">
              {robots.map((robot) => (
                <div key={robot.robotId} className="flex items-center justify-between p-3 bg-[#0f172a] rounded-lg">
                  <div className="flex items-center gap-3">
                    <div className={`w-2.5 h-2.5 rounded-full ${
                      robot.operationalMode === 'IDLE' ? 'bg-blue-400' :
                      robot.operationalMode === 'CHARGING' ? 'bg-yellow-400' :
                      robot.operationalMode === 'ERROR' ? 'bg-red-400' : 'bg-green-400'
                    }`} />
                    <div>
                      <p className="text-white text-sm font-medium">{robot.name}</p>
                      <p className="text-[#64748b] text-xs font-mono">
                        {robot.currentLocation || 'Unknown'}
                      </p>
                    </div>
                  </div>
                  <div className="flex items-center gap-3">
                    <div className="flex items-center gap-1">
                      <svg className="w-3.5 h-3.5 text-green-400" fill="currentColor" viewBox="0 0 24 24">
                        <path d="M13 10V3L4 14h7v7l9-11h-7z" />
                      </svg>
                      <span className={`text-xs font-medium ${
                        robot.batteryLevel > 50 ? 'text-green-400' :
                        robot.batteryLevel > 20 ? 'text-yellow-400' : 'text-red-400'
                      }`}>
                        {robot.batteryLevel}%
                      </span>
                    </div>
                    <span className={getRobotStatusColor(robot.operationalMode)}>{robot.operationalMode}</span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Recent Activity */}
        <div className="card">
          <h2 className="text-lg font-semibold text-white mb-4">Recent Activity</h2>
          <div className="space-y-4">
            {/* Recent Orders */}
            {recentOrders.length > 0 && (
              <div>
                <h3 className="text-xs font-medium text-[#64748b] uppercase tracking-wider mb-2">Recent Orders</h3>
                <div className="space-y-2">
                  {recentOrders.map((order) => (
                    <div key={order.id} className="flex items-center justify-between p-2.5 bg-[#0f172a] rounded-lg">
                      <div>
                        <p className="text-white text-sm font-medium">{order.orderNumber || `Order #${order.id}`}</p>
                        <p className="text-[#64748b] text-xs">{(order.items || order.lines || []).length} item(s)</p>
                      </div>
                      <span className={getOrderStatusBadge(order.status)}>{order.status}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Recent Packages */}
            {recentPackages.length > 0 && (
              <div>
                <h3 className="text-xs font-medium text-[#64748b] uppercase tracking-wider mb-2">Recent Packages</h3>
                <div className="space-y-2">
                  {recentPackages.map((pkg) => (
                    <div key={pkg.id} className="flex items-center justify-between p-2.5 bg-[#0f172a] rounded-lg">
                      <div>
                        <p className="text-white text-sm font-medium">{pkg.trackingCode || `Pkg #${pkg.id}`}</p>
                        <p className="text-[#64748b] text-xs">{pkg.productName || pkg.sku} × {pkg.quantity}</p>
                      </div>
                      <span className={getPackageStatusBadge(pkg.status)}>{pkg.status}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {recentOrders.length === 0 && recentPackages.length === 0 && (
              <div className="text-center py-8 text-[#64748b]">
                <svg className="w-12 h-12 mx-auto mb-3 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
                </svg>
                <p className="text-sm">No recent activity</p>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}