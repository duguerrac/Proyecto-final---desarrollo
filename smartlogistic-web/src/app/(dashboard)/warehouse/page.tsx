'use client';

import { useEffect, useState, useCallback, useRef } from 'react';
import { api } from '@/lib/api';
import { WarehouseLayout, LayoutCell, CellType, Robot, Package } from '@/lib/types';

// SSE connects directly to nginx gateway (same as API)
const SSE_URL = process.env.NEXT_PUBLIC_SSE_URL || 'http://localhost:8086/api/robots/telemetry/stream';
const PACKAGE_SSE_URL = process.env.NEXT_PUBLIC_PACKAGE_SSE_URL || 'http://localhost:8086/api/packages/stream';

const CELL_COLORS: Record<CellType, string> = {
  EMPTY: 'bg-[#334155]/30 border-[#475569]/30',
  SHELF: 'bg-blue-600/40 border-blue-500/50',
  CHARGING: 'bg-yellow-600/40 border-yellow-500/50',
  DELIVERY_DOCK: 'bg-purple-600/40 border-purple-500/50',
  RECEIVING_DOCK: 'bg-green-600/40 border-green-500/50',
  ROBOT_SPAWN: 'bg-cyan-600/40 border-cyan-500/50',
  OBSTACLE: 'bg-[#0f172a] border-[#1e293b]',
};

const CELL_ICONS: Record<CellType, string> = {
  EMPTY: '',
  SHELF: '📦',
  CHARGING: '🔋',
  DELIVERY_DOCK: '🚚',
  RECEIVING_DOCK: '📍',
  ROBOT_SPAWN: '🤖',
  OBSTACLE: '🧱',
};

const CELL_LABELS: Record<CellType, string> = {
  EMPTY: 'Path',
  SHELF: 'Storage',
  CHARGING: 'Charging',
  DELIVERY_DOCK: 'Delivery',
  RECEIVING_DOCK: 'Receiving',
  ROBOT_SPAWN: 'Spawn',
  OBSTACLE: 'Wall',
};

const MODE_COLORS: Record<string, string> = {
  IDLE: 'bg-blue-500',
  MOVING: 'bg-yellow-500',
  MOVING_TO_PICKUP: 'bg-yellow-500',
  PICKING_UP: 'bg-orange-500',
  MOVING_TO_DELIVERY: 'bg-amber-500',
  DELIVERING: 'bg-green-500',
  LOADING: 'bg-orange-400',
  UNLOADING: 'bg-amber-400',
  DISPATCHED: 'bg-purple-500',
  CHARGING: 'bg-yellow-400',
  ERROR: 'bg-red-500',
};

const PACKAGE_STATUS_COLORS: Record<string, string> = {
  RECEIVED: 'bg-orange-500',
  IN_TRANSIT: 'bg-blue-500',
  DELIVERED: 'bg-green-500',
  DISPATCHED: 'bg-purple-500',
};

const PACKAGE_STATUS_LABELS: Record<string, string> = {
  RECEIVED: 'At Reception',
  IN_TRANSIT: 'In Transit',
  DELIVERED: 'Delivered',
  DISPATCHED: 'Dispatched',
};

/** Parse spot code like "RP-R00-C05" → { row: 0, col: 5 } */
function parseLocation(loc: string): { row: number; col: number } | null {
  const match = loc?.match(/R(\d+)-C(\d+)/);
  if (!match) return null;
  return { row: parseInt(match[1], 10), col: parseInt(match[2], 10) };
}

/** Parse spot code to grid position (handles "RP-R00-C05" or "RP_0_5" formats) */
function parseSpotToGrid(spotCode: string): { row: number; col: number } | null {
  // Try "RP-R00-C05" format
  const match1 = spotCode?.match(/R(\d+)-C(\d+)/);
  if (match1) return { row: parseInt(match1[1], 10), col: parseInt(match1[2], 10) };
  // Try "RP_0_5" format
  const match2 = spotCode?.match(/RP_(\d+)_(\d+)/);
  if (match2) return { row: parseInt(match2[1], 10), col: parseInt(match2[2], 10) };
  return null;
}

