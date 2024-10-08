package org.sonatype.aether.connector.async;

import io.netty.buffer.ByteBuf;
import org.asynchttpclient.request.body.RandomAccessBody;
import org.asynchttpclient.request.body.generator.BodyGenerator;
import org.sonatype.aether.transfer.TransferCancelledException;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;

public class FileBodyGenerator implements BodyGenerator {
    private final File file;
    private final long regionSeek;
    private final long regionLength;

    public FileBodyGenerator(File file) {
        if (file == null) {
            throw new IllegalArgumentException("no file specified");
        } else {
            this.file = file;
            this.regionLength = file.length();
            this.regionSeek = 0L;
        }
    }

    public FileBodyGenerator(File file, long regionSeek, long regionLength) {
        if (file == null) {
            throw new IllegalArgumentException("no file specified");
        } else {
            this.file = file;
            this.regionLength = regionLength;
            this.regionSeek = regionSeek;
        }
    }

    public RandomAccessBody createBody() {
        try {
            return new FileBody(this.file, this.regionSeek, this.regionLength);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected static class FileBody implements RandomAccessBody {
        private final RandomAccessFile file;
        private final FileChannel channel;
        private final long length;

        public FileBody(File file) throws IOException {
            this.file = new RandomAccessFile(file, "r");
            this.channel = this.file.getChannel();
            this.length = file.length();
        }

        public FileBody(File file, long regionSeek, long regionLength) throws IOException {
            this.file = new RandomAccessFile(file, "r");
            this.channel = this.file.getChannel();
            this.length = regionLength;
            if (regionSeek > 0L) {
                this.file.seek(regionSeek);
            }

        }

        public long getContentLength() {
            return this.length;
        }

        @Override
        public BodyState transferTo(ByteBuf buffer) throws IOException {
            int writableBytes = buffer.writableBytes();
            ByteBuffer byteBuffer = ByteBuffer.allocate(writableBytes);
            int bytesRead = channel.read(byteBuffer);
            if (bytesRead == -1) {
                return BodyState.STOP;
            }
            byteBuffer.flip();
            buffer.writeBytes(byteBuffer);
            return bytesRead < writableBytes ? BodyState.STOP : BodyState.CONTINUE;
        }

        public BodyState read(ByteBuf buffer, CompletionHandler handler) throws IOException {
            int writableBytes = buffer.writableBytes();
            ByteBuffer byteBuffer = ByteBuffer.allocate(writableBytes);
            int bytesRead = channel.read(byteBuffer);
            if (bytesRead == -1) {
                return BodyState.STOP;
            }
            if ( bytesRead > 0 )
            {
                ByteBuffer event = byteBuffer.slice();
                try
                {
                    event.limit( (int) bytesRead );
                    handler.fireTransferProgressed( event );
                }
                catch ( TransferCancelledException e )
                {
                    throw (IOException) new IOException( e.getMessage() ).initCause( e );
                }
            }
            byteBuffer.flip();
            buffer.writeBytes(byteBuffer);
            return bytesRead < writableBytes ? BodyState.STOP : BodyState.CONTINUE;
        }

        public void close() throws IOException {
            this.file.close();
        }

        @Override
        public long transferTo(WritableByteChannel writableByteChannel) throws IOException {
            long position = channel.position();
            long transferred = channel.transferTo(position, length - position, writableByteChannel);
            channel.position(position + transferred);
            return transferred;
        }
    }
}
