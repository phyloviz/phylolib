package pt.ist.phylolib.command.algorithm.edmonds;

import pt.ist.phylolib.data.tree.Edge;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;

final class EdgeNode implements Serializable, Comparable<EdgeNode> {

	private final Edge edge;
	private final List<EdgeNode> children;

	private EdgeNode parent;

	/** Flag to mark nodes that should be removed during expansion phase */
	private boolean remove;

	private EdgeNode initialParent;

	EdgeNode(Edge edge) {
		this.edge = edge;
		this.children = new LinkedList<>();
		this.remove = false;
		this.parent = null;
		this.initialParent = null;
	}

	void addChild(EdgeNode node) {
		children.add(node);
	}

    public void clearChildren() {
        this.children.clear();
    }

	boolean isRoot() {
		return parent == null;
	}

    /** Checks if the node is a leaf node. */
    public boolean isLeaf() {
        return children == null || children.isEmpty();
    }

	Edge getEdge() {
		return edge;
	}

	EdgeNode getParent() {
		return parent;
	}

	void setParent(EdgeNode node) {
		if (initialParent == null)
			initialParent = node;
		parent = node;
	}

	List<EdgeNode> getChildren() {
		return children;
	}

	boolean isRemove() {
		return remove;
	}

	void setRemove() {
		remove = true;
	}


    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        EdgeNode other = (EdgeNode) obj;
        return edge.equals(other.edge);
    }

    @Override
    public int compareTo(EdgeNode other) {
		double thisDistance = this.edge.distance();
		double otherDistance = other.edge.distance();
		if (thisDistance < otherDistance) {
			return -1;
		} else if (thisDistance > otherDistance) {
			return 1;
		} else {
			return 0;
		}
    }
}
