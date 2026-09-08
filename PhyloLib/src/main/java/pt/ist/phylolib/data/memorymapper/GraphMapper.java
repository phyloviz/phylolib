package pt.ist.phylolib.data.memorymapper;

import pt.ist.phylolib.data.tree.Edge;
import pt.ist.phylolib.data.dataset.Profile;
import pt.ist.phylolib.command.algorithm.edmonds.WeightedDisjointSet;
import static pt.ist.phylolib.data.memorymapper.MapperConstants.*;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.function.Consumer;

/**
 * GraphMapper provides high-level methods to save and load entire graphs
 * using memory-mapped files. This class wraps EdgeListMapper and NodeIndexMapper
 * to store and query graphs.
 * <p>
 * The graph is stored in |V| + 3 files:
 * 
 * - One index for the nodes and their sequence data;
 * 
 * - |V| files for the edges incident to each node. 
 * 
 * - One file to store a node id -> offset pair for quick access to a node in the index file. This is used for node deletions.
 * 
 * - One file to store two serialized maps (one from node id to the profile string id and the inverse map) and a (possibly empty)
 * list of free node ids that can be reused for new nodes. 
 *  
 * The files are named based on a provided base name:
 * 
 * - {baseName}_edges_node{nodeId}.dat: Array of edges pointing to node with ID nodeId
 * - {baseName}_nodes.dat: Node data (header + MLST data and incoming edge offsets)
 * - {baseName}_nodes_offsetMap.ser: Serialized map of node ID to offset in the nodes.dat file for quick access
 * - {baseName}_nodes_idMaps.ser: Serialized maps for node ID to profile string ID and the inverse, plus free node ID list
 */
public class GraphMapper {

    private NodeIndexMapper nodeIndexMapper;
    private String nodeFileName;
    private String baseName;

    public GraphMapper(String baseName) {
        this.baseName = baseName;
        this.nodeFileName = baseName + NODE_FILE_SUFFIX;
        this.nodeIndexMapper = new NodeIndexMapper(this.nodeFileName);
    }

    public GraphMapper(String baseName, List<Profile> profiles, int sequenceLength) throws IOException {
        this(baseName);
        saveGraph(profiles, sequenceLength);
    }
    
    /**
     * Save a graph to memory-mapped files.
     * 
     * @param profiles List of profiles to save
     * @param edges List of edges to save
     * @param sequenceLength Fixed length for genomic data (in bytes)
     * @param baseName Base name for output files
     * @throws IOException if file operations fail
     */
    public void saveGraph(List<Profile> profiles, List<Edge> edges, int sequenceLength, String baseName) throws IOException {
        // Save node index
        nodeIndexMapper.saveGraph(profiles, sequenceLength);

        // Group edges by their destination node
        Map<Integer, List<Edge>> edgesByDestination = new HashMap<>();
        for (Edge edge : edges) {
            int destId = edge.to();
            edgesByDestination.computeIfAbsent(destId, k -> new ArrayList<>()).add(edge);
        }

        // Save edges to separate per-node files
        // For nodes with edges, save them; for nodes without edges, create empty files
        for (Profile profile : profiles) {
            int nodeId = nodeIndexMapper.strIDToIntegerID(profile.id());
            List<Edge> nodeEdges = edgesByDestination.getOrDefault(nodeId, new ArrayList<>());
            String nodeEdgeFile = baseName + "_edges_node" + nodeId + ".dat";
            EdgeListMapper.writeEdgeArray(nodeEdgeFile, nodeEdges);
        }
    }


    /**
     * Save a graph with nodes but no edges to memory-mapped files. Useful for initializing empty graphs or
     * when the edge weights are computed on-demand.
     * 
     * @param profiles List of profiles to save
     * @param sequenceLength Fixed length for genomic data (in bytes)
     * @throws IOException if file operations fail
     */
    public void saveGraph(List<Profile> profiles, int sequenceLength) throws IOException {
        nodeIndexMapper.saveGraph(profiles, sequenceLength);
    }

    /**
     * Load node IDs from the memory-mapped file. This method is more memory-efficient than loading full Node objects
     * when only node IDs are needed for computation (e.g., when edges are pre-computed and stored on disk).
     * 
     * @return Array of node IDs
     * @throws IOException if file operations fail
     */
    public int[] loadNodeIDs() throws IOException {
        return nodeIndexMapper.loadNodeIDs();
    }

