package org.jboss.perf.hibernate.model;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
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

    @OneToOne
    Node parent;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    Node left;

    int leftSize;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    Node right;

    int rightSize;

    public Node() {
    }

    public Node(Node parent) {
        this.parent = parent;
    }

    public long getId() {
        return id;
    }

    public Node getParent() {
        return parent;
    }

    public void setParent(Node parent) {
        this.parent = parent;
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
        if (parent != null ? !parent.equals(node.parent) : node.parent != null) return false;
        if (left != null ? !left.equals(node.left) : node.left != null) return false;
        return !(right != null ? !right.equals(node.right) : node.right != null);

    }

    @Override
    public int hashCode() {
        int result = (int) (id ^ (id >>> 32));
        result = 31 * result + (parent != null ? parent.hashCode() : 0);
        result = 31 * result + (left != null ? left.hashCode() : 0);
        result = 31 * result + leftSize;
        result = 31 * result + (right != null ? right.hashCode() : 0);
        result = 31 * result + rightSize;
        return result;
    }
}
