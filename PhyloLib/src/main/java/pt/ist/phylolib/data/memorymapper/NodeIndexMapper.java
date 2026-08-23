package pt.ist.phylolib.data.memorymapper;

import pt.ist.phylolib.data.dataset.Profile;

import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

/**
 * The NodeIndexMapper class offers several methods to save and load a graph's 
 * nodes to and from a memory-mapped file.
 * <p>
 * 
 * The terms 'node' and 'profile' are used interchangeably, since a graph's node represents a genomic profile.
 * 
 * File Format:
 * 
 * Header:
 *   -  [num_nodes (4 bytes), sequence_length (4 bytes)]
 * 
 * For each node:
 *    - [node_id (4 bytes), sequence_data (sequence_length * 4 bytes)]
 * 
 */
public final class NodeIndexMapper {

    /** The size of the header in bytes
     * <p>
     * - num_nodes: 4 bytes (int)
     * - sequence_length: 4 bytes (int)
     * - Total: 8 bytes
     */
    private static final int HEADER_SIZE = 2 * Integer.BYTES; // num_nodes + sequence_length

    /** The size of a node ID in bytes (4 bytes) */
    private static final int NODE_ID_BYTES = Integer.BYTES; // 4 bytes for node ID

    /** The size of each locus in bytes (4 bytes) */
    private static final int BYTES_PER_LOCUS = Integer.BYTES;

    // Chunked mapping constants to support files > 2GB (Java MappedByteBuffer limit)
    private static final long MAX_MAPPING_SIZE = 1_500_000_000L; // 1.5GB safe limit per mapping

    /** Maps integer IDs to string IDs */
    private Map<Integer, String> idMap = new HashMap<>();

    /** Maps string IDs to integer IDs */
    private Map<String, Integer> reverseIdMap = new HashMap<>();

    /** offset to the position of a given profile in the NodeIndex memory-mapped file */
    private Map<Integer, Long> nodePositionIndex = new HashMap<>();

    /** Used to determine if the maps need to be re-serialized */
    private boolean upToDateMaps = false;

    /** List of available node IDs for reuse after profile deletion. If empty, new IDs are generated
     * by incrementing the maximum existing ID.
     */
    private List<Integer> freeNodeIds = new ArrayList<>();

    /** Path to the memory-mapped file containing the node index */
    private String nodeIndexFile;

    /** Base file name for the auxiliary structures serialized files */
    private String auxiliaryDataBaseFileName;

    /**
     * Constructor for the NodeIndexMapper.
     *
     * @param file Path to the memory-mapped file containing the node index
     */
    public NodeIndexMapper(String file) {
        this.nodeIndexFile = file;

        // remove file extension for auxiliary data files
        int dotIndex = file.lastIndexOf('.');
        if (dotIndex != -1) {
            this.auxiliaryDataBaseFileName = file.substring(0, dotIndex);
        } else {
            this.auxiliaryDataBaseFileName = file;
        }

        // Check if nodeIndexFile exists
        try (RandomAccessFile raf = new RandomAccessFile(nodeIndexFile, "r")) {
            // File exists, deserialize auxiliary data
            deserializeState(
                auxiliaryDataBaseFileName + "_idMaps.ser",
                auxiliaryDataBaseFileName + "_offsetMap.ser"
            );
        } catch (IOException e) {
            // DO NOTHING
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Failed to deserialize auxiliary data: " + e.getMessage(), e);
        }

        upToDateMaps = true; // Assume deserialized maps are up to date
    }

    /**
     * Constructor for the NodeIndexMapper that also saves the provided profiles to a memory-mapped file. This constructor is useful for initializing the mapper with a new set of profiles and creating the corresponding memory-mapped file in one step.
     * @param file Path to the memory-mapped file to create and save the profiles to
     * @param profiles List of profiles to save to the memory-mapped file
     * @param sequenceLength Fixed length of sequence data for all nodes (number of elements in the sequence)
     * @throws IOException
     */
    public NodeIndexMapper(String file, List<Profile> profiles, int sequenceLength) throws IOException {
        this.nodeIndexFile = file;
        this.auxiliaryDataBaseFileName = file.substring(0, file.lastIndexOf('.'));
        saveGraph(profiles, sequenceLength);
    }

    /**
     * Calculate which region a node belongs to based on entry size.
     */
    private long getNodePosition(int nodeIndex, int entrySize) {
        return HEADER_SIZE + (long)nodeIndex * entrySize;
    }
    
