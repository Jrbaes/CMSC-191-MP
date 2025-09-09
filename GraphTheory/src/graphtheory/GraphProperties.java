/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package graphtheory;

import java.awt.Color;
import java.awt.Graphics;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;

/**
 *
 * @author mk
 */
public class GraphProperties {

    public int[][] adjacencyMatrix;
    public int[][] distanceMatrix;
    public String distanceMatrixTitle = "ShortestPathMatrix";
    public Vector<VertexPair> vpList;

    public int[][] generateAdjacencyMatrix(Vector<Vertex> vList, Vector<Edge> eList) {
        adjacencyMatrix = new int[vList.size()][vList.size()];

        for (int a = 0; a < vList.size(); a++)//initialize
        {
            for (int b = 0; b < vList.size(); b++) {
                adjacencyMatrix[a][b] = 0;
            }
        }

        for (int i = 0; i < eList.size(); i++) {
            adjacencyMatrix[vList.indexOf(eList.get(i).vertex1)][vList.indexOf(eList.get(i).vertex2)] = 1;
            adjacencyMatrix[vList.indexOf(eList.get(i).vertex2)][vList.indexOf(eList.get(i).vertex1)] = 1;
        }
        return adjacencyMatrix;
    }

    public int[][] generateDistanceMatrix(Vector<Vertex> vList) {
        distanceMatrix = new int[vList.size()][vList.size()];

        for (int a = 0; a < vList.size(); a++)//initialize
        {
            for (int b = 0; b < vList.size(); b++) {
                distanceMatrix[a][b] = 0;
            }
        }

        VertexPair vp;
        int shortestDistance;
        for (int i = 0; i < vList.size(); i++) {
            for (int j = i + 1; j < vList.size(); j++) {
                vp = new VertexPair(vList.get(i), vList.get(j));
                shortestDistance = vp.getShortestDistance();
                distanceMatrix[vList.indexOf(vp.vertex1)][vList.indexOf(vp.vertex2)] = shortestDistance;
                distanceMatrix[vList.indexOf(vp.vertex2)][vList.indexOf(vp.vertex1)] = shortestDistance;
            }
        }
        distanceMatrixTitle = "ShortestPathMatrix (Dijkstra)";
        return distanceMatrix;
    }

    // Predecessor-based single-source shortest path (Dijkstra). Returns prev[] of indices; -1 denotes none.
    @SuppressWarnings("unchecked")
    public int[] dijkstraPredecessor(Vector<Vertex> vList, Vector<Edge> eList, int sourceIdx) {
        int n = vList.size();
        Vector<int[]>[] adj = new Vector[n];
        for (int i = 0; i < n; i++) adj[i] = new Vector<>();
        for (Edge e : eList) {
            int u = vList.indexOf(e.vertex1);
            int v = vList.indexOf(e.vertex2);
            if (u < 0 || v < 0) continue;
            int w = Math.max(0, e.weight);
            adj[u].add(new int[]{v, w});
            if (!e.isDirected) {
                adj[v].add(new int[]{u, w});
            }
        }

        final int INF = 1_000_000_000;
        int[] dist = new int[n];
        int[] prev = new int[n];
        boolean[] used = new boolean[n];
        Arrays.fill(dist, INF);
        Arrays.fill(prev, -1);
        dist[sourceIdx] = 0;

        for (int it = 0; it < n; it++) {
            int u = -1, best = INF;
            for (int i = 0; i < n; i++) {
                if (!used[i] && dist[i] < best) { best = dist[i]; u = i; }
            }
            if (u == -1) break;
            used[u] = true;
            for (int[] pr : adj[u]) {
                int v = pr[0], w = pr[1];
                if (dist[u] + w < dist[v]) {
                    dist[v] = dist[u] + w;
                    prev[v] = u;
                }
            }
        }
        return prev;
    }