    /**
     * Load all profiles from the memory-mapped node index file.
     *
     * @return List of profiles in file order
     * @throws IOException if file operations fail
     */
    public List<Profile> loadProfiles() throws IOException {
        return nodeIndexMapper.loadProfiles();
    }

    /**
     * Read the sequence length from the node index file header.
     *
     * @return the sequence length
     * @throws IOException if file operations fail
     */
    public int loadSequenceLength() throws IOException {
        return nodeIndexMapper.getSequenceLength();
    }

    /**
     * Get the number of nodes in the graph.
     *
     * @return number of nodes
     * @throws IOException if file operations fail
     */
    public int getNumNodes() throws IOException {
        return nodeIndexMapper.getNumNodes();
    }

    /**
     * Add multiple profiles to the node index in a batch.
     *
     * @param profiles profiles to add
     * @param sequenceLength sequence length for all profiles
     * @throws IOException if file operations fail
     */
    public void addNodeBatch(List<Profile> profiles, int sequenceLength) throws IOException {
        nodeIndexMapper.addNodesBatch(profiles, sequenceLength);
    }

    /**
     * Resolve a string profile ID to its integer node ID.
     *
     * @param id the string profile ID
     * @return the integer node ID
     */
    public int strIDToIntegerID(String id) {
        return nodeIndexMapper.strIDToIntegerID(id);
    }
    
    
    /**
     * Read incoming edges for a specific node.
     * 
     * @param nodeId Node ID to query
     * @return List of incoming edges for the node
     * @throws IOException if file operations fail
     */
    public List<Edge> getIncomingEdges(int nodeId) throws IOException {
        return EdgeListMapper.loadEdgeArray(baseName + EDGE_NODE_FILE_SUFFIX + nodeId + FILE_EXTENSION);
    }

    public List<Edge> getIncomingEdgesUpToId(int nodeId, int maxSourceId) throws IOException {
        return EdgeListMapper.loadEdgeArrayUpToId(baseName + EDGE_NODE_FILE_SUFFIX + nodeId + FILE_EXTENSION, maxSourceId);
    }

    /**
     * Add a single node and its incident edges to the graph files.
     *
     * @param node Node to add
     * @param incomingEdges List of edges pointing TO the new node
     * @param outgoingEdges List of edges FROM the new node to other nodes
     * @param sequenceLength Fixed length for genomic data
     * @throws IOException if file operations fail
     */
    public void addNode(Profile node, List<Edge> incomingEdges, List<Edge> outgoingEdges, 
                              int sequenceLength) throws IOException {
        String edgeFile = baseName + EDGE_FILE_SUFFIX;

        // Add the node itself to the node index
        nodeIndexMapper.addNode(node, sequenceLength);
        int newNodeId = nodeIndexMapper.strIDToIntegerID(node.id());
        
        // Add incoming edges (stored as a linked list for this node)
        EdgeListMapper.addEdges(incomingEdges, newNodeId, edgeFile);
        
        // Add outgoing edges (each needs to be added to its destination's linked list)
        for (Edge outgoingEdge : outgoingEdges) {
            EdgeListMapper.addEdge(outgoingEdge, edgeFile);
        }
    }
    
    /**
     * Add a single node and its incoming edges to the graph files.
     *
     * @param node Node to add
     * @param incomingEdges List of edges pointing TO the new node
     * @param sequenceLength Fixed length for MLST data
     * @throws IOException if file operations fail
     */
    public void addNode(Profile node, List<Edge> incomingEdges, int sequenceLength) throws IOException {
        addNode(node, incomingEdges, List.of(), sequenceLength);
    }
    
