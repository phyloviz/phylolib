package pt.ist.phylolib.data.memorymapper;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import pt.ist.phylolib.data.tree.Edge;
import pt.ist.phylolib.command.algorithm.edmonds.WeightedDisjointSet;

/**
 * EdgeListMapper provides memory-mapped file operations for edge arrays.
 * Each array file stores the edges incident to a specific node.
 * 
 * File Format:
 * Header:
 *   [num_edges (8 bytes)]
 * 
 * Each edge entry (16 bytes total):
 *   [source_id (4 bytes), destination_id (4 bytes), distance (8 bytes)]
 */
public final class EdgeListMapper {
    
    public static final int HEADER_SIZE = 8; // num_edges (1 long)
    public static final int BYTES_PER_EDGE = 16; // 3 ints per edge
    public static final long NO_OFFSET = -1L;
    
    // Chunk size for batch memory mapping
    private static final long CHUNK_SIZE = 2 * 1024 * 1024; // 2MB chunks

    private static final int NODE_CACHE_THRESHOLD = 100000; // Threshold to clear node cache during streaming

    /**
     * Get the number of edges stored in the edge list file.
     * 
     * @param fileName Path to the edge list file
     * @return Number of edges in the file
     * @throws IOException if file operations fail
     */
    public static long getNumEdges(String fileName) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(fileName, "r");
             FileChannel channel = raf.getChannel()) {
            
            MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_ONLY, 0, HEADER_SIZE);
            mbb.order(ByteOrder.nativeOrder());
            return mbb.getLong();
        }
    }

    /**
     * Add a single edge to the memory-mapped edge array file.
     * <p>
     * The new edge is always appended at the end of the file.
     * 
     * @param edge The edge to add
     * @param fileName Path to the edge list file
     * @throws IOException if file operations fail
     */
    public static void addEdge(Edge edge, String fileName) throws IOException {
        int dest = edge.to();
        String nodeFileName = fileName.replace("_edges.dat", "");
        nodeFileName += "_edges_node" + dest + ".dat";
        
        try (RandomAccessFile raf = new RandomAccessFile(nodeFileName, "rw");
            FileChannel channel = raf.getChannel()) {
            
            // Read header to get edge count
            MappedByteBuffer headerMbb = channel.map(FileChannel.MapMode.READ_WRITE, 0, HEADER_SIZE);
            headerMbb.order(ByteOrder.nativeOrder());
            long edgeCount = headerMbb.getLong();

            // Calculate new file size and position to append
            long appendPosition = HEADER_SIZE + edgeCount * BYTES_PER_EDGE;
            long newFileSize = appendPosition + BYTES_PER_EDGE;
            raf.setLength(newFileSize);
            MappedByteBuffer edgeMbb = channel.map(FileChannel.MapMode.READ_WRITE, appendPosition, BYTES_PER_EDGE);
            edgeMbb.order(ByteOrder.nativeOrder());

            // Write the new edge
            edgeMbb.putInt(edge.from());
            edgeMbb.putInt(edge.to());
            edgeMbb.putDouble(edge.distance());
            
            // Update header with new count
            headerMbb.position(0);
            headerMbb.putLong(edgeCount + 1);
        }
    }

    /**
     * Add multiple edges for a node to the memory-mapped edge array.
     * <p>
     * All edges are appended at the end of the file.
     *
     * @param edges The list of edges to add
     * @param node The destination node for all edges
     * @param fileName The name of the edge list file
     * @throws IOException if file operations fail
     */
    public static void addEdges(List<Edge> edges, int node, String fileName) throws IOException {
        // Handle empty edge list - nothing to add
        if (edges == null || edges.isEmpty()) {
            return;
        }

        String nodeFileName = fileName.replace("_edges.dat", "");
        nodeFileName += "_edges_node" + node + ".dat";

        try (RandomAccessFile raf = new RandomAccessFile(nodeFileName, "rw");
             FileChannel channel = raf.getChannel()) {

            // Read header to get edge count
            MappedByteBuffer headerMbb = channel.map(FileChannel.MapMode.READ_WRITE, 0, HEADER_SIZE);
            headerMbb.order(ByteOrder.nativeOrder());
            long edgeCount = headerMbb.getLong();

            // Calculate new file size and position to append
            long appendPosition = HEADER_SIZE + edgeCount * BYTES_PER_EDGE;
            long totalEdgeSize = (long) edges.size() * BYTES_PER_EDGE;
            long newFileSize = appendPosition + totalEdgeSize;
            raf.setLength(newFileSize);
            
            // Write edges in chunks to avoid exceeding 2GB mapping limit
            long currentOffset = appendPosition;
            int edgesWritten = 0;
            while (edgesWritten < edges.size()) {
                long remainingEdges = edges.size() - edgesWritten;
                long remainingBytes = newFileSize - currentOffset;
                long chunkSize = Math.min(remainingBytes, CHUNK_SIZE);
                long edgesInChunk = chunkSize / BYTES_PER_EDGE;
                if (edgesInChunk > remainingEdges) {
                    edgesInChunk = remainingEdges;
                    chunkSize = edgesInChunk * BYTES_PER_EDGE;
                }
                
                MappedByteBuffer edgeMbb = channel.map(FileChannel.MapMode.READ_WRITE, currentOffset, chunkSize);
                edgeMbb.order(ByteOrder.nativeOrder());
                
                for (int i = 0; i < edgesInChunk; i++) {
                    Edge edge = edges.get(edgesWritten);
                    edgeMbb.putInt(edge.from());
                    edgeMbb.putInt(edge.to());
                    edgeMbb.putDouble(edge.distance());
                    edgesWritten++;
                }
                
                currentOffset += chunkSize;
            }
            
            // Update header with new count
            headerMbb.position(0);
            headerMbb.putLong(edgeCount + edges.size());
        }
    }
    
    /**
     * Add multiple edges for multiple nodes in a single batch operation. An edge is
     * only added to the edge array of its destination node.
     * 
     * @param nodeEdgesMap Map of node to its list of incoming edges
     * @param fileName The name of the edge list file
     * @throws IOException if file operations fail
     */
    public static void addEdgesBatch(Map<Integer, List<Edge>> nodeEdgesMap, String fileName) throws IOException {
        if (nodeEdgesMap == null || nodeEdgesMap.isEmpty()) {
            return;
        }

        for (Map.Entry<Integer, List<Edge>> entry : nodeEdgesMap.entrySet()) {
            int destNode = entry.getKey();
            List<Edge> edges = entry.getValue();
            if (edges == null || edges.isEmpty()) {
                continue; // Skip empty edge lists
            }
            addEdges(edges, destNode, fileName);
        }
    }

    /**
     * Adds edges to existing nodes' edge arrays.
     * This is used when new nodes have edges pointing TO existing nodes (e.g., in asymmetric graphs).
     * The edges are appended to the existing nodes' edge arrays.
     * 
     * @param existingNodeNewEdges Map of existing nodes to edges that should be added to their incoming edge lists
     * @param nodeFileName The name of the node index file (needed to get edge offsets)
     * @param edgeFileName The name of the edge list file
     * @throws IOException if file operations fail
     */
    public static void addEdgesToExistingNodes(Map<Integer, List<Edge>> existingNodeNewEdges, 
                                               String nodeFileName, String edgeFileName) throws IOException {
        if (existingNodeNewEdges == null || existingNodeNewEdges.isEmpty()) {
            return;
        }
        
        for (Map.Entry<Integer, List<Edge>> entry : existingNodeNewEdges.entrySet()) {
            int destNode = entry.getKey();
            List<Edge> edges = entry.getValue();
            if (edges == null || edges.isEmpty()) {
                continue; // Skip empty edge lists
            }
            addEdges(edges, destNode, edgeFileName);
        }
    }

    private static List<Edge> readEdgeArrayInChunks(FileChannel channel, long numEdges, long fileSize) throws IOException {
        List<Edge> edges = new ArrayList<>((int) numEdges);
        Map<Integer, Integer> nodeCache = new HashMap<>();
        
        long edgesRead = 0;
        long currentOffset = HEADER_SIZE;
        
        while (edgesRead < numEdges) {
            long remainingEdges = numEdges - edgesRead;
            long remainingBytes = fileSize - currentOffset;
            long chunkSize = Math.min(remainingBytes, CHUNK_SIZE);
            long edgesInChunk = chunkSize / BYTES_PER_EDGE;
            if (edgesInChunk > remainingEdges) {
                edgesInChunk = remainingEdges;
                chunkSize = edgesInChunk * BYTES_PER_EDGE;
            }
            
            MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_ONLY, currentOffset, chunkSize);
            mbb.order(ByteOrder.nativeOrder());
            
            for (int i = 0; i < edgesInChunk; i++) {
                int srcId = mbb.getInt();
                int destId = mbb.getInt();
                double weight = mbb.getDouble();
                
                int src = nodeCache.computeIfAbsent(srcId, id -> id);
                int dst = nodeCache.computeIfAbsent(destId, id -> id);
                
                edges.add((int) edgesRead, new Edge(src, dst, weight));
                edgesRead++;
            }
            
            currentOffset += chunkSize;
        }
        
        return edges;
    }

    /**
     * Stream edges from file directly to a consumer without loading all into memory.
     * This method reads edges in chunks and processes them immediately via the provided
     * consumer function, avoiding the memory overhead of storing all edges in a List.
     * 
     * @param filename Path to the edge list file
     * @param edgeConsumer Function to process each edge as it's read (e.g., insert into queue)
     * @throws RuntimeException wrapping IOException if file operations fail
     */
    public static void streamEdges(String filename, Consumer<Edge> edgeConsumer) {
        try (RandomAccessFile raf = new RandomAccessFile(filename, "r");
             FileChannel channel = raf.getChannel()) {
            
            long fileSize = channel.size();
            long numEdges = (fileSize - HEADER_SIZE) / BYTES_PER_EDGE;
            
            if (numEdges == 0) {
                return; // No edges to stream
            }
            
            if (fileSize < HEADER_SIZE) {
                throw new IOException("Invalid edge file format");
            }
            
            if ((fileSize - HEADER_SIZE) % BYTES_PER_EDGE != 0) {
                throw new IOException("Corrupted edge file: size does not align with edge record size");
            }
            
            // Stream edges in chunks without accumulating them in memory
            streamEdgesInChunks(channel, numEdges, fileSize, edgeConsumer);
            
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to stream edges from file: " + filename, e);
        }
    }
    
    /**
     * Internal method to stream edges in chunks to a consumer.
     * Uses memory-mapped I/O to read chunks of edges and immediately pass them to the consumer.
     * 
     * @param channel FileChannel to read from
     * @param numEdges Total number of edges to stream
     * @param fileSize Total file size in bytes
     * @param edgeConsumer Function to process each edge
     * @throws IOException if file operations fail
     */
    private static void streamEdgesInChunks(FileChannel channel, long numEdges, long fileSize,
                                            Consumer<Edge> edgeConsumer) throws IOException {
        Map<Integer, Integer> nodeCache = new HashMap<>();
        
        long edgesRead = 0;
        long currentOffset = HEADER_SIZE;
        
        while (edgesRead < numEdges) {
            long remainingEdges = numEdges - edgesRead;
            long remainingBytes = fileSize - currentOffset;
            long chunkSize = Math.min(remainingBytes, CHUNK_SIZE);
            long edgesInChunk = chunkSize / BYTES_PER_EDGE;
            
            if (edgesInChunk > remainingEdges) {
                edgesInChunk = remainingEdges;
                chunkSize = edgesInChunk * BYTES_PER_EDGE;
            }
            
            MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_ONLY, currentOffset, chunkSize);
            mbb.order(ByteOrder.nativeOrder());
            
            for (int i = 0; i < edgesInChunk; i++) {
                int srcId = mbb.getInt();
                int destId = mbb.getInt();
                double weight = mbb.getDouble();
                
                int src = nodeCache.computeIfAbsent(srcId, id -> id);
                int dst = nodeCache.computeIfAbsent(destId, id -> id);
                
                Edge edge = new Edge(src, dst, weight);
                
                // Immediately pass to consumer
                edgeConsumer.accept(edge);
                
                edgesRead++;
            }
            
            currentOffset += chunkSize;
            
            // Clear node cache periodically to prevent unbounded memory growth
            if (nodeCache.size() > NODE_CACHE_THRESHOLD) {
                nodeCache.clear();
            }
        }
    }

    public static List<Edge> loadEdgeArray(String filename) {
        try (RandomAccessFile raf = new RandomAccessFile(filename, "r")) {

            FileChannel channel = raf.getChannel();
            long fileSize = channel.size();
            long numEdges = (fileSize - HEADER_SIZE) / BYTES_PER_EDGE;
            
            List<Edge> edges = new ArrayList<>((int) numEdges);
            if (numEdges > Integer.MAX_VALUE) {
                throw new IOException("Edge list too large to fit in an array");
            }
            else if (numEdges == 0) {
                return edges; // empty array
            }
            else if (fileSize < HEADER_SIZE) {
                throw new IOException("Invalid edge file format");
            }
            else if ((fileSize - HEADER_SIZE) % BYTES_PER_EDGE != 0) {
                throw new IOException("Corrupted edge file: size does not align with edge record size");
            }
            return readEdgeArrayInChunks(channel, numEdges, fileSize);
        } 
        catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to load edge array from file: " + filename, e);
        }
    }

    /** This method assumes that edge arrays are stored by destination ID in ascending order */
    public static List<Edge> loadEdgeArrayUpToId(String filename, int maxDestId) {
        try (RandomAccessFile raf = new RandomAccessFile(filename, "r")) {

            FileChannel channel = raf.getChannel();
            long fileSize = channel.size();
            long numEdges = (fileSize - HEADER_SIZE) / BYTES_PER_EDGE;
            
            List<Edge> edges = new ArrayList<>();
            if (numEdges == 0) {
                return edges; // empty array
            }
            else if (fileSize < HEADER_SIZE) {
                throw new IOException("Invalid edge file format");
            }
            else if ((fileSize - HEADER_SIZE) % BYTES_PER_EDGE != 0) {
                throw new IOException("Corrupted edge file: size does not align with edge record size");
            }
            
            long edgesRead = 0;
            long currentOffset = HEADER_SIZE;
            
            while (edgesRead < numEdges) {
                MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_ONLY, currentOffset, Math.min(CHUNK_SIZE, fileSize - currentOffset));
                mbb.order(ByteOrder.nativeOrder());
                
                int edgesInChunk = mbb.remaining() / BYTES_PER_EDGE;
                for (int i = 0; i < edgesInChunk; i++) {
                    int src = mbb.getInt();
                    int dest = mbb.getInt();
                    double weight = mbb.getDouble();
                    
                    if (dest > maxDestId) {
                        return edges; // stop reading further
                    }
                    
                    edges.add(new Edge(src, dest, weight));
                    edgesRead++;
                }
                
                currentOffset += mbb.capacity();
            }
            
            return edges;
        } 
        catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to load edge array from file: " + filename, e);
        }
    }

     /**
     * Write a list of edges to a memory-mapped edge array file. This method overwrites any existing data in the file.
     * The edges are written in a single batch operation for efficiency.
     * 
     * @param filename Path to the edge list file
     * @param edges List of edges to write to the file
     * @throws IOException if file operations fail
     */
    public static void writeEdgeArray(String filename, List<Edge> edges) {
        try (RandomAccessFile raf = new RandomAccessFile(filename, "rw");
             FileChannel channel = raf.getChannel()) {
            
            long totalEdges = edges.size();
            long totalSize = HEADER_SIZE + totalEdges * BYTES_PER_EDGE;
            raf.setLength(totalSize);
            
            // Write header
            MappedByteBuffer headerMbb = channel.map(FileChannel.MapMode.READ_WRITE, 0, HEADER_SIZE);
            headerMbb.order(ByteOrder.nativeOrder());
            headerMbb.putLong(totalEdges);
            
            // Write edges in chunks
            long currentOffset = HEADER_SIZE;
            int edgesWritten = 0;
            while (edgesWritten < totalEdges) {
                long remainingEdges = totalEdges - edgesWritten;
                long remainingBytes = totalSize - currentOffset;
                long chunkSize = Math.min(remainingBytes, CHUNK_SIZE);
                long edgesInChunk = chunkSize / BYTES_PER_EDGE;
                if (edgesInChunk > remainingEdges) {
                    edgesInChunk = remainingEdges;
                    chunkSize = edgesInChunk * BYTES_PER_EDGE;
                }
                
                MappedByteBuffer edgeMbb = channel.map(FileChannel.MapMode.READ_WRITE, currentOffset, chunkSize);
                edgeMbb.order(ByteOrder.nativeOrder());
                
                for (int i = 0; i < edgesInChunk; i++) {
                    Edge edge = edges.get(edgesWritten);
                    edgeMbb.putInt(edge.from());
                    edgeMbb.putInt(edge.to());
                    edgeMbb.putDouble(edge.distance());
                    edgesWritten++;
                }
                
                currentOffset += chunkSize;
            }
        } 
        catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to write edge array to file: " + filename, e);
        }
    }

    /**
     * Decrement the edge count in the file header by the specified count.
     * 
     * @param filename Path to the edge list file
     * @param channel The file channel for the edge list file
     * @param count Number of edges to decrement
     * @throws IOException if file operations fail
     */
    private static void decrementHeader(String filename, FileChannel channel, long count) throws IOException {
        MappedByteBuffer headerMbb = channel.map(FileChannel.MapMode.READ_WRITE, 0, HEADER_SIZE);
        headerMbb.order(ByteOrder.nativeOrder());
        long edgeCount = headerMbb.getLong();
        headerMbb.rewind();
        headerMbb.putLong(edgeCount - count);
        headerMbb.force();
    }

    public static boolean edgeExists(String filename, int sourceId, int destId) throws IOException {
        String nodeFileName = filename.replace("_edges.dat", "");
        nodeFileName += "_edges_node" + destId + ".dat";

        try (RandomAccessFile raf = new RandomAccessFile(nodeFileName, "r");
            FileChannel channel = raf.getChannel()) {

            long fileSize = channel.size();
            long currentOffset = HEADER_SIZE;
            while (currentOffset < fileSize) {
                // Map a sizeable chunk to avoid excessive mappings
                int mappingSize = (int) Math.min(CHUNK_SIZE, fileSize - currentOffset);
                MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_ONLY, currentOffset, mappingSize);
                mbb.order(ByteOrder.nativeOrder());

                int edgesInChunk = mappingSize / BYTES_PER_EDGE;
                for (int j = 0; j < edgesInChunk; j++) {
                    int srcId = mbb.getInt();
                    int dstId = mbb.getInt();
                    mbb.getDouble(); // skip weight
                    if (srcId == sourceId && dstId == destId) {
                        return true;
                    }
                }
                currentOffset += mappingSize;
            }
        }
        return false;
    }

    public static void removeEdge(String filename, int sourceId, int destId) throws IOException {
        String nodeFileName = filename.replace("_edges.dat", "");
        nodeFileName += "_edges_node" + destId + ".dat";

        try (RandomAccessFile raf = new RandomAccessFile(nodeFileName, "rw");
            FileChannel channel = raf.getChannel()) {

            long fileSize = channel.size();
            long currentOffset = HEADER_SIZE;
            long edgeOffsetToRemove = -1;

            while (currentOffset < fileSize) {
                // Map a sizeable chunk to avoid excessive mappings
                int mappingSize = (int) Math.min(CHUNK_SIZE, fileSize - currentOffset);
                MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_ONLY, currentOffset, mappingSize);
                mbb.order(ByteOrder.nativeOrder());

                int edgesInChunk = mappingSize / BYTES_PER_EDGE;
                for (int j = 0; j < edgesInChunk; j++) {
                    int srcId = mbb.getInt();
                    int dstId = mbb.getInt();
                    mbb.getDouble(); // skip weight
                    if (srcId == sourceId && dstId == destId) {
                        edgeOffsetToRemove = currentOffset + j * BYTES_PER_EDGE;
                        
                        // If it is the last edge, truncate the file immediately
                        if (edgeOffsetToRemove + BYTES_PER_EDGE == fileSize) {
                            raf.setLength(edgeOffsetToRemove);
                        }
                        else { // Swap with the last edge and truncate file
                            long lastEdgeOffset = fileSize - BYTES_PER_EDGE;
                            MappedByteBuffer lastEdgeMbb = channel.map(FileChannel.MapMode.READ_ONLY, lastEdgeOffset, BYTES_PER_EDGE);
                            lastEdgeMbb.order(ByteOrder.nativeOrder());

                            // Read last edge data
                            int lastSrcId = lastEdgeMbb.getInt();
                            int lastDstId = lastEdgeMbb.getInt();
                            double lastWeight = lastEdgeMbb.getDouble();

                            // Write last edge data to the position of the edge to remove
                            MappedByteBuffer edgeToRemoveMbb = channel.map(FileChannel.MapMode.READ_WRITE, edgeOffsetToRemove, BYTES_PER_EDGE);
                            edgeToRemoveMbb.order(ByteOrder.nativeOrder());
                            edgeToRemoveMbb.putInt(lastSrcId);
                            edgeToRemoveMbb.putInt(lastDstId);
                            edgeToRemoveMbb.putDouble(lastWeight);

                            // Truncate the file
                            raf.setLength(lastEdgeOffset);
                        }
                        decrementHeader(nodeFileName, channel, 1);
                        return;
                    }
                }
                currentOffset += mappingSize;
            }
        }
    }

    private static void deleteFileIfExists(String fileName) throws IOException {
        File file = new File(fileName);
        if (file.exists()) {
            if (!file.delete()) {
                throw new IOException("Failed to delete file: " + fileName);
            }
        }
    }

    public static void removeEdges(String filename, int nodeId) throws IOException {
        String nodeFileName = filename.replace("_edges.dat", "");
        nodeFileName += "_edges" + "_node" + nodeId + ".dat";

        // Delete file
        deleteFileIfExists(nodeFileName);
    }

    /**
     * Remove all edge arrays for a set of nodes in a single batch operation.
     * 
     * @param nodeIdsToRemove Set of node IDs whose incident edges should be removed
     * @param fileName Path to the edge list file
     * @throws IOException if file operations fail
     */
    public static void removeEdgesBatch(Set<Integer> nodeIdsToRemove, String fileName) throws IOException {
        if (nodeIdsToRemove == null || nodeIdsToRemove.isEmpty()) {
            return;
        }

        // remove _edges.dat from filename
        String baseFileName = fileName.replace("_edges.dat", "");
        for (Integer nodeId : nodeIdsToRemove) {
            String nodeFileName = baseFileName + "_edges_node" + nodeId + ".dat";

            // Delete file
            try {
                deleteFileIfExists(nodeFileName);
            } catch (IOException e) {
                throw new IOException("Failed to delete node file: " + nodeFileName, e);
            }
        }
    }

    public static void removeOutgoingEdges(String filename, int sourceId, NodeIndexMapper nodeIndexMapper) throws IOException {
        // Get node map
        int[] nodeIDs = nodeIndexMapper.loadNodeIDs();

        // For each node in the map, remove edges from sourceId to that node
        for (int nodeId : nodeIDs) {
            removeEdge(filename, sourceId, nodeId);
        }
    }

    public static List<Edge> getOutgoingEdges(String filename, int sourceId, NodeIndexMapper nodeIndexMapper) throws IOException {
        // Get node map
        String edgeFile = filename;
        int[] nodeIDs = nodeIndexMapper.loadNodeIDs();

        List<Edge> edges = new ArrayList<>();
        // For node in the map
        for (int nodeId : nodeIDs) {
            if (nodeId == sourceId) continue;
            String nodeFileName = edgeFile.replace("_edges.dat", "");
            nodeFileName += "_edges_node" + nodeId + ".dat";

            List<Edge> nodeEdges = loadEdgeArray(nodeFileName);
            for (Edge edge : nodeEdges) {
                if (edge.from() == sourceId) {
                    edges.add(edge);
                }
            }
        }
        return edges;
    }

    public static List<Edge> getOutgoingEdgesUpToDestId(String filename, int sourceId, int maxDestId, NodeIndexMapper nodeIndexMapper) throws IOException {
        // Get node map
        String edgeFile = filename;
        int[] nodeIDs = nodeIndexMapper.loadNodeIDs();

        List<Edge> edges = new ArrayList<>();
        // For node in the map
        for (int nodeId : nodeIDs) {
            if (nodeId == sourceId || nodeId > maxDestId) continue;
            String nodeFileName = edgeFile.replace("_edges.dat", "");
            nodeFileName += "_edges_node" + nodeId + ".dat";

            List<Edge> nodeEdges = loadEdgeArray(nodeFileName);
            for (Edge edge : nodeEdges) {
                if (edge.from() == sourceId) {
                    edges.add(edge);
                }
            }
        }
        return edges;
    }


    /**
     * Find the minimum weight edge in the file that points to the target node and whose source is in a different strongly connected component.
     * This is used to find a safe edge to add during the CameriniForest contraction phase.
     * @param filename The edge array file to search for the minimum edge
     * @param targetId The ID of the target node for which we want to find the minimum incoming edge
     * @param uf The union-find data structure for managing strongly connected components
     * @param cmp Comparator for Edge objects used to determine which edge is smaller
     * @return The minimum weight edge satisfying the criteria, or null if none exists
     * @throws IOException if file operations fail
     */
    public static Edge findMinSafeEdgeInFile(String filename, int targetId, WeightedDisjointSet uf,
                                             Comparator<Edge> cmp) throws IOException {
        String nodeFileName = filename.replace("_edges.dat", "");
        nodeFileName += "_edges_node" + targetId + ".dat";

        Edge minEdge = null;

        try (RandomAccessFile raf = new RandomAccessFile(nodeFileName, "r");
             FileChannel channel = raf.getChannel()) {

            long fileSize = channel.size();
            if (fileSize <= HEADER_SIZE) {
                return null;
            }

            long numEdges = (fileSize - HEADER_SIZE) / BYTES_PER_EDGE;
            long edgesRead = 0;
            long currentOffset = HEADER_SIZE;

            while (edgesRead < numEdges) {
                long remainingEdges = numEdges - edgesRead;
                long remainingBytes = fileSize - currentOffset;
                long chunkSize = Math.min(remainingBytes, CHUNK_SIZE);
                long edgesInChunk = chunkSize / BYTES_PER_EDGE;
                if (edgesInChunk > remainingEdges) {
                    edgesInChunk = remainingEdges;
                    chunkSize = edgesInChunk * BYTES_PER_EDGE;
                }

                MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_ONLY, currentOffset, chunkSize);
                mbb.order(ByteOrder.nativeOrder());

                for (int i = 0; i < edgesInChunk; i++) {
                    int srcId = mbb.getInt();
                    int destId = mbb.getInt();
                    double weight = mbb.getDouble();

                    if (!uf.sameSet(srcId, destId)) {
                        Edge candidateEdge = new Edge(srcId, destId, weight);
                        if (minEdge == null || cmp.compare(candidateEdge, minEdge) < 0) {
                            minEdge = candidateEdge;
                        }
                    }
                    edgesRead++;
                }

                currentOffset += chunkSize;
            }
        }

        return minEdge;
    }
}