    /**
     * Calculate the start position and size for mapping a chunk of nodes.
     */
    private long[] getChunkBounds(int startNodeIndex, int numNodesToMap, int entrySize, long fileSize) {
        long startPos = getNodePosition(startNodeIndex, entrySize);
        long maxSize = (long)numNodesToMap * entrySize;
        long actualSize = Math.min(maxSize, fileSize - startPos);
        return new long[]{startPos, actualSize};
    }

    /**
     * Helper method to convert Profile sequence to bytes based on its sequence type.
     */
    private byte[] sequenceToBytes(Profile profile) {
        int schemaLength = profile.size();
        byte[] bytes = new byte[schemaLength * BYTES_PER_LOCUS];

        for (int i = 0; i < schemaLength; i++) {
            Integer locusValue = profile.locus(i);
            int offset = i * BYTES_PER_LOCUS;
            if (locusValue == null) {
                // Represent missing data as -1 (0xFFFFFFFF)
                bytes[offset] = (byte) 0xFF;
                bytes[offset + 1] = (byte) 0xFF;
                bytes[offset + 2] = (byte) 0xFF;
                bytes[offset + 3] = (byte) 0xFF;
            } else {
                // Write the integer value in big-endian format
                bytes[offset] = (byte) (locusValue >> 24);
                bytes[offset + 1] = (byte) (locusValue >> 16);
                bytes[offset + 2] = (byte) (locusValue >> 8);
                bytes[offset + 3] = (byte) locusValue.intValue();
            }
        }
        
        return bytes;
    }

    /**
     * Helper method to create a Profile from raw byte data.
     */
    private Profile createNodeFromBytes(byte[] bytes, int profileId, int schemaLength) {
        Integer[] loci = new Integer[schemaLength];
        
        for (int i = 0; i < schemaLength; i++) {
            int offset = i * BYTES_PER_LOCUS;
            int value = ((bytes[offset] & 0xFF) << 24) |
                        ((bytes[offset + 1] & 0xFF) << 16) |
                        ((bytes[offset + 2] & 0xFF) << 8) |
                        (bytes[offset + 3] & 0xFF);
            if (value == -1) {
                loci[i] = null; // Missing data
            } else {
                loci[i] = value;
            }
        }
        
        return new Profile(idMap.get(profileId), loci);
    } 

