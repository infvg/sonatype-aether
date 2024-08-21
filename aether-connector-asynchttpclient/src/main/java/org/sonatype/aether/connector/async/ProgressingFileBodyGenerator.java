package org.sonatype.aether.connector.async;

import io.netty.buffer.ByteBuf;
import io.netty.channel.DefaultFileRegion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.stream.ChunkedNioFile;
import org.asynchttpclient.AsyncHttpClientConfig;
import org.asynchttpclient.netty.channel.ChannelManager;
import org.asynchttpclient.netty.request.WriteProgressListener;
import org.asynchttpclient.request.body.RandomAccessBody;
import org.asynchttpclient.request.body.generator.BodyGenerator;
import org.sonatype.aether.transfer.TransferCancelledException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;


public class ProgressingFileBodyGenerator implements BodyGenerator {
    private final File file;
    private final long regionSeek;
    private final long regionLength;
    private final CompletionHandler completionHandler;

    public ProgressingFileBodyGenerator(File file, CompletionHandler handler) {
        this(file, 0L, file.length(), handler);
    }

    public ProgressingFileBodyGenerator(File file, long regionSeek, long regionLength, CompletionHandler handler) {
        this.file = assertNotNull(file, "file");
        this.regionLength = regionLength;
        this.regionSeek = regionSeek;
        this.completionHandler = handler;

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RandomAccessBody createBody() {
        try {
            return new ProgressingNettyFileBody(file, regionSeek, regionLength);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public class ProgressingNettyFileBody implements RandomAccessBody {

        private final RandomAccessFile file;

        private final FileChannel channel;

        private final long length;

        public ProgressingNettyFileBody( File file, long regionSeek, long regionLength) throws IOException {
            this.file = new RandomAccessFile(file, "r");
            channel = this.file.getChannel();
            length = regionLength;
            if (regionSeek > 0) {
                this.file.seek(regionSeek);
            }
        }

        @Override
        public long getContentLength()
        {
            return length;
        }

        @Override
        public BodyState transferTo(ByteBuf target) throws IOException {
            ByteBuffer event = target.nioBuffer(target.writerIndex(), target.writableBytes());
            int read = channel.read(event);
            if (read > 0) {
                target.writerIndex(target.writerIndex() + read);
                event.flip();

                if (completionHandler != null) {
                    try {
                        event.limit(read);
                        completionHandler.fireTransferProgressed(event);
                    } catch (TransferCancelledException e) {
                        throw new RuntimeException(e);
                    }
                }

                return BodyState.CONTINUE;
            } else if (read == -1) {
                return BodyState.STOP;
            }

            return BodyState.CONTINUE;
        }



        @Override
        public void close()
                throws IOException
        {
            file.close();
        }


        @Override
        public long transferTo(WritableByteChannel target) throws IOException {
            long position = file.getFilePointer();
            long transferred = channel.transferTo(position, length, new ProgressingWritableByteChannel(target));
            file.seek(position + transferred);
            return transferred;

    }
    final class ProgressingWritableByteChannel
            implements WritableByteChannel
    {

        final WritableByteChannel delegate;

        public ProgressingWritableByteChannel( WritableByteChannel delegate )
        {
            this.delegate = delegate;
        }

        public boolean isOpen()
        {
            return delegate.isOpen();
        }

        public void close()
                throws IOException
        {
            delegate.close();
        }

        @Override
        public int write( ByteBuffer src )
                throws IOException
        {
            ByteBuffer event = src.slice();
            int written = delegate.write( src );
            if ( written > 0 )
            {
                try
                {
                    event.limit( written );
                    completionHandler.fireTransferProgressed( event );
                }
                catch ( TransferCancelledException e )
                {
                    throw (IOException) new IOException( e.getMessage() ).initCause( e );
                }
            }
            return written;
        }

    }
}

}
