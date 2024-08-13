package org.sonatype.aether.connector.async;

/*******************************************************************************
 * Copyright (c) 2010-2011 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.DefaultFileRegion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.stream.ChunkedNioFile;
import org.asynchttpclient.AsyncHttpClientConfig;
import org.asynchttpclient.netty.NettyResponseFuture;
import org.asynchttpclient.netty.channel.ChannelManager;
import org.asynchttpclient.netty.request.WriteProgressListener;
import org.asynchttpclient.netty.request.body.NettyFileBody;
import org.asynchttpclient.request.body.RandomAccessBody;
import org.sonatype.aether.transfer.TransferCancelledException;


class ProgressingFileBody extends NettyFileBody
{

    private CompletionHandler completionHandler;


    public ProgressingFileBody(File file, AsyncHttpClientConfig config, CompletionHandler handler) {
        super(file, config);
        this.completionHandler = handler;

    }



    final class ProgressingBody
        implements RandomAccessBody
    {

        final RandomAccessBody delegate;

        private ProgressingWritableByteChannel channel;

        public ProgressingBody( RandomAccessBody delegate )
        {
            this.delegate = delegate;
        }

        @Override
        public long getContentLength()
        {
            return delegate.getContentLength();
        }

        @Override
        public BodyState transferTo(ByteBuf byteBuf) throws IOException {
            return null;
        }
        public long read( ByteBuffer buffer )
            throws IOException
        {
            ByteBuffer event = buffer.slice();
            long read = 2; // delegate.read( buffer );
            if ( read > 0 )
            {
                try
                {
                    event.limit( (int) read );
                    completionHandler.fireTransferProgressed( event );
                }
                catch ( TransferCancelledException e )
                {
                    throw (IOException) new IOException( e.getMessage() ).initCause( e );
                }
            }
            return read;
        }



        @Override
        public void close()
            throws IOException
        {
            delegate.close();
        }


        @Override
        public long transferTo(WritableByteChannel writableByteChannel) throws IOException {
            ProgressingWritableByteChannel dst = channel;
            if (true)//( dst == null || dst.delegate != target )
            {
                //channel = dst = new ProgressingWritableByteChannel( target );
            }
            return delegate.transferTo( dst);//position, dst );
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
