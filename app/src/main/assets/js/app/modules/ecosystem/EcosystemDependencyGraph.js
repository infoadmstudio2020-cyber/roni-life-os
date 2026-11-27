/**
 * EcosystemDependencyGraph — Inter-library dependency analysis and interactive visual graph generator.
 *
 * Capabilities:
 * - Inspects dependency relationships between registered ecosystem libraries
 * - Builds cross-library dependency matrix and directed graph
 * - Detects outdated dependencies against registered library latest versions
 * - Detects missing registrations (dependencies with ecosystem pattern but not in manager)
 * - Detects version conflicts (multiple libraries depending on different versions of same library)
 * - Detects dependency cycles (A -> B -> A) using Depth-First Search (DFS)
 * - Renders crisp, responsive SVG / HTML dependency graph
 * - Strictly read-only: does NOT automatically modify source code
 */

import { VersionComparator } from './VersionComparator.js';

export class EcosystemDependencyGraph {

  /**
   * Analyze dependency network across all registered libraries.
   *
   * @param {Array<object>} libraries
   * @returns {{
   *   nodes: Array<object>,
   *   edges: Array<object>,
   *   outdatedCount: number,
   *   conflictCount: number,
   *   cycleCount: number,
   *   outdatedList: Array<object>,
   *   conflictList: Array<object>,
   *   cycles: Array<string[]>,
   *   missingRegistrations: Array<object>
   * }}
   */
  static analyze(libraries = []) {
    // Index registered libraries by artifactId, full coordinate (groupId:artifactId), and repo name
    const libMapByArtifact = new Map();
    const libMapByCoord = new Map();
    const libMapById = new Map();

    for (const lib of libraries) {
      const art = (lib.artifactId || lib.repo || '').toLowerCase();
      const coord = `${(lib.groupId || '').toLowerCase()}:${art}`;
      libMapByArtifact.set(art, lib);
      libMapByCoord.set(coord, lib);
      libMapById.set(lib.id.toLowerCase(), lib);
    }

    const nodes = [];
    const edges = [];
    const outdatedList = [];
    const missingRegistrations = [];
    const missingCoordSet = new Set();
    const dependencyUsageMap = new Map(); // targetCoord -> [{ sourceLib, declaredVersion, scope }]

    // 1. Create nodes for all registered libraries
    for (const lib of libraries) {
      nodes.push({
        id: lib.id,
        name: lib.name || lib.repo,
        owner: lib.owner,
        repo: lib.repo,
        groupId: lib.groupId,
        artifactId: lib.artifactId,
        version: lib.projectVersion || lib.githubRelease || '1.0.0',
        ecosystemGroup: lib.ecosystemGroup || 'Default',
        status: lib.versionStatus || 'Latest',
        severity: lib.severity || 'success',
      });
    }

    // 2. Discover edges and analyze dependencies
    for (const sourceLib of libraries) {
      const deps = sourceLib.dependencies || [];

      for (const dep of deps) {
        const depArt = (dep.artifactId || dep.name || '').toLowerCase();
        const depGroup = (dep.groupId || '').toLowerCase();
        const depCoord = depGroup ? `${depGroup}:${depArt}` : depArt;
        const declaredVer = dep.version || 'unknown';
        const scope = dep.scope || 'implementation';

        // Find if this dependency is one of our registered libraries
        let targetLib = libMapByCoord.get(depCoord) || libMapByArtifact.get(depArt);

        if (targetLib) {
          // Self-dependency safety
          if (targetLib.id === sourceLib.id) continue;

          // Track usage for conflict detection
          if (!dependencyUsageMap.has(targetLib.id)) {
            dependencyUsageMap.set(targetLib.id, []);
          }
          dependencyUsageMap.get(targetLib.id).push({
            sourceLib,
            declaredVersion: declaredVer,
            scope,
          });

          // Check if outdated
          const latestTargetVer = targetLib.projectVersion || targetLib.githubRelease || targetLib.gitTag;
          let isOutdated = false;
          if (declaredVer && latestTargetVer && !VersionComparator.isEqual(declaredVer, latestTargetVer)) {
            if (VersionComparator.isLess(declaredVer, latestTargetVer)) {
              isOutdated = true;
              outdatedList.push({
                sourceLib: sourceLib.name || sourceLib.repo,
                sourceId: sourceLib.id,
                targetLib: targetLib.name || targetLib.repo,
                targetId: targetLib.id,
                declaredVersion: declaredVer,
                latestVersion: latestTargetVer,
                scope,
              });
            }
          }

          edges.push({
            from: sourceLib.id,
            to: targetLib.id,
            fromName: sourceLib.name || sourceLib.repo,
            toName: targetLib.name || targetLib.repo,
            declaredVersion: declaredVer,
            latestVersion: latestTargetVer,
            scope,
            isOutdated,
          });
        } else {
          // Check if this looks like an unregistered ecosystem dependency (e.g. com.github.<owner>:...)
          if (depGroup.includes('com.github.') || depGroup.includes('nativeruntime')) {
            if (!missingCoordSet.has(depCoord)) {
              missingCoordSet.add(depCoord);
              missingRegistrations.push({
                sourceLib: sourceLib.name || sourceLib.repo,
                groupId: dep.groupId,
                artifactId: dep.artifactId || dep.name,
                declaredVersion: declaredVer,
                scope,
              });
            }
          }
        }
      }
    }

    // 3. Detect version conflicts: multiple libraries depending on different versions of the same target
    const conflictList = [];
    for (const [targetId, usages] of dependencyUsageMap.entries()) {
      if (usages.length > 1) {
        const versions = new Set(usages.map(u => u.declaredVersion));
        if (versions.size > 1) {
          const targetLib = libMapById.get(targetId);
          conflictList.push({
            targetLib: targetLib?.name || targetId,
            targetId,
            conflictingVersions: Array.from(versions),
            usages: usages.map(u => ({
              consumer: u.sourceLib.name || u.sourceLib.repo,
              version: u.declaredVersion,
              scope: u.scope,
            })),
          });
        }
      }
    }

    // 4. Detect dependency cycles using DFS
    const cycles = this.detectCycles(nodes.map(n => n.id), edges);

    return {
      nodes,
      edges,
      outdatedCount: outdatedList.length,
      conflictCount: conflictList.length,
      cycleCount: cycles.length,
      outdatedList,
      conflictList,
      cycles,
      missingRegistrations,
    };
  }

