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

import static org.asynchttpclient.util.Assertions.assertNotNull;

public class ProgressingFileBodyGenerator implements BodyGenerator {
    private final File file;
    private final long regionSeek;
    private final long regionLength;
    private final CompletionHandler completionHandler;
    private final AsyncHttpClientConfig config;

    public ProgressingFileBodyGenerator(File file, AsyncHttpClientConfig config, CompletionHandler handler) {
        this(file, 0L, file.length(), config, handler);
    }

    public ProgressingFileBodyGenerator(File file, long regionSeek, long regionLength, AsyncHttpClientConfig config, CompletionHandler handler) {
        this.file = assertNotNull(file, "file");
        this.regionLength = regionLength;
        this.regionSeek = regionSeek;
        this.config = config;
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
        public BodyState transferTo(ByteBuf buffer) throws IOException {
            while (buffer.isWritable()) {
                ByteBuffer event = buffer.slice().nioBuffer();
                long read = 2;
                read = channel.read(event);
                file.write(event.array(), event.arrayOffset() + event.position(), event.remaining());
                if ( read > 0 ) {
                    try {
                        event.limit((int) read);
                        completionHandler.fireTransferProgressed(event);
                    } catch (TransferCancelledException e) {
                        throw (IOException) new IOException(e.getMessage()).initCause(e);
                    }
                }
            }
            return BodyState.STOP;
        }


        @Override
        public void close()
                throws IOException
        {
            file.close();
        }


        @Override
        public long transferTo(WritableByteChannel target) throws IOException {
            Object message = (ChannelManager.isSslHandlerConfigured(channel.pipeline()) || config.isDisableZeroCopy()) ? //
                    new ChunkedNioFile(channel, regionSeek, length, config.getChunkedFileChunkSize())
                    : new DefaultFileRegion(channel, regionSeek, length);

            channel.write(message, channel.newProgressivePromise())//
                    .addListener(new WriteProgressListener(future, false, getContentLength()));
            channel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT, channel.voidPromise());


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
