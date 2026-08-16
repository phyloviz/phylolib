package pt.ist.phylolib.data.matrix;

public class Matrix {

    private final boolean symmetric;
    private final String[] ids;
    private final double[][] distances;
    private final IDistance distance;

    public Matrix(boolean symmetric, String[] ids) {
        this.symmetric = symmetric;
        this.ids = ids;
        this.distances = new double[ids.length][];
        this.distance = null;
    }

    public Matrix(boolean symmetric, String[] ids, IDistance distance) {
        this.symmetric = symmetric;
        this.ids = ids;
        this.distances = new double[ids.length][];
        this.distance = distance;
    }

    public Matrix(boolean symmetric, String[] ids, double[][] distances) {
        this.symmetric = symmetric;
        this.ids = ids;
        this.distances = distances;
        this.distance = null;
    }

    public String[] ids() {
        return ids;
    }

    public int size() {
        return distances.length;
    }

    protected boolean isSymmetric() {
        return symmetric;
    }

    public double distance(int i, int j) {
        if (i == j)
            return 0;
        if (symmetric) {
            int k = i;
            i = Math.max(i, j);
            j = Math.min(k, j);
        }
        if (distance != null)
            return distance.get(i, j);

        // No null check needed for primitives (default is 0.0, but array exists)
        return distances[i][j];
    }

    /**
     * Gets a distance matrix corrected according to the given correction formula.
     *
     * @param correction the correction formula to apply to each phylogenetic
     *                   distance of this matrix
     * @return a new distance matrix with the phylogenetic distances of this matrix
     * corrected
     */
    public Matrix correct(ICorrection correction) {
        // If matrix is lazy (distances is null), no change needed

        if (distance != null) {
            return new Matrix(symmetric, ids, (i, j) -> correction.get(distance.get(i, j)));
        }

        // If matrix is eager (primitive array), we must construct a new primitive array
        double[][] newDistances = new double[distances.length][];

        for (int i = 0; i < distances.length; i++) {
            if (distances[i] != null) {
                newDistances[i] = new double[distances[i].length];
                for (int j = 0; j < distances[i].length; j++) {
                    newDistances[i][j] = correction.get(distances[i][j]);
                }
            }
        }
        return new Matrix(symmetric, ids, newDistances);
    }

    /**
     * Represents a phylogenetic distance provider between two profiles.
     */
    public interface IDistance {

        /**
         * Calculates the phylogenetic distance between two given profiles.
         *
         * @param i a number identifying one profile
         * @param j a number identifying another profile
         * @return the calculated phylogenetic distance between the profiles identified
         * by i and j
         */
        double get(int i, int j);

    }

    /**
     * Represents a correction formula for a phylogenetic distance.
     */
    public interface ICorrection {

        /**
         * Corrects the given phylogenetic distance.
         *
         * @param distance the phylogenetic distance to correct
         * @return the value resultant from correcting the phylogenetic distance
         */
        double get(double distance);

    }

}
