package org.csstudio.archive.reader.channelarchiver.file;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

public class ArchiveFileBuffer implements AutoCloseable
{
	private final ByteBuffer buffer;
	private FileChannel fileChannel;
	private File file;
	
	public ArchiveFileBuffer(File file) throws IOException
	{
		this.buffer = ByteBuffer.allocate(65536); //TODO: what size? Bigger means less fetching, but too big means memory runs out
		setFile(file);
	}
	
	public void setFile(File file) throws IOException
	{
		this.file = file;
		fileChannel = FileChannel.open(file.toPath(), StandardOpenOption.READ);
		buffer.position(0).limit(0);
	}
	
	public File getFile()
	{
		return file;
	}
	
	public void prepareGet(int numBytes) throws IOException
	{
		if (buffer.remaining() < numBytes)
		{
			buffer.compact();
			fileChannel.read(buffer);
			buffer.limit(buffer.position()); //use limit to mark extent of read
			buffer.position(0);
		}
	}
	
	public void get(byte dst []) throws IOException
	{
		prepareGet(dst.length);
		buffer.get(dst);
	}
	
	public long getUnsignedInt() throws IOException
	{
		prepareGet(4);
		return Integer.toUnsignedLong(buffer.getInt());
	}
	
	public short getShort() throws IOException
	{
		prepareGet(2);
		return buffer.getShort();
	}

	public float getFloat() throws IOException
	{
		prepareGet(4);
		return buffer.getFloat();
	}

	public double getDouble() throws IOException
	{
		prepareGet(8);
		return buffer.getDouble();
	}
	
	public byte get() throws IOException
	{
		if (!buffer.hasRemaining())
		{
			buffer.clear();
			buffer.limit(fileChannel.read(buffer));
		}
		return buffer.get();
	}
	
	//get epicsTime saved in file as java Instant; automatically
	//converts from Channel Archiver epoch (1990) to java epoch (1970)
	public Instant getEpicsTime() throws IOException
	{
		return Instant.ofEpochSecond(getUnsignedInt() + 631152000L, getInt());
	}
	
	public void skip(int numBytes) throws IOException
	{
		int numAlready = buffer.remaining();
		while (numBytes > numAlready)
		{
			numBytes -= numAlready;
			buffer.clear();
			numAlready = fileChannel.read(buffer);
			buffer.limit(numAlready);
			buffer.position(0);
		}
		buffer.position(buffer.position() + numBytes);
	}
	
	public void offset(long offset) throws IOException
	{
 		if (offset < 0 || offset > fileChannel.size())
		{
			//throw new RuntimeException("Offset is invalid.") ?
			return;
		}
		//check if buffer contains the data
		//(Buffer always represents a contiguous portion of the file's contents)
		long buffer_start_offset = fileChannel.position() - buffer.limit();
		boolean doesNotContain = buffer_start_offset > offset || fileChannel.position() < offset;
		if (doesNotContain)
		{
			fileChannel.position(offset);
			buffer.clear();
			buffer.limit(fileChannel.read(buffer));
			buffer.position(0);
		}
		else
		{
			buffer.position((int)(offset - buffer_start_offset));
		}
	}
	
	public int getInt() throws IOException
	{
		prepareGet(4);
		return buffer.getInt();
	}

	long offset() throws IOException
	{
		return fileChannel.position() - buffer.limit() + buffer.position();
	}

	public int remaining()
	{
		return buffer.remaining();
	}
	
	@Override
	public void close() throws IOException
	{
		fileChannel.close();
	}
	
	public String toString()
	{
		long offset = -1;
		try
		{
			offset = offset();
		}
		catch (IOException e)
		{
			
		}
		return String.format("buffer@offset=%x(%d): %02x %02x %02x %02x %02x %02x %02x %02x", offset, offset, buffer.get(buffer.position()),
				buffer.get(buffer.position()+1), buffer.get(buffer.position()+2), buffer.get(buffer.position()+3), buffer.get(buffer.position()+4),
				buffer.get(buffer.position()+5), buffer.get(buffer.position()+6), buffer.get(buffer.position()+7));
	}
}