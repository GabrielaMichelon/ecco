package at.jku.isse.ecco.storage.json.impl.entities;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

public class JsonNode implements Node.Op, Serializable {
    private boolean unique = true;

    private final List<Op> children = new ArrayList<>();

    private Artifact.Op<?> artifact = null;

    private Op parent = null;


    public JsonNode() {
    }

    public JsonNode(Artifact.Op<?> artifact) {
        this.artifact = artifact;
    }


    @Override
    public Op createNode() {
        return new JsonNode();
    }


    @Override
    public boolean isAtomic() {
        if (this.artifact != null)
            return this.artifact.isAtomic();
        else
            return false;
    }


    @Override
    public Association getContainingAssociation() {
        if (this.parent == null)
            return null;
        else
            return this.parent.getContainingAssociation();
    }


    @Override
    public Artifact.Op<?> getArtifact() {
        return artifact;
    }

    @Override
    public void setArtifact(Artifact.Op<?> artifact) {
        this.artifact = artifact;
    }

    @Override
    public Op getParent() {
        return parent;
    }

    @Override
    public void setParent(Op parent) {
        this.parent = parent;
    }

    @Override
    public boolean isUnique() {
        return this.unique;
    }

    @Override
    public void setUnique(boolean unique) {
        this.unique = unique;
    }


    @Override
    public void addChild(Op child) {
        checkNotNull(child);

        if (this.getArtifact() != null && !this.getArtifact().isOrdered() && this.children.contains(child))
            throw new EccoException("An equivalent child is already contained. If multiple equivalent children are allowed use an ordered node.");

        this.children.add(child);
        child.setParent(this);
    }

    @Override
    public void addChildren(Op... children) {
        for (Op child : children)
            this.addChild(child);
    }

    @Override
    public void removeChild(Op child) {
        checkNotNull(child);

        this.children.remove(child);
        child.setParent(null);
    }


    @Override
    public List<Op> getChildren() {
        return this.children;
    }


    @Override
    public int hashCode() {
        return this.getArtifact() != null ? this.getArtifact().hashCode() : 0;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null) return false;
        if (!(other instanceof Node)) return false;

        Node otherNode = (Node) other;

        if (this.getArtifact() == null)
            return otherNode.getArtifact() == null;

        return this.getArtifact().equals(otherNode.getArtifact());
    }


    @Override
    public String toString() {
        return this.getNodeString();
    }


    // properties

    private transient Map<String, Object> properties = new HashMap<>();

    @Override
    public Map<String, Object> getProperties() {
        return this.properties;
    }

    public static class JsonRootNode extends JsonNode implements RootNode.Op {
        private Association containingAssociation;


        public JsonRootNode() {
            super();
        }


        @Override
        public boolean isUnique() {
            return true;
        }

        @Override
        public boolean isAtomic() {
            return false;
        }


        @Override
        public RootNode.Op createNode() {
            return new JsonRootNode();
        }


        @Override
        public void setContainingAssociation(Association.Op containingAssociation) {
            this.containingAssociation = containingAssociation;
        }

        @Override
        public Association getContainingAssociation() {
            return this.containingAssociation;
        }

        @Override
        public String toString() {
            return "root";
        }
    }
}