  /**
   * Detect cycles in directed graph using DFS.
   *
   * @param {string[]} nodeIds
   * @param {Array<{from: string, to: string}>} edges
   * @returns {Array<string[]>} list of cycles (each cycle is an array of node IDs)
   */
  static detectCycles(nodeIds, edges) {
    const adj = new Map();
    for (const id of nodeIds) adj.set(id, []);
    for (const e of edges) {
      if (adj.has(e.from)) adj.get(e.from).push(e.to);
    }

    const visited = new Map(); // 0 = unvisited, 1 = visiting (on stack), 2 = visited
    const cycles = [];
    const stack = [];

    const dfs = (u) => {
      visited.set(u, 1);
      stack.push(u);

      const neighbors = adj.get(u) || [];
      for (const v of neighbors) {
        const state = visited.get(v) || 0;
        if (state === 1) {
          // Cycle found! Extract cycle from stack
          const cycleStart = stack.indexOf(v);
          if (cycleStart !== -1) {
            cycles.push([...stack.slice(cycleStart), v]);
          }
        } else if (state === 0) {
          dfs(v);
        }
      }

      stack.pop();
      visited.set(u, 2);
    };

    for (const id of nodeIds) {
      if ((visited.get(id) || 0) === 0) {
        dfs(id);
      }
    }

    return cycles;
  }

