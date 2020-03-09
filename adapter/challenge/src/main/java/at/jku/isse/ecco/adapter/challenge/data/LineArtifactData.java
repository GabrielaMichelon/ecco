package at.jku.isse.ecco.adapter.challenge.data;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

public class LineArtifactData implements ArtifactData {

	private String line;

	public LineArtifactData(String line) {
		this.line = line;
	}

	public String getLine() {
		return this.line;
	}

	@Override
	public String toString() {
		return this.line;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.line);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		//if (getClass() != obj.getClass())
		//	return false;
		//LineArtifactData other = (LineArtifactData) obj;
		if (line == null) {
			//if (other.line != null)
			if (obj.toString() != null)
				return false;
		//} else if (!line.equals(other.line))
		} else if (!line.equals(obj.toString().replace("at.jku.isse.ecco.adapter.runtime.data","at.jku.isse.ecco.adapter.challenge.data")))
			return false;
		return true;
	}

}