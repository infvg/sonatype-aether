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


class ProgressingFileBodyGenerator
        extends FileBodyGenerator
{

    private final CompletionHandler completionHandler;

    public ProgressingFileBodyGenerator( File file, CompletionHandler completionHandler )
    {
        super( file );
        this.completionHandler = completionHandler;
    }

    @Override
    public RandomAccessBody createBody()
    {
        return new ProgressingBody( super.createBody() );
    }

    final class ProgressingBody
            implements RandomAccessBody
    {

        final FileBody delegate;

        private ProgressingWritableByteChannel channel;

        public ProgressingBody( RandomAccessBody delegate )
        {
            this.delegate = (FileBody) delegate;
        }

        public long getContentLength()
        {
            return delegate.getContentLength();
        }

        @Override
        public BodyState transferTo(ByteBuf buffer )
                throws IOException
        {
            return delegate.read(buffer, completionHandler);
        }

        public long transferTo(WritableByteChannel target )
                throws IOException
        {
            ProgressingWritableByteChannel dst = channel;
            if ( dst == null || dst.delegate != target )
            {
                channel = dst = new ProgressingWritableByteChannel( target );
            }
            return delegate.transferTo(dst);
        }

        public void close()
                throws IOException
        {
            delegate.close();
        }

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
