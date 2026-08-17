package pt.ist.phylolib.data.matrix;

import pt.ist.phylolib.data.memorymapper.GraphMapper;
import java.io.IOException;

public class MemoryMappedMatrix extends Matrix {
    
    /** The base file name for matrices stored in external memory through memory mapping */
    private String baseFileName;

    private GraphMapper graphMapper;


    public MemoryMappedMatrix(boolean symmetric, String[] ids, String baseFileName, GraphMapper mapper) {
        super(symmetric, ids);
        this.baseFileName = baseFileName;
        this.graphMapper = mapper;
    }

    public String getBaseFileName() {
        return baseFileName;
    }

    public GraphMapper getGraphMapper() {
        return this.graphMapper;
    }

    @Override
    public double distance(int i, int j) {
        double dist = Double.NaN;
        try {
            dist = GraphMapper.getDistance(baseFileName, i, j, isSymmetric());
            if (isSymmetric() && dist == Double.NaN) {
                dist = GraphMapper.getDistance(baseFileName, j, i, isSymmetric());
            }
        } catch (IOException e) {
            throw new RuntimeException("Error accessing distance from memory-mapped matrix", e);
        }
        return dist;
    }

}