  /**
   * Render an interactive responsive SVG dependency diagram.
   *
   * @param {{ nodes: Array<object>, edges: Array<object>, cycles: Array<string[]> }} graphData
   * @param {number} [width=800]
   * @param {number} [height=420]
   * @returns {string} SVG markup string
   */
  static renderSvgGraph(graphData, width = 800, height = 400) {
    const { nodes, edges, cycles } = graphData;

    if (!nodes || nodes.length === 0) {
      return `
        <div style="padding:40px;text-align:center;color:var(--text-muted)">
          <span class="material-symbols-outlined" style="font-size:48px">hub</span>
          <p style="margin-top:8px">No ecosystem libraries registered yet to build dependency graph.</p>
        </div>`;
    }

    // Arrange nodes in a circular or multi-row layout
    const nodeCount = nodes.length;
    const centerX = width / 2;
    const centerY = height / 2;
    const radiusX = Math.max(160, Math.min(width * 0.38, 300));
    const radiusY = Math.max(110, Math.min(height * 0.35, 150));

    const coords = new Map();
    nodes.forEach((n, idx) => {
      const angle = (2 * Math.PI * idx) / nodeCount - Math.PI / 2;
      const x = centerX + radiusX * Math.cos(angle);
      const y = centerY + radiusY * Math.sin(angle);
      coords.set(n.id, { x, y });
    });

    // Check which nodes are in a cycle
    const cycleNodes = new Set();
    cycles.forEach(c => c.forEach(id => cycleNodes.add(id)));

    // Generate edge arrows
    let edgesSvg = '';
    for (const e of edges) {
      const p1 = coords.get(e.from);
      const p2 = coords.get(e.to);
      if (!p1 || !p2) continue;

      const isCycleEdge = cycleNodes.has(e.from) && cycleNodes.has(e.to);
      const strokeColor = isCycleEdge ? '#EF4444' : (e.isOutdated ? '#F59E0B' : '#0284C7');
      const strokeDash = e.scope === 'compileOnly' ? 'stroke-dasharray="4,4"' : '';

      // Offset line so arrow doesn't overlap node circle
      const dx = p2.x - p1.x;
      const dy = p2.y - p1.y;
      const dist = Math.sqrt(dx * dx + dy * dy) || 1;
      const targetRadius = 38;
      const endX = p2.x - (dx / dist) * targetRadius;
      const endY = p2.y - (dy / dist) * targetRadius;

      // Midpoint for label
      const midX = (p1.x + endX) / 2;
      const midY = (p1.y + endY) / 2;

      edgesSvg += `
        <g class="dep-edge">
          <line x1="${p1.x}" y1="${p1.y}" x2="${endX}" y2="${endY}"
                stroke="${strokeColor}" stroke-width="2" ${strokeDash}
                marker-end="url(#arrow-${e.isOutdated ? 'warning' : (isCycleEdge ? 'error' : 'normal')})" />
          <rect x="${midX - 32}" y="${midY - 9}" width="64" height="18" rx="4" fill="var(--bg-surface)" stroke="var(--border)" stroke-width="1" />
          <text x="${midX}" y="${midY + 4}" fill="${strokeColor}" font-size="10" text-anchor="middle" font-family="monospace">
            ${e.declaredVersion || e.scope}
          </text>
        </g>`;
    }

    // Generate node circles and labels
    let nodesSvg = '';
    for (const n of nodes) {
      const p = coords.get(n.id);
      if (!p) continue;

      const inCycle = cycleNodes.has(n.id);
      const borderCol = inCycle ? '#EF4444' : (n.severity === 'warning' ? '#F59E0B' : (n.severity === 'error' ? '#EF4444' : '#10B981'));
      const bgCol = 'var(--bg-card)';

      nodesSvg += `
        <g class="dep-node" data-id="${n.id}" style="cursor:pointer">
          <circle cx="${p.x}" cy="${p.y}" r="32" fill="${bgCol}" stroke="${borderCol}" stroke-width="2.5" />
          <text x="${p.x}" y="${p.y - 6}" fill="var(--text-primary)" font-size="11" font-weight="600" text-anchor="middle">
            ${n.name.length > 12 ? n.name.slice(0, 10) + '…' : n.name}
          </text>
          <text x="${p.x}" y="${p.y + 10}" fill="var(--text-muted)" font-size="9" text-anchor="middle" font-family="monospace">
            ${n.version}
          </text>
          <text x="${p.x}" y="${p.y + 22}" fill="${borderCol}" font-size="8" font-weight="600" text-anchor="middle">
            ${inCycle ? 'CYCLE' : n.status}
          </text>
        </g>`;
    }

    return `
      <svg viewBox="0 0 ${width} ${height}" class="ecosystem-dep-svg" style="width:100%;height:auto;max-height:${height}px;background:var(--bg-surface);border-radius:var(--radius-lg);border:1px solid var(--border)">
        <defs>
          <marker id="arrow-normal" viewBox="0 0 10 10" refX="6" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
            <path d="M 0 1 L 8 5 L 0 9 z" fill="#0284C7" />
          </marker>
          <marker id="arrow-warning" viewBox="0 0 10 10" refX="6" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
            <path d="M 0 1 L 8 5 L 0 9 z" fill="#F59E0B" />
          </marker>
          <marker id="arrow-error" viewBox="0 0 10 10" refX="6" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
            <path d="M 0 1 L 8 5 L 0 9 z" fill="#EF4444" />
          </marker>
        </defs>
        ${edgesSvg}
        ${nodesSvg}
      </svg>`;
  }
}
