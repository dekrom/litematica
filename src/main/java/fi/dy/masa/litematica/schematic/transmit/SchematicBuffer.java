package fi.dy.masa.litematica.schematic.transmit;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

import fi.dy.masa.litematica.Litematica;
import fi.dy.masa.litematica.util.FileType;

public class SchematicBuffer implements AutoCloseable
{
    public static final int BUFFER_SIZE = 16384;
    private final String name;
    private final FileType type;
    private final HashMap<Integer, Slice> buffer;

    public SchematicBuffer(String name)
    {
        this(name, FileType.LITEMATICA_SCHEMATIC);
    }

    public SchematicBuffer(String name, FileType type)
    {
        this.name = name;
        this.type = type;
        this.buffer = new HashMap<>();
    }

    public String getName()
    {
        return this.name;
    }

    public FileType getType()
    {
        return this.type;
    }

    public Path getFileName()
    {
        String ext = FileType.getFileExt(this.type);

        // The name is received from an untrusted server over the Servux channel. Strip any
        // directory components or path traversal so a hostile server cannot write outside the
        // target directory (e.g. drop a jar into mods/); only a bare file name is ever used.
        String safe = this.name.replace('\\', '/');
        int slash = safe.lastIndexOf('/');

        if (slash >= 0)
        {
            safe = safe.substring(slash + 1);
        }

        if (safe.isEmpty() || safe.equals(".") || safe.equals(".."))
        {
            safe = "default_file";
        }

        if (safe.contains(ext))
        {
            return Path.of(safe);
        }
        else
        {
            return Path.of(safe + ext);
        }
    }

    public void receiveSlice(final int number, Slice slice)
    {
        this.buffer.put(number, slice);
    }

    public Path writeFile(Path dir)
    {
        if (!Files.isDirectory(dir))
        {
            try
            {
                Files.createDirectory(dir);
            }
            catch (IOException err)
            {
                Litematica.LOGGER.error("LitematicBuffer#writeFile(): Exception creating directory '{}'; {}", dir.toAbsolutePath().toString(), err.getLocalizedMessage());
                return null;
            }
        }

        Path file = dir.resolve(this.getFileName()).normalize();

        if (!file.startsWith(dir.normalize()))
        {
            Litematica.LOGGER.error("LitematicBuffer#writeFile(): Refusing to write file outside the schematic directory: '{}'", file.toAbsolutePath().toString());
            return null;
        }

        if (Files.exists(file))
        {
            try
            {
                Files.delete(file);
            }
            catch (IOException err)
            {
                Litematica.LOGGER.error("LitematicBuffer#writeFile(): Exception deleting file '{}'; {}", file.toAbsolutePath().toString(), err.getLocalizedMessage());
                return null;
            }
        }

        try (OutputStream os = Files.newOutputStream(file))
        {
            // Write in correct Slice order
            for (int i = 0; i < this.buffer.size(); i++)
            {
                Slice entry = this.buffer.get(i);

                if (entry != null)
                {
                    os.write(entry.data(), 0, entry.size());
                }
            }
        }
        catch (Exception err)
        {
            Litematica.LOGGER.error("LitematicBuffer#writeFile(): Exception saving file '{}'; {}", file.toAbsolutePath().toString(), err.getLocalizedMessage());
            return null;
        }

        Litematica.debugLog("LitematicBuffer#writeFile(): Saved file '{}' successfully", file.toAbsolutePath().toString());
        this.buffer.clear();
        return file;
    }

    @Override
    public void close() throws Exception
    {
        this.buffer.clear();
    }

    public record Slice(byte[] data, int size) {}
}