    /**
     * Save graph's profiles to a memory-mapped file.
     * 
     * @param profiles List of profiles to save
     * @param sequenceLength Fixed length of sequence data for all nodes (number of elements)
     * @throws IOException if file operations fail
     */
    public void saveGraph(List<Profile> profiles, int sequenceLength) throws IOException {        
        // Calculate file size: header + entries
        // Each entry: node_id (4 bytes) + mlst_data
        // int entrySize = NODE_ID_BYTES + mlstLength * bytesPerElement;
        int entrySize = NODE_ID_BYTES + sequenceLength * BYTES_PER_LOCUS;
        long fileSize = HEADER_SIZE + (long) profiles.size() * entrySize;
        
        try (RandomAccessFile raf = new RandomAccessFile(nodeIndexFile, "rw");
             FileChannel channel = raf.getChannel()) {
            
            raf.setLength(fileSize);
            
            // Write header
            MappedByteBuffer headerBuf = channel.map(FileChannel.MapMode.READ_WRITE, 0, HEADER_SIZE);
            headerBuf.order(ByteOrder.nativeOrder());
            headerBuf.putInt(profiles.size());     // num_nodes
            headerBuf.putInt(sequenceLength);       // sequence_length (number of elements)
            
            // Write profiles in chunks to avoid exceeding 2GB limit
            int profilesPerChunk = (int)(MAX_MAPPING_SIZE / entrySize);
            if (profilesPerChunk == 0) profilesPerChunk = 1; // Handle extremely large entries
            
            for (int i = 0; i < profiles.size(); i += profilesPerChunk) {
                int endIdx = Math.min(i + profilesPerChunk, profiles.size());
                int chunkSize = endIdx - i;
                
                long[] bounds = getChunkBounds(i, chunkSize, entrySize, fileSize);
                long position = bounds[0];
                long size = bounds[1];
                
                MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_WRITE, position, size);
                mbb.order(ByteOrder.nativeOrder());
                
                // Write data for nodes in this chunk
                for (int j = i; j < endIdx; j++) {
                    Profile profile = profiles.get(j);
                    String profileId = profile.id();
                    int nodeId = generateIntegerID(profileId); // Generate a new node ID (this method should ensure uniqueness)

                    // Update offset map
                    nodePositionIndex.put(nodeId, position + (long)(j - i) * entrySize);
                    
                    // Write node ID
                    mbb.putInt(nodeId);
                    
                    // Write sequence data
                    byte[] sequenceBytes = sequenceToBytes(profile);
                    mbb.put(sequenceBytes);
                }
                
                mbb.force();
            }

            serializeState(
                auxiliaryDataBaseFileName + "_idMaps.ser",
                auxiliaryDataBaseFileName + "_offsetMap.ser"
            );
        }
    }

    // /**
    //  * Save graph using Graph object.
    //  * 
    //  * Deprecated method kept around for the unit tests.
    //  * 
    //  * @param graph Graph to save
    //  * @param sequenceLength Fixed length of sequence data
    //  * @param fileName Path to output file
    //  * @throws IOException if file operations fail
    //  */
    // public static void saveGraph(Graph graph, int sequenceLength, String fileName) throws IOException {
    //     saveGraph(graph.getNodes(), sequenceLength, fileName);
    // }
    
    
    /**
     * Get the number of nodes stored in the memory-mapped file.
     * 
     * @return Number of nodes
     * @throws IOException if file operations fail
     */
    public int getNumNodes() throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(nodeIndexFile, "r");
             FileChannel channel = raf.getChannel()) {
            
            if (channel.size() < HEADER_SIZE) {
                throw new IOException("Invalid file format: file too small for header");
            }
            
            MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_ONLY, 0, Integer.BYTES);
            mbb.order(ByteOrder.nativeOrder());
            return mbb.getInt();
        }
    }

    public int[] loadNodeIDs() throws IOException {
        // Get the number of nodes from the header
        int numNodes = getNumNodes();
        int[] nodeIDs = new int[numNodes];

        try (RandomAccessFile raf = new RandomAccessFile(nodeIndexFile, "r");
             FileChannel channel = raf.getChannel()) {
            
            if (channel.size() < HEADER_SIZE) {
                throw new IOException("Invalid file format: file too small for header");
            }
            
            // Read header to get sequence length and type (to calculate entry size)
            MappedByteBuffer headerBuf = channel.map(FileChannel.MapMode.READ_ONLY, 0, HEADER_SIZE);
            headerBuf.order(ByteOrder.nativeOrder());
            int numNodesFromHeader = headerBuf.getInt();
            int sequenceLength = headerBuf.getInt();
            
            int entrySize = NODE_ID_BYTES + sequenceLength * BYTES_PER_LOCUS;
            
            // Read node IDs in chunks
            int nodesPerChunk = (int)(MAX_MAPPING_SIZE / entrySize);
            if (nodesPerChunk == 0) nodesPerChunk = 1;
            
            long fileSize = channel.size();
            
            for (int i = 0; i < numNodesFromHeader; i += nodesPerChunk) {
                int endIdx = Math.min(i + nodesPerChunk, numNodesFromHeader);
                int chunkSize = endIdx - i;
                
                long[] bounds = getChunkBounds(i, chunkSize, entrySize, fileSize);
                long position = bounds[0];
                long size = bounds[1];
                
                MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_ONLY, position, size);
                mbb.order(ByteOrder.nativeOrder());
                
                for (int j = 0; j < chunkSize; j++) {
                    nodeIDs[i + j] = mbb.getInt(j * entrySize); // Read only the node ID
                }
            }
        }
        return nodeIDs;
    }

    /**
     * Load all profiles from the memory-mapped file.
     *
     * @return List of profiles in file order
     * @throws IOException if file operations fail
     */
    public List<Profile> loadProfiles() throws IOException {
        List<Profile> profiles = new ArrayList<>();

        try (RandomAccessFile raf = new RandomAccessFile(nodeIndexFile, "r");
             FileChannel channel = raf.getChannel()) {

            if (channel.size() < HEADER_SIZE) {
                return profiles;
            }

            MappedByteBuffer headerMbb = channel.map(FileChannel.MapMode.READ_ONLY, 0, HEADER_SIZE);
            headerMbb.order(ByteOrder.nativeOrder());
            int numNodes = headerMbb.getInt();
            int sequenceLength = headerMbb.getInt();

            int entrySize = NODE_ID_BYTES + sequenceLength * BYTES_PER_LOCUS;
            int nodesPerChunk = (int)(MAX_MAPPING_SIZE / entrySize);
            if (nodesPerChunk == 0) nodesPerChunk = 1;

            long fileSize = channel.size();

            for (int i = 0; i < numNodes; i += nodesPerChunk) {
                int endIdx = Math.min(i + nodesPerChunk, numNodes);
                int chunkSize = endIdx - i;

                long[] bounds = getChunkBounds(i, chunkSize, entrySize, fileSize);
                long position = bounds[0];
                long size = bounds[1];

                MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_ONLY, position, size);
                mbb.order(ByteOrder.nativeOrder());

                for (int j = 0; j < chunkSize; j++) {
                    profiles.add(readProfile(mbb, sequenceLength));
                }
            }
        }

        return profiles;
    }

    /**
     * Read the sequence length from the file header.
     *
     * @return the sequence length
     * @throws IOException if file operations fail
     */
    public int getSequenceLength() throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(nodeIndexFile, "r");
             FileChannel channel = raf.getChannel()) {
            if (channel.size() < HEADER_SIZE) {
                throw new IOException("Invalid file format: file too small for header");
            }
            MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_ONLY, 0, HEADER_SIZE);
            mbb.order(ByteOrder.nativeOrder());
            mbb.getInt(); // skip numNodes
            return mbb.getInt(); // sequenceLength
        }
    }

    // /**
    //  * Load graph node IDs from memory-mapped file.
    //  * 
    //  * @param fileName Path to the node data file
    //  * @return Map of node ID to Node object
    //  * @throws IOException if file operations fail
    //  */
    // public static Map<Integer, Node> loadNodes(String fileName) throws IOException {
    //     Map<Integer, Node> nodeMap = new HashMap<>();
        
    //     try (RandomAccessFile raf = new RandomAccessFile(fileName, "r");
    //          FileChannel channel = raf.getChannel()) {
            
    //         long fileSize = channel.size();
    //         if (fileSize < HEADER_SIZE) {
    //             throw new IOException("Invalid file format: file too small for header");
    //         }
            
    //         // Read header
    //         MappedByteBuffer headerBuf = channel.map(FileChannel.MapMode.READ_ONLY, 0, HEADER_SIZE);
    //         headerBuf.order(ByteOrder.nativeOrder());
    //         int numNodes = headerBuf.getInt();
    //         int mlstLength = headerBuf.getInt();
    //         byte sequenceType = headerBuf.get();
            
    //         int bytesPerElement = (sequenceType == SEQUENCE_TYPE_NUCLEOTIDE_PROFILE) ? 1 : Long.BYTES;
    //         int entrySize = NODE_ID_BYTES + mlstLength * bytesPerElement;
            
    //         // Read nodes in chunks to avoid exceeding 2GB limit
    //         int nodesPerChunk = (int)(MAX_MAPPING_SIZE / entrySize);
    //         if (nodesPerChunk == 0) nodesPerChunk = 1;
            
    //         for (int i = 0; i < numNodes; i += nodesPerChunk) {
    //             int endIdx = Math.min(i + nodesPerChunk, numNodes);
    //             int chunkSize = endIdx - i;
                
    //             long[] bounds = getChunkBounds(i, chunkSize, entrySize, fileSize);
    //             long position = bounds[0];
    //             long size = bounds[1];
                
    //             MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_ONLY, position, size);
    //             mbb.order(ByteOrder.nativeOrder());
                
    //             // Read data for each node entry in this chunk
    //             for (int j = i; j < endIdx; j++) {
    //                 // Read node ID
    //                 int nodeId = mbb.getInt();
                    
    //                 // Read MLST data
    //                 byte[] mlstBytes = new byte[mlstLength * bytesPerElement];
    //                 mbb.get(mlstBytes);
                    
    //                 // Create node from the data
    //                 nodeMap.put(nodeId, createNodeFromBytes(mlstBytes, sequenceType, nodeId, mlstLength));
    //             }
    //         }
    //     }
        
    //     return nodeMap;
    // }

    /**
     * Write a single profile to the memory-mapped file at the current position.
     * @param profile The profile to write
     * @param mbb The MappedByteBuffer positioned at the correct location for writing this profile's data
     */
    private void writeProfile(Profile profile, int nodeId, MappedByteBuffer mbb) {
        // Write node ID
        mbb.putInt(nodeId);
        
        // Write sequence data
        byte[] sequenceBytes = sequenceToBytes(profile);
        mbb.put(sequenceBytes);
    }

    /**
     * Add multiple profiles to the memory-mapped file in a single batch operation.
     * 
     * @param profiles List of profiles to add
     * @param sequenceLength Fixed length of sequence data
     * @throws IOException if file operations fail
     */
    public void addNodesBatch(List<Profile> profiles, int sequenceLength) throws IOException {
        if (profiles == null || profiles.isEmpty()) {
            return;
        }
        
        try (RandomAccessFile raf = new RandomAccessFile(nodeIndexFile, "rw");
             FileChannel channel = raf.getChannel()) {
            
            // Update num_nodes in header
            MappedByteBuffer headerMbb = channel.map(FileChannel.MapMode.READ_WRITE, 0, Integer.BYTES);
            headerMbb.order(ByteOrder.nativeOrder());
            int currentNumNodes = headerMbb.getInt();
            headerMbb.position(0);
            headerMbb.putInt(currentNumNodes + profiles.size());
            
            int bytesPerElement = BYTES_PER_LOCUS;
            
            // Calculate entry size and total size needed
            int entrySize = NODE_ID_BYTES + sequenceLength * bytesPerElement;
            long position = channel.size();
            long totalSize = (long) profiles.size() * entrySize;
            
            // Pre-allocate file space to avoid filesystem reallocation overhead
            raf.setLength(position + totalSize);
            
            // Write profiles in chunks to avoid exceeding 2GB limit
            int profilesPerChunk = (int)(MAX_MAPPING_SIZE / entrySize);
            if (profilesPerChunk == 0) profilesPerChunk = 1;
            
            for (int i = 0; i < profiles.size(); i += profilesPerChunk) {
                int endIdx = Math.min(i + profilesPerChunk, profiles.size());
                int chunkSize = endIdx - i;
                long chunkBytes = (long)chunkSize * entrySize;
                
                MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_WRITE, 
                                                    position + (long)i * entrySize, 
                                                    chunkBytes);
                mbb.order(ByteOrder.nativeOrder());
                
                // Write profiles in this chunk
                for (int j = i; j < endIdx; j++) {
                    int nodeId = generateIntegerID(profiles.get(j).id());
                    currentNumNodes++;
                    nodePositionIndex.put(nodeId, position + (long)(j - i) * entrySize);
                    writeProfile(profiles.get(j), nodeId, mbb);
                }
            }
        }
        upToDateMaps = false; // Mark maps as not up to date since we've added nodes
    }
    
    /**
     * Add a single profile to the memory-mapped file.
     * 
     * @param profile The profile to add
     * @param sequenceLength Fixed length of sequence data
     * @throws IOException if file operations fail
     */
    public void addNode(Profile profile, int sequenceLength) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(nodeIndexFile, "rw");
             FileChannel channel = raf.getChannel()) {
            
            // Update num_nodes in header
            MappedByteBuffer headerMbb = channel.map(FileChannel.MapMode.READ_WRITE, 0, Integer.BYTES);
            headerMbb.order(ByteOrder.nativeOrder());
            int currentNumNodes = headerMbb.getInt();
            headerMbb.position(0);
            headerMbb.putInt(currentNumNodes + 1);

            int bytesPerElement = BYTES_PER_LOCUS;
            
            // Append node entry at the end of file: node_id + mlst_data
            long position = channel.size();
            int entrySize = NODE_ID_BYTES + sequenceLength * bytesPerElement;
            MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_WRITE, position, entrySize);
            mbb.order(ByteOrder.nativeOrder());
            
            int nodeId = generateIntegerID(profile.id()); // Generate a new node ID
            writeProfile(profile, nodeId, mbb);
            currentNumNodes++;
        }
        upToDateMaps = false; // Mark maps as not up to date since we've added a node
    }

    /**
     * Get the in-memory index of node ID to file position (byte offset).
     * 
     * @return Map of node ID to byte position in file where that node's data starts
     */
    public Map<Integer, Long> getNodePositionIndex() {
        if (nodePositionIndex == null || nodePositionIndex.isEmpty()) {
            try {
                nodePositionIndex = buildNodePositionIndex();
            } catch (IOException e) {
                throw new RuntimeException("Failed to build node position index: " + e.getMessage(), e);
            }
        }
        return new HashMap<>(nodePositionIndex);
    }

    /**
     * Build a complete in-memory index of node ID to file position (byte offset).
     * This index can be reused for multiple update operations to avoid repeated file scans.
     * 
     * @return Map of node ID to byte position in file where that node's data starts
     * @throws IOException if file operations fail
     */
    public Map<Integer, Long> buildNodePositionIndex() throws IOException {
        Map<Integer, Long> index = new HashMap<>();
        
        try (RandomAccessFile raf = new RandomAccessFile(nodeIndexFile, "r");
             FileChannel channel = raf.getChannel()) {
            
            if (channel.size() < HEADER_SIZE) {
                throw new IOException("Invalid file format: file too small for header");
            }
            
            // Read header
            MappedByteBuffer headerMbb = channel.map(FileChannel.MapMode.READ_ONLY, 0, HEADER_SIZE);
            headerMbb.order(ByteOrder.nativeOrder());
            int numNodes = headerMbb.getInt();
            int sequenceLength = headerMbb.getInt();
            
            int bytesPerElement = BYTES_PER_LOCUS;
            int entrySize = NODE_ID_BYTES + sequenceLength * bytesPerElement;
            
            // Read nodes in chunks to avoid exceeding 2GB limit
            int nodesPerChunk = (int)(MAX_MAPPING_SIZE / entrySize);
            if (nodesPerChunk == 0) nodesPerChunk = 1;
            
            long fileSize = channel.size();
            
            for (int i = 0; i < numNodes; i += nodesPerChunk) {
                int endIdx = Math.min(i + nodesPerChunk, numNodes);
                int chunkSize = endIdx - i;
                
                long[] bounds = getChunkBounds(i, chunkSize, entrySize, fileSize);
                long chunkStartPosition = bounds[0];
                long size = bounds[1];
                
                MappedByteBuffer dataMbb = channel.map(FileChannel.MapMode.READ_ONLY, chunkStartPosition, size);
                dataMbb.order(ByteOrder.nativeOrder());
                
                // Read node IDs and build index
                for (int j = i; j < endIdx; j++) {
                    int offsetInBuffer = (j - i) * entrySize;

                    int nodeId = dataMbb.getInt(offsetInBuffer);
                    long filePosition = chunkStartPosition + offsetInBuffer;
                    index.put(nodeId, filePosition);
                }
            }
        }
        
        return index;
    }

    /**
     * Read a single profile from the memory-mapped file at the current position.
     * @param mbb The MappedByteBuffer positioned at the correct location for reading this profile's data
     * @return The Profile object read from the buffer
     */
    private Profile readProfile(MappedByteBuffer mbb, int sequenceLength) {
        int nodeId = readNodeId(mbb, sequenceLength, false); // Read node ID without advancing position (we'll read sequence data separately)
        Integer[] sequenceData = readSequenceData(mbb, sequenceLength);

        return new Profile(idMap.get(nodeId), sequenceData); // Placeholder: sequence data will be read separately based on header info
    }

    /**
     * Read sequence data from the memory-mapped buffer.
     * 
     * @param mbb The memory-mapped buffer
     * @param sequenceLength The length of the sequence
     * @return An array of integers representing the sequence data
     */
    private Integer[] readSequenceData(MappedByteBuffer mbb, int sequenceLength) {
        Integer[] sequenceData = new Integer[sequenceLength];
        for (int i = 0; i < sequenceLength; i++) {
            sequenceData[i] = mbb.getInt();
        }
        return sequenceData;
    }

    /**
     * Read a node ID from the memory-mapped buffer.
     * 
     * @param mbb The memory-mapped buffer
     * @param sequenceLength The length of the sequence
     * @param advancePosition Whether to advance the buffer position to skip the sequence data after reading the node ID
     * @return The node ID
     */
    private int readNodeId(MappedByteBuffer mbb, int sequenceLength, boolean advancePosition) {
        int nodeId = mbb.getInt();

        if (advancePosition) {
            // skip sequence data
            int bytesPerElement = BYTES_PER_LOCUS;
            mbb.position(mbb.position() + sequenceLength * bytesPerElement);
        }
        return nodeId;
    }

    /**
     * Remove a profile from the memory-mapped file by replacing it with the last entry.
     * 
     * @param profile The profile to remove
     * @throws IOException if file operations fail
     */
    public void removeNode(Profile profile) throws IOException {
        String profileId = profile.id();
        try (RandomAccessFile raf = new RandomAccessFile(nodeIndexFile, "rw");
             FileChannel channel = raf.getChannel()) {

            if (channel.size() < HEADER_SIZE) {
                throw new IOException("Invalid file format: file too small for header");
            }
            
            // Read header
            MappedByteBuffer headerMbb = channel.map(FileChannel.MapMode.READ_ONLY, 0, HEADER_SIZE);
            headerMbb.order(ByteOrder.nativeOrder());
            int numNodes = headerMbb.getInt();
            int sequenceLength = headerMbb.getInt();
            
            int bytesPerElement = BYTES_PER_LOCUS;
            int entrySize = NODE_ID_BYTES + sequenceLength * bytesPerElement;

            int nodeIdToRemove = reverseIdMap.get(profileId);
            
            // Find the position of the node to remove
            long removePosition = -1;
            long position = HEADER_SIZE;
            int entryIndex = 0;
            
            for (int i = 0; i < numNodes; i++) {
                if (position + entrySize > channel.size()) {
                    break;
                }
                
                MappedByteBuffer entryMbb = channel.map(FileChannel.MapMode.READ_ONLY, position, Integer.BYTES);
                entryMbb.order(ByteOrder.nativeOrder());
                int currentNodeId = entryMbb.getInt();
                
                if (currentNodeId == nodeIdToRemove) {
                    removePosition = position;
                    entryIndex = i;
                    break;
                }
                
                position += entrySize;
            }
            
            if (removePosition == -1) {
                throw new IOException("Node ID " + nodeIdToRemove + " not found in file");
            }

            // If not the last entry, copy the last entry into this position
            if (entryIndex != numNodes - 1) {
                long lastNodePosition = HEADER_SIZE + (long) (numNodes - 1) * entrySize;

                MappedByteBuffer lastNodeMbb = channel.map(FileChannel.MapMode.READ_ONLY, lastNodePosition, entrySize);
                MappedByteBuffer removeNodeMbb = channel.map(FileChannel.MapMode.READ_WRITE, removePosition, entrySize);

                byte[] lastNodeData = new byte[entrySize];
                lastNodeMbb.get(lastNodeData);
                removeNodeMbb.put(lastNodeData);
                removeNodeMbb.force();
            }

            // Truncate the file to remove the last node entry
            channel.truncate(channel.size() - entrySize);

            // Update num_nodes in header
            headerMbb = channel.map(FileChannel.MapMode.READ_WRITE, 0, Integer.BYTES);
            headerMbb.order(ByteOrder.nativeOrder());
            headerMbb.position(0);
            headerMbb.putInt(numNodes - 1);
            headerMbb.force();
        }
        upToDateMaps = false; // Mark maps as not up to date since we've removed a node
    }

    /**
     * Remove multiple nodes from the memory-mapped file in a single batch operation.
     * 
     * @param profiles List of profiles to remove
     * @throws IOException if file operations fail
     */
    public void removeNodesBatch(List<Profile> profiles) throws IOException {
        if (profiles == null || profiles.isEmpty()) {
            return;
        }
        
        // Create a set of node IDs for faster lookup
        Set<Integer> nodeIdsToRemove = new HashSet<>();
        for (Profile profile : profiles) {
            nodeIdsToRemove.add(reverseIdMap.get(profile.id()));
        }
        
        try (RandomAccessFile raf = new RandomAccessFile(nodeIndexFile, "rw");
             FileChannel channel = raf.getChannel()) {
            
            if (channel.size() < HEADER_SIZE) {
                throw new IOException("Invalid file format: file too small for header");
            }
            
            // Read header
            MappedByteBuffer headerMbb = channel.map(FileChannel.MapMode.READ_ONLY, 0, HEADER_SIZE);
            headerMbb.order(ByteOrder.nativeOrder());
            int numNodes = headerMbb.getInt();
            int sequenceLength = headerMbb.getInt();
            
            int bytesPerElement = BYTES_PER_LOCUS;
            int entrySize = NODE_ID_BYTES + sequenceLength * bytesPerElement;
            
            // Calculate total data size
            long dataSize = channel.size() - HEADER_SIZE;
            
            // Map the entire data region
            MappedByteBuffer dataMbb = channel.map(FileChannel.MapMode.READ_WRITE, HEADER_SIZE, dataSize);
            dataMbb.order(ByteOrder.nativeOrder());
            
            // Compact the file by moving entries that should be kept
            int writeIndex = 0;  // Index in the buffer where we write the next kept entry
            int removedCount = 0;
            
            byte[] entryBuffer = new byte[entrySize];
            
            for (int i = 0; i < numNodes; i++) {
                int readOffset = i * entrySize;
                
                // Read the node ID
                dataMbb.position(readOffset);
                int nodeId = dataMbb.getInt();
                
                // Check if this node should be removed
                if (nodeIdsToRemove.contains(nodeId)) {
                    removedCount++;
                    continue;  // Skip this entry
                }
                
                // If write position differs from read position, we need to move the entry
                if (writeIndex != i) {
                    // Read the entire entry
                    dataMbb.position(readOffset);
                    dataMbb.get(entryBuffer);
                    
                    // Write it to the correct position
                    dataMbb.position(writeIndex * entrySize);
                    dataMbb.put(entryBuffer);
                }
                
                writeIndex++;
            }
            
            dataMbb.force();
            
            // Truncate the file to remove unused space
            long newSize = HEADER_SIZE + (long) (numNodes - removedCount) * entrySize;
            channel.truncate(newSize);
            
            // Update num_nodes in header
            headerMbb = channel.map(FileChannel.MapMode.READ_WRITE, 0, Integer.BYTES);
            headerMbb.order(ByteOrder.nativeOrder());
            headerMbb.position(0);
            headerMbb.putInt(numNodes - removedCount);
            headerMbb.force();
        }
        upToDateMaps = false; // Mark maps as not up to date since we've removed nodes
    }

    /**
     * Convert an integer ID to its corresponding string ID.
     * @param id The integer ID to convert
     * @return The string ID corresponding to the given integer ID
     */
    protected String integerIDToStrID(int id) {
        return idMap.get(id);
    }

    /** 
     * Convert a string ID to its corresponding integer ID.
     * @param id The string ID to convert
     * @return The integer ID corresponding to the given string ID
     */
    protected int strIDToIntegerID(String id) {
        return reverseIdMap.get(id);
    }

    /**
     * Store a mapping between an integer ID and a string ID in both maps. This method checks for existing mappings to prevent duplicates and ensures that the maps remain consistent. If the mapping is successfully stored, it marks the maps as not up to date, indicating that they need to be serialized to disk to persist the changes.
     * @param id The integer ID to store
     * @param strId The string ID to store
     * @return true if the mapping was successfully stored, false if either ID already exists in the maps
     */
    protected boolean storeIDPair(int id, String strId) {
        if (idMap.containsKey(id) || reverseIdMap.containsKey(strId)) {
            return false; // ID already exists
        }
        idMap.put(id, strId);
        reverseIdMap.put(strId, id);
        upToDateMaps = false;
        return true;
    }

    /**
     * Generate a new integer ID for a given string ID. If the string ID already exists, return the existing integer ID. If not, generate a new integer ID, store the mapping, and return it. This method also checks the freeNodeIds list to reuse IDs from removed nodes before generating new incremental IDs.
     * @param id The string ID for which to generate an integer ID
     * @return The integer ID corresponding to the given string ID
     */
    private int generateIntegerID(String id) {
        int newId;
        if (reverseIdMap.containsKey(id)) {
            return reverseIdMap.get(id); // ID already exists, return it
        }
        else if (!freeNodeIds.isEmpty()) {
            newId = freeNodeIds.remove(freeNodeIds.size() - 1);
        } 
        else {
            newId = idMap.size() + 1; // Simple incremental ID generation
        }
        storeIDPair(newId, id);
        return newId;
    } 

    /**
     * Serialize the ID maps to disk for persistence. This should be called after all updates to the maps are done. This method
     * also serializes the freeNodeIDs list to ensure that the state of available IDs is preserved across sessions.
     * @param fileName Path to the file where the maps should be saved
     * @throws IOException
     */
    protected void serializeIDMaps(String fileName) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(fileName))) {
            oos.writeObject(idMap);
            oos.writeObject(reverseIdMap);
            oos.writeObject(freeNodeIds);
            upToDateMaps = true;
        }
    }

    /**
     * Deserialize the ID maps from disk. This should be called during initialization to load the existing mappings. This method
     * also loads the freeNodeIDs list to restore the state of available IDs.
     * @param fileName Path to the file where the maps are saved
     * @throws IOException
     * @throws ClassNotFoundException
     */
    @SuppressWarnings("unchecked")
    protected void deserializeIDMaps(String fileName) throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(fileName))) {
            idMap = (Map<Integer, String>) ois.readObject();
            reverseIdMap = (Map<String, Integer>) ois.readObject();
            freeNodeIds = (List<Integer>) ois.readObject();
            upToDateMaps = true;
        }
    }

    /**
     * Serialize the node position index to disk for persistence.
     * @param fileName Path to the file where the index should be saved
     * @throws IOException
     */
    protected void serializeOffsetMap(String fileName) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(fileName))) {
            oos.writeObject(nodePositionIndex);
        }
    }

    /**
     * Deserialize the node position index from disk.
     * @param fileName Path to the file where the index is saved
     * @throws IOException
     * @throws ClassNotFoundException
     */
    @SuppressWarnings("unchecked")
    protected void deserializeOffsetMap(String fileName) throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(fileName))) {
            nodePositionIndex = (Map<Integer, Long>) ois.readObject();
        }
    }

    /**
     * Serialize the state of the node index mapper to disk for persistence.
     * @param idMapFile Path to the file where the ID maps should be saved
     * @param offsetMapFile Path to the file where the offset map should be saved
     * @throws IOException
     */
    protected void serializeState(String idMapFile, String offsetMapFile) throws IOException {
        serializeIDMaps(idMapFile);
        serializeOffsetMap(offsetMapFile);
    }

    /** 
     * Deserialize the state of the node index mapper from disk. This should be called during initialization to restore the state of the mapper.
     * @param idMapFile Path to the file where the ID maps are saved
     * @param offsetMapFile Path to the file where the offset map is saved
     * @throws IOException
     * @throws ClassNotFoundException
     */
    private void deserializeState(String idMapFile, String offsetMapFile) throws IOException, ClassNotFoundException {
        deserializeIDMaps(idMapFile);
        deserializeOffsetMap(offsetMapFile);
    }
}