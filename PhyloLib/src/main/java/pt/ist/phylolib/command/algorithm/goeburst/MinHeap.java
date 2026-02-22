package pt.ist.phylolib.command.algorithm.goeburst;

final class MinHeap {

    private final int[] heap;
    private final int[] pos;
    private final double[] key;
    private int size;

    MinHeap(double[] key) {
        this.key = key;
        int n = key.length;
        this.heap = new int[n];
        this.pos = new int[n];
        for (int i = 0; i < n; i++) {
            heap[i] = i;
            pos[i] = i;
        }
        this.size = n;
    }

    boolean isEmpty() {
        return size == 0;
    }

    int extractMin() {
        if (size == 0)
            return -1;

        int min = heap[0];
        int last = heap[size - 1];
        heap[0] = last;
        pos[last] = 0;
        size--;
        pos[min] = -1;

        if (size > 0)
            siftDown(0);

        return min;
    }

    void decreaseKey(int node) {
        int i = pos[node];
        if (i < 0)
            return;
        siftUp(i);
    }

    private void siftUp(int i) {
        while (i > 0) {
            int p = (i - 1) / 2;
            if (!less(heap[i], heap[p]))
                break;
            swap(i, p);
            i = p;
        }
    }

    private void siftDown(int i) {
        while (true) {
            int l = 2 * i + 1;
            int r = l + 1;
            int smallest = i;

            if (l < size && less(heap[l], heap[smallest]))
                smallest = l;
            if (r < size && less(heap[r], heap[smallest]))
                smallest = r;

            if (smallest == i)
                break;
            swap(i, smallest);
            i = smallest;
        }
    }

    private boolean less(int a, int b) {
        double ka = key[a];
        double kb = key[b];
        if (ka < kb)
            return true;
        if (ka > kb)
            return false;
        return a < b;
    }

    private void swap(int i, int j) {
        int ti = heap[i];
        int tj = heap[j];
        heap[i] = tj;
        heap[j] = ti;
        pos[ti] = j;
        pos[tj] = i;
    }
}