    /**
     * Add multiple nodes and their edges in a single batch operation.
     * 
     * @param nodes List of nodes to add
     * @param nodeEdges Map of node to its incoming edges
     * @param existingNodeNewEdges Map of existing nodes to edges that should be added to them (edges from new nodes TO existing nodes)
     * @param sequenceLength Fixed length for genomic data
     * @throws IOException if file operations fail
     */
    public void addNodesBatch(List<Profile> nodes, Map<Profile, List<Edge>> nodeEdges, 
                                     Map<Profile, List<Edge>> existingNodeNewEdges,
                                     int sequenceLength) throws IOException {
        String edgeFile = baseName + EDGE_FILE_SUFFIX;
        
        // Add all nodes at once
        nodeIndexMapper.addNodesBatch(nodes, sequenceLength);
        
        // Get the Integer IDs for the new nodes after they have been added
        Map<Integer, List<Edge>> nodeIdToEdges = new HashMap<>();
        for (Profile node : nodes) {
            int nodeId = nodeIndexMapper.strIDToIntegerID(node.id());
            nodeIdToEdges.put(nodeId, nodeEdges.getOrDefault(node, List.of()));
        }

        // Add edges for new nodes in one batch operation
        EdgeListMapper.addEdgesBatch(nodeIdToEdges, edgeFile);

        // Convert existingNodeNewEdges to use Integer node IDs instead of Profile keys
        Map<Integer, List<Edge>> existingNodeIdToNewEdges = new HashMap<>();
        for (Map.Entry<Profile, List<Edge>> entry : existingNodeNewEdges.entrySet()) {
            int existingNodeId = nodeIndexMapper.strIDToIntegerID(entry.getKey().id());
            existingNodeIdToNewEdges.put(existingNodeId, entry.getValue());
        }
        
        // Add edges incoming to existing nodes
        if (existingNodeNewEdges != null && !existingNodeNewEdges.isEmpty()) {
            EdgeListMapper.addEdgesToExistingNodes(existingNodeIdToNewEdges, nodeFileName, edgeFile);
        }
    }

    /**
     * Remove a single node and all its incident and outgoing edges from the graph files.
     * @param node Node to remove
     * @throws IOException
     */
    public void removeNode(Profile node) throws IOException {
        String edgeFile = baseName + EDGE_FILE_SUFFIX;

        // Remove the edge file for this node (incoming edges)
        EdgeListMapper.removeEdges(edgeFile, nodeIndexMapper.strIDToIntegerID(node.id()));

        // Remove all outgoing edges from this node (edges in other nodes' files)
        EdgeListMapper.removeOutgoingEdges(edgeFile, nodeIndexMapper.strIDToIntegerID(node.id()), nodeIndexMapper);

        // Remove the node from the node index
        nodeIndexMapper.removeNode(node);
    }

    /**
     * Remove multiple nodes and their incident edges in a single batch operation.
     * <p>
     * The operation removes:
     * 1. All edges where the source or destination node is in the nodes list
     * 2. All corresponding node entries from the node index
     * 
     * @param nodes List of nodes to remove
     * @param sequenceLength Fixed length for genomic data
     * @throws IOException if file operations fail
     */
    public void removeNodesBatch(List<Profile> nodes, int sequenceLength) throws IOException {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        
        String edgeFile = baseName + EDGE_FILE_SUFFIX;
        
        // Create set of node IDs for edge removal
        Set<Integer> nodeIds = new HashSet<>();
        for (Profile node : nodes) {
            nodeIds.add(nodeIndexMapper.strIDToIntegerID(node.id()));
        }
        
        // Remove all edges incident to these nodes in one batch
        EdgeListMapper.removeEdgesBatch(nodeIds, edgeFile);
        
        // Remove all outgoing edges from these nodes
        for (Integer nodeId : nodeIds) {
            EdgeListMapper.removeOutgoingEdges(edgeFile, nodeId, nodeIndexMapper);
        }
        
        // Remove all nodes in one batch
        nodeIndexMapper.removeNodesBatch(nodes);
    }

    /**
     * Check if an edge exists between two nodes.
     * @param sourceId ID of the source node
     * @param destId ID of the destination node
     * @return true if the edge exists, false otherwise
     * @throws IOException if file operations fail
     */
    public boolean edgeExists(int sourceId, int destId) throws IOException {
        return EdgeListMapper.edgeExists(baseName, sourceId, destId);
    }


    /**
     * Remove an edge between two nodes.
     * @param sourceId ID of the source node
     * @param destId ID of the destination node
     * @throws IOException if file operations fail
     */
    public void removeEdge(int sourceId, int destId) throws IOException {
        EdgeListMapper.removeEdge(baseName, sourceId, destId);
    }

    /**
     * Add an edge to the graph. This method updates the edge file and the corresponding node's edge list.
     * @param edge Edge to add
     * @throws IOException if file operations fail
     */
    public void addEdge(Edge edge) throws IOException {
        String edgeFile = baseName + EDGE_FILE_SUFFIX;
        EdgeListMapper.addEdge(edge, edgeFile);
    }

