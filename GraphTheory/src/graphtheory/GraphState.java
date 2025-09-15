package graphtheory;

import java.util.Vector;
import java.awt.Color;

public class GraphState {
    public Vector<Vertex> vertexList;
    public Vector<Edge> edgeList;
    public boolean directionalityEnabled;
    public boolean weightsEnabled;
    public int selectedTool;
    public double zoom;
    public int panX, panY;

    public GraphState(Vector<Vertex> vertexList, Vector<Edge> edgeList, 
                     boolean directionalityEnabled, boolean weightsEnabled, 
                     int selectedTool, double zoom, int panX, int panY) {
        this.directionalityEnabled = directionalityEnabled;
        this.weightsEnabled = weightsEnabled;
        this.selectedTool = selectedTool;
        this.zoom = zoom;
        this.panX = panX;
        this.panY = panY;
        
        this.vertexList = new Vector<>();
        for (Vertex v : vertexList) {
            this.vertexList.add(cloneVertex(v));
        }

        // After all vertices are cloned, establish their connections
        for (int i = 0; i < vertexList.size(); i++) {
            Vertex originalVertex = vertexList.get(i);
            Vertex clonedVertex = this.vertexList.get(i);
            for (Vertex connected : originalVertex.connectedVertices) {
                Vertex clonedConnected = findClonedVertex(connected, this.vertexList);
                if (clonedConnected != null) {
                    clonedVertex.addVertex(clonedConnected);
                }
            }
        }

        this.edgeList = new Vector<>();
        for (Edge e : edgeList) {
            this.edgeList.add(cloneEdge(e, this.vertexList));
        }
    }

    private Vertex cloneVertex(Vertex original) {
        Vertex clone = new Vertex(original.name, original.location.x, original.location.y);
        clone.color = original.color;
        // We will restore connections later to avoid deep recursion issues
        return clone;
    }

    private Edge cloneEdge(Edge original, Vector<Vertex> clonedVertices) {
        Vertex v1 = findClonedVertex(original.vertex1, clonedVertices);
        Vertex v2 = findClonedVertex(original.vertex2, clonedVertices);
        Edge clone = new Edge(v1, v2);
        clone.color = original.color;
        clone.isDirected = original.isDirected;
        clone.weight = original.weight;
        return clone;
    }

    private Vertex findClonedVertex(Vertex original, Vector<Vertex> clonedVertices) {
        for (Vertex v : clonedVertices) {
            if (v.name.equals(original.name)) {
                return v;
            }
        }
        return null; // Should not happen
    }
}