    // Predecessor-based single-source shortest path (Bellman-Ford). Handles negative weights; returns prev[]; null if negative cycle detected.
    public int[] bellmanFordPredecessor(Vector<Vertex> vList, Vector<Edge> eList, int sourceIdx) {
        int n = vList.size();
        final int INF = 1_000_000_000;
        int[] dist = new int[n];
        int[] prev = new int[n];
        Arrays.fill(dist, INF);
        Arrays.fill(prev, -1);
        dist[sourceIdx] = 0;

        // Build edge list as directed arcs; if e.isDirected == false, include both directions
        class Arc { int u, v, w; Arc(int u, int v, int w){this.u=u;this.v=v;this.w=w;} }
        java.util.List<Arc> arcs = new java.util.ArrayList<>();
        for (Edge e : eList) {
            int u = vList.indexOf(e.vertex1);
            int v = vList.indexOf(e.vertex2);
            if (u < 0 || v < 0) continue;
            arcs.add(new Arc(u, v, e.weight));
            if (!e.isDirected) arcs.add(new Arc(v, u, e.weight));
        }

        for (int i = 0; i < n - 1; i++) {
            boolean any = false;
            for (Arc a : arcs) {
                if (dist[a.u] < INF && dist[a.u] + a.w < dist[a.v]) {
                    dist[a.v] = dist[a.u] + a.w;
                    prev[a.v] = a.u;
                    any = true;
                }
            }
            if (!any) break;
        }
        // Detect negative cycle (reachable)
        for (Arc a : arcs) {
            if (dist[a.u] < INF && dist[a.u] + a.w < dist[a.v]) {
                return null;
            }
        }
        return prev;
    }

    // Floyd-Warshall all-pairs shortest paths; returns dist matrix; sets distanceMatrix and title.
    public int[][] floydWarshall(Vector<Vertex> vList, Vector<Edge> eList) {
        int n = vList.size();
        final int INF = 1_000_000_000;
        int[][] dist = new int[n][n];
        for (int i = 0; i < n; i++) {
            Arrays.fill(dist[i], INF);
            dist[i][i] = 0;
        }
        for (Edge e : eList) {
            int u = vList.indexOf(e.vertex1);
            int v = vList.indexOf(e.vertex2);
            if (u < 0 || v < 0) continue;
            dist[u][v] = Math.min(dist[u][v], e.weight);
            if (!e.isDirected) dist[v][u] = Math.min(dist[v][u], e.weight);
        }

        for (int k = 0; k < n; k++) {
            for (int i = 0; i < n; i++) {
                if (dist[i][k] == INF) continue;
                for (int j = 0; j < n; j++) {
                    if (dist[k][j] == INF) continue;
                    int nd = dist[i][k] + dist[k][j];
                    if (nd < dist[i][j]) dist[i][j] = nd;
                }
            }
        }
        this.distanceMatrix = dist;
        this.distanceMatrixTitle = "All-Pairs Shortest Paths (Floyd-Warshall)";
        return dist;
    }

    // New: weighted, direction-aware shortest paths using Dijkstra from each source
    @SuppressWarnings("unchecked")
    public int[][] generateDistanceMatrixWeighted(Vector<Vertex> vList, Vector<Edge> eList) {
        int n = vList.size();
        distanceMatrix = new int[n][n];

        // Build adjacency lists with weights. If an edge is undirected (isDirected == false), add both directions.
        Vector<int[]>[] adj = new Vector[n]; // each int[] = {to, weight}
        for (int i = 0; i < n; i++) adj[i] = new Vector<>();
        for (Edge e : eList) {
            int u = vList.indexOf(e.vertex1);
            int v = vList.indexOf(e.vertex2);
            if (u < 0 || v < 0) continue;
            int w = Math.max(0, e.weight);
            adj[u].add(new int[]{v, w});
            if (!e.isDirected) {
                adj[v].add(new int[]{u, w});
            }
        }

        final int INF = 1_000_000_000;
        for (int s = 0; s < n; s++) {
            int[] dist = new int[n];
            boolean[] used = new boolean[n];
            Arrays.fill(dist, INF);
            dist[s] = 0;

            for (int iter = 0; iter < n; iter++) {
                int u = -1;
                int best = INF;
                for (int i = 0; i < n; i++) {
                    if (!used[i] && dist[i] < best) { best = dist[i]; u = i; }
                }
                if (u == -1) break; // remaining unreachable
                used[u] = true;
                for (int[] pr : adj[u]) {
                    int v = pr[0];
                    int w = pr[1];
                    if (dist[u] + w < dist[v]) {
                        dist[v] = dist[u] + w;
                    }
                }
            }

            for (int t = 0; t < n; t++) {
                distanceMatrix[s][t] = (s == t || dist[t] == INF) ? 0 : dist[t];
            }
        }

        return distanceMatrix;
    }

