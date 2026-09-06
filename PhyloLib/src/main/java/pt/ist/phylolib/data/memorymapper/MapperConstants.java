package pt.ist.phylolib.data.memorymapper;

public final class MapperConstants {

    private MapperConstants() {
        // Prevent instantiation
    }

    protected static final String FILE_EXTENSION = ".dat";
    protected static final String NODE_FILE_SUFFIX = "_nodes" + FILE_EXTENSION;
    protected static final String EDGE_FILE_SUFFIX = "_edges" + FILE_EXTENSION;
    protected static final String EDGE_NODE_FILE_SUFFIX = "_edges_node";
    protected static final String PHYLOGENY_EDGE_FILE_SUFFIX = "_phylogeny_edges" + FILE_EXTENSION;
    
}