    /**
    * Load edges incident to a specific node. This method reads the edge file for the given node ID and returns the list of edges.
    * @param nodeId ID of the node whose incident edges to load
    * @return List of edges incident to the specified node
    * @throws IOException if file operations fail
    */
    public List<Edge> loadIncidentEdges(int nodeId) throws IOException {
        String edgeFile = baseName + EDGE_NODE_FILE_SUFFIX + nodeId + FILE_EXTENSION;
        return EdgeListMapper.loadEdgeArray(edgeFile);
    }
    
    /**
     * Stream edges incident to a specific node directly to a consumer.
     * 
     * @param nodeId ID of the node whose incident edges to stream
     * @param edgeConsumer Function to process each edge as it's read
     */
    public void streamIncidentEdges(int nodeId, Consumer<Edge> edgeConsumer) {
        String edgeFile = baseName + EDGE_NODE_FILE_SUFFIX + nodeId + FILE_EXTENSION;
        EdgeListMapper.streamEdges(edgeFile, edgeConsumer);
    }

    /**
     * Save a phylogenetic tree (arborescence) to a memory-mapped file. The tree is represented as a list of edges.
     * 
     * @param phylogeny List of edges representing the phylogenetic tree
     * @throws IOException if file operations fail
     */
    public void saveArborescence(List<Edge> phylogeny) throws IOException {
        String edgeFile = baseName + PHYLOGENY_EDGE_FILE_SUFFIX;
        EdgeListMapper.writeEdgeArray(edgeFile, phylogeny);
    }

    public List<Edge> getOutgoingEdges(int sourceId) throws IOException {
        String edgeFile = baseName + EDGE_FILE_SUFFIX;
        return EdgeListMapper.getOutgoingEdges(edgeFile, sourceId, nodeIndexMapper);
    }

    /**
     * Get outgoing edges from a source node up to a maximum destination node ID.
     * 
     * @param sourceId ID of the source node
     * @param maxDestId Maximum destination node ID to consider for outgoing edges
     * @return List of outgoing edges from the source node with destination IDs less than or equal to maxDestId
     * @throws IOException if file operations fail
     */
    public List<Edge> getOutgoingEdgesUpToId(int sourceId, int maxDestId) throws IOException {
        String edgeFile = baseName + EDGE_FILE_SUFFIX;
        return EdgeListMapper.getOutgoingEdgesUpToDestId(edgeFile, sourceId, maxDestId, nodeIndexMapper);
    }

    /**
     * Remove all outgoing edges from a source node. This method updates the edge file to remove any edges where the source node matches the given ID.
     * @param sourceId ID of the source node whose outgoing edges should be removed
     * @throws IOException if file operations fail
     */
    public void removeOutgoingEdges(int sourceId) throws IOException {
        String edgeFile = baseName + EDGE_FILE_SUFFIX;
        EdgeListMapper.removeOutgoingEdges(edgeFile, sourceId, nodeIndexMapper);
    }

    /**
     * Find the minimum-weight safe edge incoming to a strongly connected component (SCC) of nodes. This method iterates over all nodes in the SCC,
     * reads their incoming edges, and uses the provided comparator to find the minimum-weight edge that does not create a cycle in the union-find structure.
     * 
     * @param baseName Base name for files
     * @param uf Union-find structure to check for cycles
     * @param sccNodes Set of node IDs in the strongly connected component
     * @param cmp Comparator to determine edge weights
     * @return The minimum-weight safe edge incoming to the SCC, or null if no such edge exists
     * @throws IOException if file operations fail
     */
    public Edge findMinSafeEdgeIncomingToSCC(String baseName, WeightedDisjointSet uf,
                                                     Set<Integer> sccNodes, Comparator<Edge> cmp) throws IOException {
        String edgeFile = baseName + "_edges.dat";

        Edge currBest = null;
        for (Integer nodeId : sccNodes) {
            Edge e = EdgeListMapper.findMinSafeEdgeInFile(edgeFile, nodeId, uf, cmp);
            if (e != null) {
                if (currBest == null || cmp.compare(e, currBest) < 0) {
                    currBest = e;
                }
            }
        }
        return currBest;
    }

    public static double getDistance(String baseName, int i, int j, boolean symmetric) throws IOException {
        double dist = EdgeListMapper.getEdgeDistance(baseName, i, j);
        if (symmetric && Double.isNaN(dist)) {
            dist = EdgeListMapper.getEdgeDistance(baseName, j, i);
        }

        return dist;
    }
}