    public void displayContainers(Vector<Vertex> vList) {
        vpList = new Vector<VertexPair>();
        int[] kWideGraph = new int[10];
        for (int i = 0; i < kWideGraph.length; i++) {
            kWideGraph[i] = -1;
        }



        VertexPair vp;

        for (int a = 0; a < vList.size(); a++) {    // assign vertex pairs
            for (int b = a + 1; b < vList.size(); b++) {
                vp = new VertexPair(vList.get(a), vList.get(b));
                vpList.add(vp);
                int longestWidth = 0;
                System.out.println(">Vertex Pair " + vList.get(a).name + "-" + vList.get(b).name + "\n All Paths:");
                vp.generateVertexDisjointPaths();
                for (int i = 0; i < vp.VertexDisjointContainer.size(); i++) {//for every container of the vertex pair
                    int width = vp.VertexDisjointContainer.get(i).size();
                    Collections.sort(vp.VertexDisjointContainer.get(i), new descendingWidthComparator());
                    int longestLength = vp.VertexDisjointContainer.get(i).firstElement().size();
                    longestWidth = Math.max(longestWidth, width);
                    System.out.println("\tContainer " + i + " - " + "Width=" + width + " - Length=" + longestLength);

                    for (int j = 0; j < vp.VertexDisjointContainer.get(i).size(); j++) //for every path in the container
                    {
                        System.out.print("\t\tPath " + j + "\n\t\t\t");
                        for (int k = 0; k < vp.VertexDisjointContainer.get(i).get(j).size(); k++) {
                            System.out.print("-" + vp.VertexDisjointContainer.get(i).get(j).get(k).name);
                        }
                        System.out.println();
                    }

                }
                //d-wide for vertexPair
                for (int k = 1; k <= longestWidth; k++) { // 1-wide, 2-wide, 3-wide...
                    int minLength = 999;
                    for (int m = 0; m < vp.VertexDisjointContainer.size(); m++) // for each container with k-wide select shortest length
                    {
                        minLength = Math.min(minLength, vp.VertexDisjointContainer.get(m).size());
                    }
                    if (minLength != 999) {
                        System.out.println(k + "-wide for vertexpair(" + vp.vertex1.name + "-" + vp.vertex2.name + ")=" + minLength);
                        kWideGraph[k] = Math.max(kWideGraph[k], minLength);
                    }
                }
            }
        }

        for (int i = 0; i < kWideGraph.length; i++) {
            if (kWideGraph[i] != -1) {
                System.out.println("D" + i + "(G)=" + kWideGraph[i]);
            }
        }


    }

    public void drawAdjacencyMatrix(Graphics g, Vector<Vertex> vList, int x, int y) {
        int cSize = 20;
        g.setColor(Color.LIGHT_GRAY);
        g.fillRect(x, y-30, vList.size() * cSize+cSize, vList.size() * cSize+cSize);
        g.setColor(Color.black);
        g.drawString("AdjacencyMatrix", x, y - cSize);
        for (int i = 0; i < vList.size(); i++) {
            g.setColor(Color.RED);
            g.drawString(vList.get(i).name, x + cSize + i * cSize, y);
            g.drawString(vList.get(i).name, x, cSize + i * cSize + y);
            g.setColor(Color.black);
            for (int j = 0; j < vList.size(); j++) {
                g.drawString("" + adjacencyMatrix[i][j], x + cSize * (j + 1), y + cSize * (i + 1));
            }
        }
    }

