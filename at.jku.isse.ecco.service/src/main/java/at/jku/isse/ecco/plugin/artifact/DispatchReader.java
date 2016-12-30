package at.jku.isse.ecco.plugin.artifact;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.listener.ReadListener;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.Inject;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

public class DispatchReader implements ArtifactReader<Path, Set<Node.Op>> {

	private final EntityFactory entityFactory;

	@Override
	public String getPluginId() {
		return ArtifactPlugin.class.getName();
	}

	private static final String[] typeHierarchy = new String[]{};

	@Override
	public String[] getTypeHierarchy() {
		return typeHierarchy;
	}

	/**
	 * The collection of readers to which should be dispatched.
	 */
	private Collection<ArtifactReader<Path, Set<Node.Op>>> readers;

	/**
	 * @param readers The collection of readers to which should be dispatched.
	 */
	@Inject
	public DispatchReader(EntityFactory entityFactory, Set<ArtifactReader<Path, Set<Node.Op>>> readers) {
		checkNotNull(entityFactory);

		this.entityFactory = entityFactory;
		this.readers = readers;
	}

	private Collection<ReadListener> listeners = new ArrayList<ReadListener>();

	@Override
	public void addListener(ReadListener listener) {
		this.listeners.add(listener);
	}

	@Override
	public void removeListener(ReadListener listener) {
		this.listeners.remove(listener);
	}

	private void fireReadEvent(Path path, ArtifactReader reader) {
		for (ReadListener listener : this.listeners) {
			listener.fileReadEvent(path, reader);
		}
	}

	@Override
	public boolean canRead(Path file) {
		for (ArtifactReader<Path, Set<Node.Op>> reader : this.readers) {
			if (reader.canRead(file))
				return true;
		}
		return false;
	}

	/**
	 * @param file The file to be read.
	 * @return The reader best suited for reading the file.
	 */
	private ArtifactReader<Path, Set<Node.Op>> getReaderForFile(Path base, Path file) {
		ArtifactReader<Path, Set<Node.Op>> currentReader = null;
		for (ArtifactReader<Path, Set<Node.Op>> reader : this.readers) {
			if (reader.canRead(base.resolve(file)) && (currentReader == null || currentReader.getTypeHierarchy().length < reader.getTypeHierarchy().length))
				currentReader = reader;
		}
		return currentReader;
	}

	@Override
	public Set<Node.Op> read(Path[] input) {
		return this.read(Paths.get("."), input);
	}

	@Override
	public Set<Node.Op> read(Path base, Path[] input) {
		if (!Files.exists(base)) {
			throw new EccoException("Base directory does not exist.");
		} else if (!Files.isDirectory(base)) {
			throw new EccoException("Current base directory is not a directory but a file.");
		}


		Set<Node.Op> nodes = new HashSet<>();

		base = base.normalize();

		for (Path path : input) {

			Map<ArtifactReader<Path, Set<Node.Op>>, ArrayList<Path>> readerToFilesMap = new HashMap<>();

			// this reader itself is responsible for the directory tree structure (unless there is an adapter that deals with a directory)
			Map<Path, Node.Op> directoryNodes = new HashMap<>();
			Node.Op baseDirectoryNode = this.readDirectories(base, base.resolve(path), readerToFilesMap, directoryNodes);
			nodes.add(baseDirectoryNode);

			// let readers read the assigned files
			for (ArtifactReader<Path, Set<Node.Op>> reader : this.readers) {
				ArrayList<Path> filesList = readerToFilesMap.get(reader);

				if (filesList != null) {
					Path[] pluginInput = filesList.toArray(new Path[filesList.size()]);

					Set<Node.Op> pluginNodes = reader.read(base, pluginInput);
					for (Node.Op pluginNode : pluginNodes) {
						if (!(pluginNode.getArtifact().getData() instanceof PluginArtifactData))
							throw new EccoException("Plugin must return valid plugin nodes as root nodes in order for it to be compatible with dispatchers.");
						PluginArtifactData pluginArtifactData = (PluginArtifactData) pluginNode.getArtifact().getData();
						Path parent = pluginArtifactData.getPath().getParent();
						if (parent == null)
							parent = Paths.get(".").normalize();
						Node.Op parentNode = directoryNodes.get(parent);
						if (parentNode != null)
							parentNode.addChild(pluginNode);
						else
							throw new EccoException("Plugin '" + pluginArtifactData.getPluginId() + "' returned an invalid plugin node: " + pluginNode);
					}
				}
			}

		}

		// return produced nodes
		return nodes;
	}


//	private Set<Path> ignoredFiles = new HashSet<Path>();
//
//	public void setIgnoredFiles(Set<Path> ignoredFiles) {
//		this.ignoredFiles = ignoredFiles;
//	}
//
//	public Set<Path> getIgnoredFiles() {
//		return this.ignoredFiles;
//	}

	private Set<String> ignorePatterns = new HashSet<>();

	public Set<String> getIgnorePatterns() {
		return this.ignorePatterns;
	}

	private boolean isIgnored(Path path) {
//		return this.ignoredFiles.contains(path);
		for (String ignorePattern : this.ignorePatterns) {
			PathMatcher pm = FileSystems.getDefault().getPathMatcher(ignorePattern);
			if (pm.matches(path))
				return true;
		}
		return false;
	}


	private Node.Op readDirectories(Path base, Path current, Map<ArtifactReader<Path, Set<Node.Op>>, ArrayList<Path>> readerToFilesMap, Map<Path, Node.Op> directoryNodes) {
		Path relativeCurrent = base.relativize(current);

		try {
			if (Files.isDirectory(current) && this.getReaderForFile(base, relativeCurrent) == null) { // deal with directories that cannot be dispatched
				if (!this.isIgnored(relativeCurrent)) { // if directory is not ignored add it to directories
					Artifact.Op<?> directoryArtifact = entityFactory.createArtifact(new DirectoryArtifactData(relativeCurrent));
					Node.Op directoryNode = entityFactory.createNode(directoryArtifact);
					directoryNodes.put(relativeCurrent, directoryNode);

					this.fireReadEvent(base.relativize(current), this);

					// go into sub directories
					Files.list(current).forEach(d -> {
						Node.Op child = this.readDirectories(base, d, readerToFilesMap, directoryNodes);
						if (child != null)
							directoryNode.addChild(child);
					});

					return directoryNode;
				}
			} else { // deal with files and directories that can be dispatched
				if (!this.isIgnored(relativeCurrent)) { // if file is not ignored add it to readerToFilesMap
					// assign file to reader
					ArtifactReader<Path, Set<Node.Op>> reader = this.getReaderForFile(base, relativeCurrent);

					if (reader != null) {
						ArrayList<Path> fileList = readerToFilesMap.get(reader);
						if (fileList == null)
							fileList = new ArrayList<Path>();
						fileList.add(relativeCurrent);
						readerToFilesMap.put(reader, fileList);
						this.fireReadEvent(relativeCurrent, reader);

//						// add artifact plugin node
//						Artifact<PluginArtifactData> pluginArtifact = this.entityFactory.createArtifact(new PluginArtifactData(reader.getPluginId(), relativeCurrent));
//						Node pluginNode = this.entityFactory.createOrderedNode(pluginArtifact);
//						directoryNodes.put(relativeCurrent, pluginNode);
//
//						return pluginNode;
					}

					return null;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return null;
	}

}