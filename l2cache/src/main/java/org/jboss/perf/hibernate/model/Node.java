package org.jboss.perf.hibernate.model;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToOne;

/**
 * Test entity for forming a tree
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Entity
public class Node {
    @Id
    @GeneratedValue
    long id;

    boolean root;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    Node left;

    int leftSize;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    Node right;

    int rightSize;

    public Node() {
    }

    public Node(boolean root) {
        this.root = root;
    }

    public long getId() {
        return id;
    }

    public boolean isRoot() {
        return root;
    }

    public Node getLeft() {
        return left;
    }

    public void setLeft(Node left) {
        this.left = left;
    }

    public Node getRight() {
        return right;
    }

    public void setRight(Node right) {
        this.right = right;
    }

    public int getLeftSize() {
        return leftSize;
    }

    public void setLeftSize(int leftSize) {
        this.leftSize = leftSize;
    }

    public int getRightSize() {
        return rightSize;
    }

    public void setRightSize(int rightSize) {
        this.rightSize = rightSize;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Node node = (Node) o;

        if (id != node.id) return false;
        if (leftSize != node.leftSize) return false;
        if (rightSize != node.rightSize) return false;
        if (root != node.root) return false;
        if (left != null ? !left.equals(node.left) : node.left != null) return false;
        return !(right != null ? !right.equals(node.right) : node.right != null);

    }

    @Override
    public int hashCode() {
        int result = (int) (id ^ (id >>> 32));
        result = 31 * result + (root ? 1 : 0);
        result = 31 * result + (left != null ? left.hashCode() : 0);
        result = 31 * result + leftSize;
        result = 31 * result + (right != null ? right.hashCode() : 0);
        result = 31 * result + rightSize;
        return result;
    }
}