    public void drawDistanceMatrix(Graphics g, Vector<Vertex> vList, int x, int y) {
        int cSize = 20;
        g.setColor(Color.LIGHT_GRAY);
        g.fillRect(x, y-30, vList.size() * cSize+cSize, vList.size() * cSize+cSize);
        g.setColor(Color.black);
        g.drawString(distanceMatrixTitle, x, y - cSize);
        for (int i = 0; i < vList.size(); i++) {
            g.setColor(Color.RED);
            g.drawString(vList.get(i).name, x + cSize + i * cSize, y);
            g.drawString(vList.get(i).name, x, cSize + i * cSize + y);
            g.setColor(Color.black);
            for (int j = 0; j < vList.size(); j++) {
                g.drawString("" + distanceMatrix[i][j], x + cSize * (j + 1), y + cSize * (i + 1));
            }
        }
    }

    public Vector<Vertex> vertexConnectivity(Vector<Vertex> vList) {
        Vector<Vertex> origList = new Vector<Vertex>();
        Vector<Vertex> tempList = new Vector<Vertex>();
        Vector<Vertex> toBeRemoved = new Vector<Vertex>();
        Vertex victim;


        origList.setSize(vList.size());
        Collections.copy(origList, vList);

        int maxPossibleRemove = 0;
        while (graphConnectivity(origList)) {
            Collections.sort(origList, new ascendingDegreeComparator());
            maxPossibleRemove = origList.firstElement().getDegree();

            for (Vertex v : origList) {
                if (v.getDegree() == maxPossibleRemove) {
                    for (Vertex z : v.connectedVertices) {
                        if (!tempList.contains(z)) {
                            tempList.add(z);
                        }
                    }
                }
            }

            while (graphConnectivity(origList) && tempList.size() > 0) {
                Collections.sort(tempList, new descendingDegreeComparator());
                victim = tempList.firstElement();
                tempList.removeElementAt(0);
                origList.remove(victim);
                for (Vertex x : origList) {
                    x.connectedVertices.remove(victim);
                }
                toBeRemoved.add(victim);
            }
            tempList.removeAllElements();
        }

        return toBeRemoved;
    }

    private boolean graphConnectivity(Vector<Vertex> vList) {

        Vector<Vertex> visitedList = new Vector<Vertex>();

        recurseGraphConnectivity(vList.firstElement().connectedVertices, visitedList); //recursive function
        if (visitedList.size() != vList.size()) {
            return false;
        } else {
            return true;
        }
    }

    private void recurseGraphConnectivity(Vector<Vertex> vList, Vector<Vertex> visitedList) {
        for (Vertex v : vList) {
            {
                if (!visitedList.contains(v)) {
                    visitedList.add(v);
                    recurseGraphConnectivity(v.connectedVertices, visitedList);
                }
            }
        }
    }

    private class ascendingDegreeComparator implements Comparator<Vertex> {

        public int compare(Vertex v1, Vertex v2) {
            return Integer.compare(v1.getDegree(), v2.getDegree());
        }
    }

    private class descendingDegreeComparator implements Comparator<Vertex> {

        public int compare(Vertex v1, Vertex v2) {
            return Integer.compare(v2.getDegree(), v1.getDegree());
        }
    }

    private class descendingWidthComparator implements Comparator<Vector<Vertex>> {

        public int compare(Vector<Vertex> v1, Vector<Vertex> v2) {
            return Integer.compare(v2.size(), v1.size());
        }
    }
}