export default function WarehousePage() {
  const [layout, setLayout] = useState<WarehouseLayout | null>(null);
  const [robots, setRobots] = useState<Map<string, Robot>>(new Map());
  const [packages, setPackages] = useState<Package[]>([]);
  const [spotGridMap, setSpotGridMap] = useState<Map<string, { row: number; col: number }>>(new Map());
  const [loading, setLoading] = useState(true);
  const [selectedCell, setSelectedCell] = useState<LayoutCell | null>(null);
  const [selectedRobot, setSelectedRobot] = useState<Robot | null>(null);
  const [shelfItems, setShelfItems] = useState<{ itemId: number; name: string; sku: string; quantityAvailable: number }[]>([]);
  const [loadingItems, setLoadingItems] = useState(false);
  const [sseStatus, setSseStatus] = useState<'connecting' | 'connected' | 'disconnected'>('disconnected');
  const eventSourceRef = useRef<EventSource | null>(null);
  const packageEventSourceRef = useRef<EventSource | null>(null);
  const packagePollRef = useRef<NodeJS.Timeout | null>(null);

  // Fetch shelf items when a SHELF cell is selected
  useEffect(() => {
    if (selectedCell && selectedCell.cellType === 'SHELF') {
      setLoadingItems(true);
      setShelfItems([]);
      api.getSpotItemsByCell(selectedCell.rowIndex, selectedCell.colIndex)
        .then((items) => setShelfItems(items))
        .catch(() => setShelfItems([]))
        .finally(() => setLoadingItems(false));
    } else {
      setShelfItems([]);
    }
  }, [selectedCell]);

  useEffect(() => {
    loadLayout();
    loadRobots();
    loadPackages();
    connectSSE();

    // Connect to package SSE stream for real-time updates
    connectPackageSSE();

    // Fallback: poll packages every 10 seconds (slower, SSE is primary)
    packagePollRef.current = setInterval(loadPackages, 10000);

    return () => {
      if (eventSourceRef.current) {
        eventSourceRef.current.close();
      }
      if (packageEventSourceRef.current) {
        packageEventSourceRef.current.close();
      }
      if (packagePollRef.current) {
        clearInterval(packagePollRef.current);
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
          return [...prev, pkg];
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
      // Retry connection after 5 seconds
      setTimeout(connectPackageSSE, 5000);
    };
  }, []);

  const loadLayout = async () => {
    try {
      const [layoutData, spotsData] = await Promise.all([
        api.getWarehouseLayout(),
        api.getSpots().catch(() => [])
      ]);
      if (layoutData) {
        setLayout(layoutData);
        // Build spot code → grid position mapping using spot coordinates
        if (Array.isArray(spotsData) && layoutData.cellSize > 0) {
          const map = new Map<string, { row: number; col: number }>();
          spotsData.forEach((spot: { code: string; x: number; y: number }) => {
            const col = Math.round(spot.x / layoutData.cellSize);
            const row = Math.round(spot.y / layoutData.cellSize);
            map.set(spot.code, { row, col });
          });
          setSpotGridMap(map);
        }
      }
    } catch {
      // silently handle
    } finally {
      setLoading(false);
    }
  };

  const loadRobots = async () => {
    try {
      const robotsData = await api.getRobots();
      const map = new Map<string, Robot>();
      robotsData.forEach((r: Robot) => map.set(r.robotId, r));
      setRobots(map);
    } catch {
      // silently handle
    }
  };

  const loadPackages = async () => {
    try {
      const packagesData = await api.getPackages();
      setPackages(packagesData);
    } catch {
      // silently handle
    }
  };

  const connectSSE = useCallback(() => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
    }

    setSseStatus('connecting');
    const es = new EventSource(SSE_URL);
    eventSourceRef.current = es;

    es.onopen = () => setSseStatus('connected');

    // Backend sends named events: event: telemetry
    es.addEventListener('telemetry', (event) => {
      try {
        const data = JSON.parse(event.data);
        const robotData = data.robot || data;
        if (robotData.id || robotData.robotId) {
          const robot: Robot = {
            robotId: robotData.id || robotData.robotId,
            name: robotData.name || 'Unknown',
            batteryLevel: robotData.batteryLevel ?? 0,
            available: robotData.available ?? true,
            currentLocation: robotData.currentLocation || '',
            operationalMode: robotData.operationalMode || 'IDLE',
          };
          setRobots((prev) => {
            const next = new Map(prev);
            next.set(robot.robotId, robot);
            return next;
          });
        }
      } catch {
        // ignore parse errors
      }
    });

    // Also handle default message events
    es.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);
        if (data.status === 'connected') return;
        const robotData = data.robot || data;
        if (robotData.id || robotData.robotId) {
          const robot: Robot = {
            robotId: robotData.id || robotData.robotId,
            name: robotData.name || 'Unknown',
            batteryLevel: robotData.batteryLevel ?? 0,
            available: robotData.available ?? true,
            currentLocation: robotData.currentLocation || '',
            operationalMode: robotData.operationalMode || 'IDLE',
          };
          setRobots((prev) => {
            const next = new Map(prev);
            next.set(robot.robotId, robot);
            return next;
          });
        }
      } catch {
        // ignore
      }
    };

    es.onerror = () => {
      setSseStatus('disconnected');
      es.close();
      setTimeout(connectSSE, 5000);
    };
  }, []);

  // Build 2D grid from layout cells
  const buildGrid = (): (LayoutCell | null)[][] => {
    if (!layout) return [];
    const grid: (LayoutCell | null)[][] = [];
    for (let r = 0; r < layout.rows; r++) {
      grid[r] = [];
      for (let c = 0; c < layout.cols; c++) {
        const cell = (layout.cells || []).find((s) => s.rowIndex === r && s.colIndex === c);
        grid[r][c] = cell || null;
      }
    }
    return grid;
  };

  const getRobotsOnCell = (row: number, col: number): Robot[] => {
    const result: Robot[] = [];
    robots.forEach((robot) => {
      const pos = parseLocation(robot.currentLocation);
      if (pos && pos.row === row && pos.col === col) {
        result.push(robot);
      }
    });
    return result;
  };

  /** Resolve any spot code to grid position */
  const resolveSpotPosition = (spotCode: string): { row: number; col: number } | null => {
    // Try parsing RP-R00-C00 format first
    const parsed = parseSpotToGrid(spotCode);
    if (parsed) return parsed;
    // Try the spotGridMap (for SP- codes from /api/spots)
    return spotGridMap.get(spotCode) || null;
  };

  /** Get active (non-delivered) packages at a specific cell based on receptionSpotCode or targetSpotCode */
  const getPackagesOnCell = (row: number, col: number): Package[] => {
    return packages.filter((pkg) => {
      if (pkg.status === 'DELIVERED') return false;
      // If RECEIVED, show at reception spot
      if (pkg.status === 'RECEIVED') {
        const pos = resolveSpotPosition(pkg.receptionSpotCode);
        return pos !== null && pos.row === row && pos.col === col;
      }
      // If IN_TRANSIT or DISPATCHED, show at target spot
      if (pkg.status === 'IN_TRANSIT' || pkg.status === 'DISPATCHED') {
        const pos = resolveSpotPosition(pkg.targetSpotCode);
        return pos !== null && pos.row === row && pos.col === col;
      }
      return false;
    });
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="w-10 h-10 border-4 border-primary-500 border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }

  const grid = buildGrid();

  return (
    <div className="space-y-6">
      {/* Page header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-white">Warehouse Map</h1>
          <p className="text-[#94a3b8] text-sm mt-1">Real-time warehouse top view with robot positions & packages</p>
        </div>
        <div className="flex items-center gap-4">
          <div className="flex items-center gap-2 text-sm">
            <span className={`w-2.5 h-2.5 rounded-full ${
              sseStatus === 'connected' ? 'bg-green-500 animate-pulse' :
              sseStatus === 'connecting' ? 'bg-yellow-500 animate-pulse' : 'bg-red-500'
            }`} />
            <span className="text-[#94a3b8]">
              {sseStatus === 'connected' ? 'Live' : sseStatus === 'connecting' ? 'Connecting...' : 'Disconnected'}
            </span>
          </div>
          <button onClick={() => { loadRobots(); loadPackages(); connectSSE(); }} className="btn-secondary text-sm">
            Refresh
          </button>
        </div>
      </div>

      {/* Legend */}
      <div className="card">
        <div className="flex flex-wrap gap-4 text-sm">
          {(Object.entries(CELL_COLORS) as [CellType, string][]).map(([type, colorClass]) => (
            <div key={type} className="flex items-center gap-2">
              <div className={`w-5 h-5 rounded border ${colorClass} flex items-center justify-center text-[10px]`}>
                {CELL_ICONS[type]}
              </div>
              <span className="text-[#94a3b8]">{CELL_LABELS[type]}</span>
            </div>
          ))}
          <div className="flex items-center gap-2 ml-4">
            <div className="w-5 h-5 rounded-full bg-primary-500 flex items-center justify-center text-white text-[8px] font-bold">R</div>
            <span className="text-[#94a3b8]">Robot</span>
          </div>
          <div className="flex items-center gap-2 ml-2">
            <div className="w-5 h-5 rounded-full bg-orange-500 flex items-center justify-center text-white text-[8px] font-bold">P</div>
            <span className="text-[#94a3b8]">Package</span>
          </div>
        </div>
      </div>

      {/* Main content: Grid + Detail panel */}
      <div className="grid grid-cols-1 xl:grid-cols-4 gap-6">
        {/* Warehouse Grid */}
        <div className="xl:col-span-3 card overflow-auto">
          {!layout ? (
            <div className="text-center py-16 text-[#64748b]">
              <svg className="w-16 h-16 mx-auto mb-4 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 20l-5.447-2.724A1 1 0 013 16.382V5.618a1 1 0 011.447-.894L9 7m0 13l6-3m-6 3V7m6 10l4.553 2.276A1 1 0 0021 18.382V7.618a1 1 0 00-.553-.894L15 4m0 13V4m0 0L9 7" />
              </svg>
              <p>No warehouse layout found</p>
              <p className="text-xs mt-1">Please seed the database with a layout and activate it</p>
            </div>
          ) : (
            <div className="inline-block min-w-full">
              {/* Column headers */}
              <div className="flex">
                <div className="w-10 h-8 flex-shrink-0" />
                {Array.from({ length: layout.cols }, (_, c) => (
                  <div key={c} className="w-14 h-8 flex items-center justify-center text-[#64748b] text-xs font-mono flex-shrink-0">
                    {c}
                  </div>
                ))}
              </div>
              {/* Grid rows */}
              {grid.map((row, r) => (
                <div key={r} className="flex">
                  <div className="w-10 h-14 flex items-center justify-center text-[#64748b] text-xs font-mono flex-shrink-0">
                    {r}
                  </div>
                  {row.map((cell, c) => {
                    const cellType = cell?.cellType || 'EMPTY';
                    const robotsHere = getRobotsOnCell(r, c);
                    const packagesHere = getPackagesOnCell(r, c);
                    const isSelected = selectedCell?.rowIndex === r && selectedCell?.colIndex === c;
                    return (
                      <div
                        key={`${r}-${c}`}
                        onClick={() => cell && setSelectedCell(cell)}
                        className={`w-14 h-14 border flex flex-col items-center justify-center relative cursor-pointer transition-all duration-200 ${
                          cell
                            ? `${CELL_COLORS[cellType as CellType] || CELL_COLORS.EMPTY} ${isSelected ? 'ring-2 ring-primary-400 z-10' : 'hover:brightness-125'}`
                            : 'bg-transparent border-transparent'
                        }`}
                        title={cell ? `${CELL_LABELS[cellType as CellType]} (${r},${c})${packagesHere.length > 0 ? ` — ${packagesHere.length} package(s)` : ''}` : ''}
                      >
                        {cell && (
                          <span className="text-[10px] leading-none">{CELL_ICONS[cellType as CellType]}</span>
                        )}
                        {/* Package markers (bottom-left) */}
                        {packagesHere.length > 0 && (
                          <div className="absolute -bottom-1 -left-1 flex gap-0.5 z-10">
                            {packagesHere.slice(0, 3).map((pkg, i) => (
                              <div
                                key={pkg.id}
                                className={`w-4 h-4 rounded-sm ${PACKAGE_STATUS_COLORS[pkg.status] || 'bg-orange-500'} flex items-center justify-center text-white text-[7px] font-bold border border-[#1e293b] shadow-lg`}
                                title={`Pkg #${pkg.id} — ${pkg.sku} (${pkg.status})`}
                              >
                                {pkg.quantity}
                              </div>
                            ))}
                            {packagesHere.length > 3 && (
                              <div className="w-4 h-4 rounded-sm bg-gray-500 flex items-center justify-center text-white text-[7px] font-bold border border-[#1e293b]">
                                +{packagesHere.length - 3}
                              </div>
                            )}
                          </div>
                        )}
                        {/* Robot markers */}
                        {robotsHere.length > 0 && (
                          <div className="absolute -top-1 -right-1 flex gap-0.5">
                            {robotsHere.map((robot) => (
                              <button
                                key={robot.robotId}
                                onClick={(e) => { e.stopPropagation(); setSelectedRobot(robot); setSelectedCell(null); }}
                                className={`w-5 h-5 rounded-full ${MODE_COLORS[robot.operationalMode] || 'bg-gray-500'} flex items-center justify-center text-white text-[7px] font-bold border-2 border-[#1e293b] shadow-lg hover:scale-125 transition-transform z-20`}
                                title={`${robot.name} - ${robot.operationalMode}`}
                              >
                                {robot.name.charAt(0)}
                              </button>
                            ))}
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Detail Panel */}
        <div className="xl:col-span-1 space-y-4">
          {/* Robot Detail */}
          {selectedRobot && (
            <div className="card">
              <div className="flex items-center justify-between mb-3">
                <h3 className="text-white font-semibold">Robot Details</h3>
                <button onClick={() => setSelectedRobot(null)} className="text-[#64748b] hover:text-white">
                  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                  </svg>
                </button>
              </div>
              <div className="space-y-3">
                <div className="flex items-center gap-3">
                  <div className={`w-10 h-10 rounded-full ${MODE_COLORS[selectedRobot.operationalMode] || 'bg-gray-500'} flex items-center justify-center text-white font-bold`}>
                    {selectedRobot.name.charAt(0)}
                  </div>
                  <div>
                    <p className="text-white font-medium">{selectedRobot.name}</p>
                    <p className="text-[#64748b] text-xs">{selectedRobot.robotId}</p>
                  </div>
                </div>
                <div className="space-y-2 text-sm">
                  <div className="flex justify-between">
                    <span className="text-[#94a3b8]">Status</span>
                    <span className="text-white font-medium">{selectedRobot.operationalMode.replace(/_/g, ' ')}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-[#94a3b8]">Available</span>
                    <span className={selectedRobot.available ? 'text-green-400' : 'text-red-400'}>
                      {selectedRobot.available ? 'Yes' : 'No'}
                    </span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-[#94a3b8]">Location</span>
                    <span className="text-white font-mono text-xs">{selectedRobot.currentLocation}</span>
                  </div>
                  <div className="flex justify-between items-center">
                    <span className="text-[#94a3b8]">Battery</span>
                    <div className="flex items-center gap-2">
                      <div className="w-16 bg-[#0f172a] rounded-full h-2">
                        <div
                          className={`h-2 rounded-full ${
                            selectedRobot.batteryLevel > 50 ? 'bg-green-500' :
                            selectedRobot.batteryLevel > 20 ? 'bg-yellow-500' : 'bg-red-500'
                          }`}
                          style={{ width: `${selectedRobot.batteryLevel}%` }}
                        />
                      </div>
                      <span className={`text-xs font-medium ${
                        selectedRobot.batteryLevel > 50 ? 'text-green-400' :
                        selectedRobot.batteryLevel > 20 ? 'text-yellow-400' : 'text-red-400'
                      }`}>
                        {selectedRobot.batteryLevel}%
                      </span>
                    </div>
                  </div>
                </div>

                {/* Cargo Status */}
                <div className="mt-3 pt-3 border-t border-[#1e293b]">
                  <h4 className="text-[#94a3b8] text-xs font-semibold uppercase tracking-wider mb-2">
                    Cargo Status
                  </h4>
                  {['IDLE', 'CHARGING'].includes(selectedRobot.operationalMode) ? (
                    <div className="bg-[#0f172a] rounded-lg p-2.5 flex items-center gap-2">
                      <span className="text-[#64748b] text-lg">📭</span>
                      <span className="text-[#64748b] text-sm">Empty — no cargo</span>
                    </div>
                  ) : ['MOVING_TO_PICKUP', 'PICKING_UP'].includes(selectedRobot.operationalMode) ? (
                    <div className="bg-yellow-500/10 rounded-lg p-2.5 flex items-center gap-2">
                      <span className="text-yellow-400 text-lg">⏳</span>
                      <div>
                        <p className="text-yellow-300 text-sm font-medium">Heading to pick up item</p>
                        <p className="text-[#64748b] text-xs">No cargo loaded yet</p>
                      </div>
                    </div>
                  ) : ['MOVING_TO_DELIVERY', 'DELIVERING', 'LOADING', 'UNLOADING'].includes(selectedRobot.operationalMode) ? (
                    <div className="bg-green-500/10 rounded-lg p-2.5 flex items-center gap-2">
                      <span className="text-green-400 text-lg">📦</span>
                      <div>
                        <p className="text-green-300 text-sm font-medium">Carrying cargo</p>
                        <p className="text-[#64748b] text-xs">En route to delivery point</p>
                      </div>
                    </div>
                  ) : ['DISPATCHED'].includes(selectedRobot.operationalMode) ? (
                    <div className="bg-purple-500/10 rounded-lg p-2.5 flex items-center gap-2">
                      <span className="text-purple-400 text-lg">🚀</span>
                      <div>
                        <p className="text-purple-300 text-sm font-medium">Dispatched</p>
                        <p className="text-[#64748b] text-xs">Assigned to a task</p>
                      </div>
                    </div>
                  ) : (
                    <div className="bg-[#0f172a] rounded-lg p-2.5 flex items-center gap-2">
                      <span className="text-[#64748b] text-lg">❓</span>
                      <span className="text-[#64748b] text-sm">{selectedRobot.operationalMode}</span>
                    </div>
                  )}
                </div>

                {/* Location on grid */}
                {parseLocation(selectedRobot.currentLocation) && (
                  <div className="mt-3 pt-3 border-t border-[#1e293b]">
                    <button
                      onClick={() => {
                        const pos = parseLocation(selectedRobot.currentLocation)!;
                        const cell = layout?.cells?.find(c => c.rowIndex === pos.row && c.colIndex === pos.col);
                        if (cell) { setSelectedCell(cell); setSelectedRobot(null); }
                      }}
                      className="w-full text-center text-xs text-primary-400 hover:text-primary-300 transition-colors py-1"
                    >
                      📍 View cell ({parseLocation(selectedRobot.currentLocation)!.row}, {parseLocation(selectedRobot.currentLocation)!.col})
                    </button>
                  </div>
                )}
              </div>
            </div>
          )}

          {/* Cell Detail */}
          {selectedCell && (
            <div className="card">
              <div className="flex items-center justify-between mb-3">
                <h3 className="text-white font-semibold">Cell Details</h3>
                <button onClick={() => setSelectedCell(null)} className="text-[#64748b] hover:text-white">
                  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                  </svg>
                </button>
              </div>
              <div className="space-y-3">
                <div className="flex items-center gap-3">
                  <div className={`w-10 h-10 rounded-lg ${CELL_COLORS[selectedCell.cellType]} border flex items-center justify-center text-lg`}>
                    {CELL_ICONS[selectedCell.cellType]}
                  </div>
                  <div>
                    <p className="text-white font-medium">{CELL_LABELS[selectedCell.cellType]}</p>
                    <p className="text-[#64748b] text-xs">{selectedCell.cellType}</p>
                  </div>
                </div>
                <div className="space-y-2 text-sm">
                  <div className="flex justify-between">
                    <span className="text-[#94a3b8]">Position</span>
                    <span className="text-white font-mono">({selectedCell.rowIndex}, {selectedCell.colIndex})</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-[#94a3b8]">Root Point</span>
                    <span className="text-white font-mono text-xs">RP_{selectedCell.rowIndex}_{selectedCell.colIndex}</span>
                  </div>
                </div>

                {/* Packages at this cell */}
                {getPackagesOnCell(selectedCell.rowIndex, selectedCell.colIndex).length > 0 && (
                  <div className="mt-3 pt-3 border-t border-[#1e293b]">
                    <h4 className="text-[#94a3b8] text-xs font-semibold uppercase tracking-wider mb-2">
                      📦 Packages ({getPackagesOnCell(selectedCell.rowIndex, selectedCell.colIndex).length})
                    </h4>
                    <div className="space-y-2">
                      {getPackagesOnCell(selectedCell.rowIndex, selectedCell.colIndex).map((pkg) => (
                        <div key={pkg.id} className="bg-[#0f172a] rounded-lg p-2.5 space-y-1.5">
                          <div className="flex items-center justify-between">
                            <div className="flex items-center gap-2">
                              <div className={`w-3 h-3 rounded-sm ${PACKAGE_STATUS_COLORS[pkg.status] || 'bg-orange-500'}`} />
                              <span className="text-white text-sm font-medium">#{pkg.id}</span>
                            </div>
                            <span className={`text-xs px-2 py-0.5 rounded-full ${
                              pkg.status === 'RECEIVED' ? 'bg-orange-500/20 text-orange-300' :
                              pkg.status === 'IN_TRANSIT' ? 'bg-blue-500/20 text-blue-300' :
                              pkg.status === 'DISPATCHED' ? 'bg-purple-500/20 text-purple-300' :
                              'bg-gray-500/20 text-gray-300'
                            }`}>
                              {PACKAGE_STATUS_LABELS[pkg.status] || pkg.status}
                            </span>
                          </div>
                          <div className="flex items-center justify-between text-xs">
                            <span className="text-[#94a3b8]">SKU</span>
                            <span className="text-white font-mono">{pkg.sku}</span>
                          </div>
                          <div className="flex items-center justify-between text-xs">
                            <span className="text-[#94a3b8]">Quantity</span>
                            <span className="text-white font-bold">×{pkg.quantity}</span>
                          </div>
                          <div className="flex items-center justify-between text-xs">
                            <span className="text-[#94a3b8]">Target</span>
                            <span className="text-white font-mono">{pkg.targetSpotCode}</span>
                          </div>
                          {pkg.robotId && (
                            <div className="flex items-center justify-between text-xs">
                              <span className="text-[#94a3b8]">Robot</span>
                              <span className="text-primary-300 font-mono">{pkg.robotId}</span>
                            </div>
                          )}
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* Shelf Items Section */}
                {selectedCell.cellType === 'SHELF' && (
                  <div className="mt-3 pt-3 border-t border-[#1e293b]">
                    <h4 className="text-[#94a3b8] text-xs font-semibold uppercase tracking-wider mb-2">
                      Stored Items
                    </h4>
                    {loadingItems ? (
                      <div className="flex items-center justify-center py-4">
                        <div className="w-5 h-5 border-2 border-primary-500 border-t-transparent rounded-full animate-spin" />
                      </div>
                    ) : shelfItems.length === 0 ? (
                      <p className="text-[#64748b] text-sm text-center py-3">No items stored</p>
                    ) : (
                      <div className="space-y-2">
                        {shelfItems.map((item) => (
                          <div key={item.itemId} className="bg-[#0f172a] rounded-lg p-2.5 flex items-center justify-between">
                            <div className="min-w-0 flex-1">
                              <p className="text-white text-sm font-medium truncate">{item.name}</p>
                              <p className="text-[#64748b] text-xs font-mono">{item.sku}</p>
                            </div>
                            <div className="ml-2 flex items-center gap-1.5">
                              <span className="bg-blue-600/30 text-blue-300 text-xs font-bold px-2 py-0.5 rounded-full">
                                ×{item.quantityAvailable}
                              </span>
                            </div>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )}

                {/* Robots on this cell */}
                {getRobotsOnCell(selectedCell.rowIndex, selectedCell.colIndex).length > 0 && (
                  <div className="mt-3 pt-3 border-t border-[#1e293b]">
                    <h4 className="text-[#94a3b8] text-xs font-semibold uppercase tracking-wider mb-2">
                      Robots Here
                    </h4>
                    <div className="space-y-1.5">
                      {getRobotsOnCell(selectedCell.rowIndex, selectedCell.colIndex).map((robot) => (
                        <div key={robot.robotId} className="bg-[#0f172a] rounded-lg p-2 flex items-center gap-2">
                          <div className={`w-3 h-3 rounded-full ${MODE_COLORS[robot.operationalMode] || 'bg-gray-500'}`} />
                          <span className="text-white text-sm">{robot.name}</span>
                          <span className="text-[#64748b] text-xs ml-auto">{robot.operationalMode.replace(/_/g, ' ')}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            </div>
          )}

          {/* Robot list */}
          <div className="card">
            <h3 className="text-white font-semibold mb-3">
              Active Robots ({robots.size})
            </h3>
            <div className="space-y-2">
              {robots.size === 0 ? (
                <p className="text-[#64748b] text-sm text-center py-4">No robots found</p>
              ) : (
                Array.from(robots.values()).map((robot) => {
                  const pos = parseLocation(robot.currentLocation);
                  return (
                    <button
                      key={robot.robotId}
                      onClick={() => { setSelectedRobot(robot); setSelectedCell(null); }}
                      className={`w-full flex items-center gap-2 p-2 rounded-lg transition-colors text-left ${
                        selectedRobot?.robotId === robot.robotId ? 'bg-primary-600/20' : 'hover:bg-[#0f172a]'
                      }`}
                    >
                      <div className={`w-3 h-3 rounded-full ${MODE_COLORS[robot.operationalMode] || 'bg-gray-500'}`} />
                      <div className="flex-1 min-w-0">
                        <p className="text-white text-sm font-medium truncate">{robot.name}</p>
                        <p className="text-[#64748b] text-xs">
                          {pos ? `(${pos.row}, ${pos.col})` : robot.currentLocation || 'Unknown'}
                        </p>
                      </div>
                      <span className={`text-xs ${
                        robot.batteryLevel > 50 ? 'text-green-400' :
                        robot.batteryLevel > 20 ? 'text-yellow-400' : 'text-red-400'
                      }`}>
                        {robot.batteryLevel}%
                      </span>
                    </button>
                  );
                })
              )}
            </div>
          </div>

          {/* Package summary */}
          {packages.length > 0 && (
            <div className="card">
              <h3 className="text-white font-semibold mb-3">
                Packages ({packages.length})
              </h3>
              <div className="space-y-1.5">
                {packages
                  .filter(p => p.status !== 'DELIVERED')
                  .slice(0, 10)
                  .map((pkg) => (
                  <div
                    key={pkg.id}
                    className="flex items-center gap-2 p-2 rounded-lg bg-[#0f172a] hover:bg-[#0f172a]/80 cursor-pointer"
                    onClick={() => {
                      // Try to find the cell for this package and select it
                      const pos = pkg.status === 'RECEIVED'
                        ? resolveSpotPosition(pkg.receptionSpotCode)
                        : resolveSpotPosition(pkg.targetSpotCode);
                      if (pos && layout) {
                        const cell = layout.cells?.find(c => c.rowIndex === pos.row && c.colIndex === pos.col);
                        if (cell) { setSelectedCell(cell); setSelectedRobot(null); }
                      }
                    }}
                  >
                    <div className={`w-3 h-3 rounded-sm ${PACKAGE_STATUS_COLORS[pkg.status] || 'bg-orange-500'}`} />
                    <div className="flex-1 min-w-0">
                      <p className="text-white text-sm font-medium">#{pkg.id} — {pkg.sku}</p>
                      <p className="text-[#64748b] text-xs">
                        ×{pkg.quantity} → {pkg.targetSpotCode}
                      </p>
                    </div>
                    <span className={`text-xs px-1.5 py-0.5 rounded ${
                      pkg.status === 'RECEIVED' ? 'bg-orange-500/20 text-orange-300' :
                      pkg.status === 'IN_TRANSIT' ? 'bg-blue-500/20 text-blue-300' :
                      'bg-gray-500/20 text-gray-300'
                    }`}>
                      {PACKAGE_STATUS_LABELS[pkg.status] || pkg.status}
                    </span>
                  </div>
                ))}
                {packages.filter(p => p.status !== 'DELIVERED').length === 0 && (
                  <p className="text-[#64748b] text-sm text-center py-2">All packages delivered ✓</p>
                )}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}